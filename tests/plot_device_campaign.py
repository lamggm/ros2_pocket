"""Physical campaign implementation. Imported lazily, never at CLI --help.

All Android actions use the increment-10 guarded client. Fixture controls require
exact immutable ID, source hash, run label and explicit command-line ownership.
No startup/installation/network change is implemented here.
"""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time
import uuid


def validate_fixture(state, cid, namespace, domain):
    config = state.get('Config', {})
    cmd = config.get('Cmd') or []
    if (state.get('Id') != cid or state.get('State', {}).get('Running') is not True
            or config.get('Labels', {}).get('ros-pocket.plot-run') != namespace):
        raise ValueError('Fixture identity/ownership not established')
    for flag, value in [('--namespace', namespace), ('--domain', str(domain)),
                        ('--control', '/tmp/plot-phase'), ('--stamped-writer', '/fixture/plot-stamped-writer')]:
        if cmd.count(flag) != 1 or cmd.index(flag) + 1 >= len(cmd) or cmd[cmd.index(flag) + 1] != value:
            raise ValueError('Fixture command differs: ' + flag)
    if '/fixture/device_plot_fixture.py' not in cmd or '--stamped-writer' not in cmd:
        raise ValueError('Own fixture with real source-stamp writer required')
    return state


class Fixture:
    def __init__(self, cid, namespace, domain, out):
        self.cid, self.namespace, self.domain, self.out = cid, namespace, domain, out
        self.verified = False

    def docker(self, *args, timeout=10):
        result = subprocess.run(['docker', *args], text=True, capture_output=True, timeout=timeout)
        if result.returncode:
            raise RuntimeError('Fixture Docker command failed: ' + result.stderr[:400])
        return result.stdout

    def verify(self):
        state = json.loads(self.docker('inspect', self.cid))[0]
        validate_fixture(state, self.cid, self.namespace, self.domain)
        expected = hashlib.sha256(Path(__file__).with_name('device_plot_fixture.py').read_bytes()).hexdigest()
        source = self.docker('exec', self.cid, 'sha256sum', '/fixture/device_plot_fixture.py').split()
        if not source or source[0] != expected:
            raise ValueError('Fixture source hash differs from this harness')
        writer = Path(__file__).resolve().parents[1] / 'build/plot-stamped-writer'
        writer_hash = hashlib.sha256(writer.read_bytes()).hexdigest()
        actual_writer = self.docker('exec', self.cid, 'sha256sum', '/fixture/plot-stamped-writer').split()
        if not actual_writer or actual_writer[0] != writer_hash:
            raise ValueError('Source timestamp writer bytes differ from the verified local executable')
        (self.out / 'fixture-identity.json').write_text(json.dumps(state, indent=2))
        self.verified = True

    def phase(self, phase):
        from device_plot_fixture import PHASES
        if phase not in (*PHASES, 'stop'):
            raise ValueError('Unknown phase')
        self.verify()
        self.docker('exec', self.cid, 'python3', '-c',
                    'import os,sys; p="/tmp/plot-phase."+sys.argv[2]; '
                    'open(p,"x").write(sys.argv[1]); os.replace(p,"/tmp/plot-phase")', phase, uuid.uuid4().hex)

    def events(self, phase):
        self.verify()
        events = []
        # Only the contiguous suffix of the CURRENT phase can cover this window.
        # A prior return separated by gap must not be stitched into this return.
        rows = [json.loads(line) for line in self.docker('logs', '--tail', '64', self.cid).splitlines()
                if line.startswith('{')]
        for row in reversed(rows):
            if row.get('event') != 'publication' or row.get('namespace') != self.namespace or row.get('phase') != phase:
                break
            events.append(row)
        events.reverse()
        if len(events) < 12 or events[-1]['monotonicNs'] - events[0]['monotonicNs'] < 6_000_000_000:
            raise ValueError('Phase not sustained for >=6 seconds')
        for a, b in zip(events, events[1:]):
            if (not 0 < b['monotonicNs'] - a['monotonicNs'] <= 1_000_000_000
                    or b['sequence'] != a['sequence'] + 1):
                raise ValueError('Producer coverage has gaps')
        path = self.out / ('producer-' + phase + '-' + uuid.uuid4().hex + '.json')
        path.write_text(json.dumps(events, indent=2))
        return events


def paused_survivors(before, after, removed_slot):
    # qaSeries renumbers seriesIndex after removal. All immutable data/identity
    # fields, including receive endpoints, must otherwise be byte-for-byte equal.
    def without_index(series):
        return [{k: v for k, v in s.items() if k != 'seriesIndex'} for s in series]
    expected = [s for s in before if s['slot'] != removed_slot]
    return bool(expected) and without_index(after) == without_index(expected)


def require_phase_graph(state, slots, *, gap_slots=(), finite_min=1):
    for key in ('intake', 'display'):
        series = state[key]
        if len(series) != len(slots) or {s['slot'] for s in series} != set(slots):
            raise ValueError('Missing, duplicate or extra selected series for phase')
        if any(s['gapPoints'] < 1 for s in series if s['slot'] in gap_slots):
            raise ValueError('Nonfinite not retained as gaps')
        if any(s['finitePoints'] < finite_min for s in series):
            raise ValueError('Missing finite phase points')


def campaign(client, args):
    from tablet_plot_acceptance import Navigator, observation, paused_progress, identity
    import tablet_ui as ui
    report = {'kind': client.kind, 'scope': 'plot-functional-not-soak', 'passed': False,
              'physicalAccepted': False, 'checks': [], 'errors': []}
    fixture = Fixture(args.fixture_container, args.namespace, args.domain, client.out)
    cleanup = False
    previous = None
    nav = Navigator(client, args.namespace)
    deadline = time.monotonic() + 540
    def check(condition, message):
        if not condition:
            raise ValueError(message)
    def live(slots=(0, 1, 2)):
        nonlocal previous
        until = min(deadline, time.monotonic() + 15)
        while time.monotonic() < until:
            q = client.qa()
            # Narrow readiness conditions only; malformed metadata/identities fail.
            if q.get('valid') is False or q.get('plotDisplay', {}).get('drawPending') is True:
                time.sleep(.7); continue
            if previous and q.get('capturedMonotonicNs', 0) <= previous['exportedMonotonicNs']:
                time.sleep(.7); continue
            state = observation(q, previous, slots=slots)
            previous = q
            return state
        raise ValueError('No fresh completed-draw worker capture before deadline')
    def align():
        nav.chart()
        for _ in range(10):
            q = client.qa()
            if q.get('plotDisplay', {}).get('chartFullyVisible') is True:
                return
            client.scroll('down')
        raise ValueError('Chart cannot be fully observed')
    def screenshot(label):
        align()
        nodes = client.dump('chart-crop')
        bounds = ui.node_by(nodes, 'plot_chart')['bounds']
        m = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
        check(m is not None, 'Chart bounds unavailable')
        x, y, right, bottom = map(int, m.groups())
        client.focus()
        data = subprocess.run([ui.ADB, '-s', client.serial, 'exec-out', 'screencap', '-p'],
                              capture_output=True, timeout=5, check=True).stdout
        client.focus()
        check(data.startswith(b'\x89PNG\r\n\x1a\n') and len(data) < 20_000_000, 'Invalid screenshot')
        raw = client.out / (label + '-' + uuid.uuid4().hex + '.png'); raw.write_bytes(data)
        crop = raw.with_name(raw.stem + '-chart.png')
        subprocess.run(['magick', str(raw), '-crop', f'{right-x}x{bottom-y}+{x}+{y}', '+repage', str(crop)],
                       check=True, capture_output=True, timeout=5)
        raw.unlink()  # Keep only the exact authorized chart, not unrelated UI content.
        return crop
    def pixels_equal(a, b):
        metric = subprocess.run(['magick', 'compare', '-metric', 'AE', str(a), str(b), 'null:'],
                                capture_output=True, text=True, timeout=5)
        check(metric.returncode in (0, 1), 'Pixel comparison unavailable')
        (client.out / ('pixel-' + uuid.uuid4().hex + '.json')).write_text(json.dumps({'a': a.name, 'b': b.name, 'AE': metric.stderr.strip()}))
        return metric.returncode == 0
    def wait(seconds):
        check(time.monotonic() + seconds < deadline, 'Campaign deadline exhausted')
        time.sleep(seconds)
    def disconnect():
        nodes = client.find('connect', direction='up')
        text = ui.node_by(nodes, 'connect').get('text', '').casefold()
        if text == 'desconectar':
            client.tap('connect')
        for _ in range(8):
            nodes = client.dump('disconnect-readback')
            if ui.node_by(nodes, 'connect').get('text', '').casefold() == 'conectar':
                return
            time.sleep(.3)
        raise ValueError('App disconnect not confirmed')
    try:
        check(client.kind == 'adb_observation', 'Synthetic transport is not physical acceptance')
        check(subprocess.run(['magick', '-version'], capture_output=True, timeout=5).returncode == 0, 'ImageMagick required')
        client.verify(); fixture.verify(); cleanup = True
        # Verify installed artifact bytes before changing UI. Never install/replace.
        paths = client.adb('shell', 'pm', 'path', ui.PKG).splitlines()
        check(len(paths) == 1 and re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+~\-]+/base\.apk', paths[0]) is not None,
              'Single exact installed DEV base APK required')
        actual = client.adb('shell', 'sha256sum', paths[0][8:]).split()[0]
        expected = hashlib.sha256(args.candidate_apk.read_bytes()).hexdigest()
        check(actual == expected, 'Installed candidate APK bytes differ')
        report['apkSha256'] = expected
        client.adb('shell', 'am', 'start', '--user', '0', '-n', ui.PKG + '/com.lamggm.rospocket.MainActivity')
        client.focus(); disconnect()
        client.field('domain', str(args.domain)); client.field('peer', args.peer); client.hide_ime()
        client.tap('connect')
        # Clear persisted local filters through actual Views before selecting own topics.
        client.find('diagnostics', direction='up')
        for ident in ('explorer_namespace', 'explorer_type'):
            client.find(ident, direction='down'); client.field(ident, ''); client.hide_ime()
        for slot, suffix in enumerate(('a', 'b', 'stamped')):
            nav.watch(slot, suffix)
        wait(2)
        for slot, suffix in enumerate(('a', 'b', 'stamped')):
            nav.series(slot, suffix)
        align(); wait(2); baseline = live()
        require_phase_graph(baseline, (0, 1, 2), finite_min=1)
        screenshot('finite'); report['checks'].append('finite-completed-draw')
        for phase in ('nan', 'positive_infinity', 'negative_infinity'):
            fixture.phase(phase); wait(7); fixture.events(phase)
            q = live()
            require_phase_graph(q, (0, 1, 2), gap_slots=(0, 2))
            screenshot(phase); report['checks'].append(phase)
        fixture.phase('return'); wait(7); fixture.events('return'); finite = live()
        require_phase_graph(finite, (0, 1, 2), finite_min=1)
        nav.tap('plot_pause', 'up'); align(); wait(1); before = live(); first = screenshot('paused-before')
        wait(7); after = live(); second = screenshot('paused-after')
        check(paused_progress(before, after) and pixels_equal(first, second), 'Pause changed chart or stopped intake')
        report['checks'].append('pause-pixels-and-intake')
        nav.series(0, 'a', False); align(); wait(1); removed = live()
        check(paused_survivors(after['display'], removed['display'], 0), 'Selective paused uncheck advanced surviving traces')
        nav.tap('plot_pause', 'up'); nav.series(0, 'a'); align(); wait(2)
        resumed_image = screenshot('resumed')
        check(not pixels_equal(second, resumed_image), 'Resume did not draw new pixels')
        report['checks'].append('resume-new-pixels')
        nav.tap('plot_pause', 'up'); align(); wait(1); before_button = live()
        nav.tap(('Remover Watch 1 · ' + args.namespace + '/a data').upper(), 'up')
        align(); wait(1); after_button = live()
        check(paused_survivors(before_button['display'], after_button['display'], 0), 'Paused remove button disturbed other traces')
        nav.tap('plot_pause', 'up'); nav.series(0, 'a'); align(); wait(2)
        report['checks'].append('both-selective-paused-removal-controls')
        fixture.phase('gap'); wait(7); fixture.events('gap'); gap = live(slots=(1,))
        require_phase_graph(gap, (1,), finite_min=1)
        fixture.phase('return'); wait(7); fixture.events('return'); returned = live()
        require_phase_graph(returned, (1,), finite_min=1)
        for slot, suffix in ((0, 'a'), (2, 'stamped')):
            nav.series(slot, suffix)
        align(); wait(2); before = live()
        fixture.phase('source_reset'); wait(7); fixture.events('source_reset'); after = live()
        a = next(s for s in before['intake'] if s['slot'] == 2)
        b = next(s for s in after['intake'] if s['slot'] == 2)
        check(identity(a) == identity(b) and b['newestReceiveNs'] > a['newestReceiveNs'] and b['breakBeforePoints'] > a['breakBeforePoints'], 'Source reset not separated from receive-monotonic axis')
        screenshot('source-reset'); fixture.phase('source_jump'); wait(7); fixture.events('source_jump'); live(); screenshot('source-jump')
        report['checks'].append('gap-return-retirement-and-source-clock')
        fixture.phase('window'); start = live(); wait(34); fixture.events('window'); end = live()
        old = {identity(s): s for s in start['intake']}
        check(all(s['oldestReceiveNs'] > old[identity(s)]['oldestReceiveNs'] for s in end['intake']), 'Real 30-second window did not evict')
        screenshot('window'); report['checks'].append('real-window-eviction-not-600-invented-points')
        # Reassign slot zero to a different actual topic without changing healthy slot1/2.
        nav.watch(0, 'b'); align(); wait(2); reassigned = live()
        check(all(s['slot'] != 0 for s in reassigned['intake']), 'Reassigned series resurrected')
        report['checks'].append('reader-reassignment-invalidation')
        report['passed'] = True
        report['physicalAccepted'] = False  # screenshots still require independent/operator review.
        report['pending'] = ['independent screenshot/readability review', 'integrated-candidate qualification and operator acceptance', 'official soak separate']
        return report
    except Exception as exc:
        report['errors'].append(type(exc).__name__ + ': ' + str(exc))
        raise
    finally:
        if cleanup:
            try:
                disconnect()
            except Exception as exc:
                report['errors'].append('cleanup app: ' + str(exc)); report['passed'] = False
            try:
                fixture.phase('stop')
                fixture.docker('wait', fixture.cid, timeout=8)
                stopped = json.loads(fixture.docker('inspect', fixture.cid))[0]
                if stopped['State']['Running'] is not False:
                    raise ValueError('Fixture stop not confirmed')
                (client.out / 'fixture-stopped.json').write_text(json.dumps(stopped, indent=2))
            except Exception as exc:
                report['errors'].append('cleanup fixture: ' + str(exc)); report['passed'] = False
        (client.out / 'plot-result.json').write_text(json.dumps(report, indent=2))
