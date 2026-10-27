#!/usr/bin/env python3
"""Real Jazzy/Cyclone Session phase driver, called INSIDE network-none container.

Standalone --help performs no network/device actions. Outputs real native JSONL
for replay through PlotController/PlotView native Canvas; no fabricated points.
"""
import argparse
import json
import os
from pathlib import Path
import selectors
import subprocess
import time
import uuid


def write_phase(control, phase):
    # The independent publisher must see the old or new complete token, never
    # the empty file exposed by write_text's truncate/write interval.
    staged = control.with_name(control.name + '.tmp')
    staged.write_text(phase)
    staged.replace(control)


def run(reader, writer, out):
    out.mkdir(parents=True, exist_ok=True)
    namespace = '/plot_' + uuid.uuid4().hex
    control = out / 'phase'
    write_phase(control, 'finite')
    records = out / 'snapshots.jsonl'
    publisher_log = (out / 'publisher.jsonl').open('w')
    publisher = subprocess.Popen(['python3', '-u', str(Path(__file__).with_name('device_plot_fixture.py')),
                                  '--domain', '71', '--namespace', namespace, '--control', str(control),
                                  '--duration', '180', '--stamped-writer', str(writer)], stdout=publisher_log)
    child = subprocess.Popen([str(reader)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
    report = {'kind': 'real_dds_host_not_android', 'rmw': os.environ.get('RMW_IMPLEMENTATION'),
              'namespace': namespace, 'passed': False, 'phaseObservations': {}}
    started = time.monotonic()
    def response(command=None):
        if command:
            child.stdin.write(command + '\n'); child.stdin.flush()
        with selectors.DefaultSelector() as selector:
            selector.register(child.stdout, selectors.EVENT_READ)
            if not selector.select(8):
                raise AssertionError('Native reader timeout')
        line = child.stdout.readline()
        if not line:
            raise AssertionError('Native reader exited')
        return json.loads(line)
    def watch(slot, suffix):
        assert response(f'watch {slot} rt{namespace}/{suffix} std_msgs::msg::dds_::Float64_ 0 0')['ok']
    def validate_phase(phase, rows):
        a = rows[0]['snapshot']['watches'][0]
        b = rows[-1]['snapshot']['watches'][0]
        healthy_a, healthy_b = rows[0]['snapshot']['watches'][1], rows[-1]['snapshot']['watches'][1]
        assert healthy_b['count'] > healthy_a['count'], 'Healthy intake stopped'
        assert healthy_b['generation'] == healthy_a['generation']
        if phase == 'gap':
            assert b['count'] == a['count'] and b['receivedMonotonicNs'] == a['receivedMonotonicNs']
            assert not b['fresh'], 'Source stop must become stale'
        else:
            expected = phase if phase in ('nan', 'positive_infinity', 'negative_infinity') else 'finite'
            unique = {r['snapshot']['watches'][0]['count'] for r in rows
                      if r['snapshot']['watches'][0]['numericFields'][0]['state'] == expected}
            assert len(unique) >= 5, f'{phase} was not observed by >=5 distinct 500ms polls'
            for r in rows[2:]:
                f = r['snapshot']['watches'][0]['numericFields'][0]
                assert f['value'] is None if expected != 'finite' else f['value'] != 0
        report['phaseObservations'][phase] = len(rows)
    try:
        assert response()['ok']
        for slot, suffix in enumerate(('a', 'b', 'stamped')):
            watch(slot, suffix)
        deadline = time.monotonic() + 12
        while True:
            first = response('poll')
            if all(first['watches'][i]['count'] > 1 for i in range(3)):
                break
            assert time.monotonic() < deadline, first
            time.sleep(.5)
        with records.open('w') as stream:
            def capture(phase):
                snapshot = response('poll')
                row = {'phase': phase, 'hostMonotonicNs': time.monotonic_ns(), 'snapshot': snapshot}
                stream.write(json.dumps(row) + '\n'); stream.flush()
                return row
            previous = None
            for phase in ('finite', 'nan', 'positive_infinity', 'negative_infinity', 'return', 'gap',
                          'return_after_gap', 'source_reset', 'source_jump', 'window'):
                actual = 'return' if phase == 'return_after_gap' else phase
                write_phase(control, actual)
                # Let a prior in-flight DDS write drain, without counting it toward the phase oracle.
                time.sleep(.65)
                rows, until = [], time.monotonic() + (34 if phase == 'window' else 6.5)
                while time.monotonic() < until:
                    assert publisher.poll() is None, 'Publisher died before deadline'
                    rows.append(capture(phase)); time.sleep(.5)
                validate_phase(actual, rows)
                last = rows[-1]['snapshot']['watches'][2]
                if previous and phase in ('source_reset', 'source_jump'):
                    assert last['receivedMonotonicNs'] > previous['receivedMonotonicNs']
                    if phase == 'source_reset':
                        assert last['sourceTimestampNs'] < previous['sourceTimestampNs']
                    else:
                        assert last['sourceTimestampNs'] > previous['sourceTimestampNs']
                previous = last
            baseline = rows[-1]['snapshot']
            assert response('remove 0')['ok']
            removed = capture('removed')['snapshot']
            assert removed['watches'][0]['count'] == 0 and not removed['watches'][0]['readerActive']
            watch(0, 'b')
            time.sleep(1)
            reassigned = capture('reassigned')['snapshot']
            assert reassigned['watches'][0]['generation'] > baseline['watches'][0]['generation']
            assert reassigned['watches'][1]['count'] > baseline['watches'][1]['count']
            stopped = response('stop')
            stream.write(json.dumps({'phase': 'stop', 'snapshot': stopped}) + '\n')
            assert not stopped['running']
        report.update(passed=True, elapsedSeconds=time.monotonic() - started,
                      sourceClockStimulus='separate real Cyclone dds_write_ts; Jazzy a/b use native RMW source clock')
        return report
    finally:
        (out / 'result.json').write_text(json.dumps(report, indent=2))
        if child.poll() is None:
            try:
                response('exit')
            except (AssertionError, BrokenPipeError):
                pass
            try:
                child.wait(timeout=4)
            except subprocess.TimeoutExpired:
                child.kill(); child.wait(timeout=4)
        write_phase(control, 'stop')
        try:
            publisher.wait(timeout=5)
        except subprocess.TimeoutExpired:
            publisher.kill(); publisher.wait(timeout=4)
        publisher_log.close()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--reader', type=Path, required=True)
    p.add_argument('--writer', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    print(json.dumps(run(a.reader, a.writer, a.out), indent=2))

if __name__ == '__main__':
    main()
