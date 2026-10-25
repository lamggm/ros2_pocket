#!/usr/bin/env python3
"""Official fixed-plan soak collector. Read-only ADB; never uses UIAutomator."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import selectors
import subprocess
import sys
import time

try:
    from . import qualification as q, device_snapshot as qa, android_instrumentation as ai
except ImportError:
    import qualification as q
    import device_snapshot as qa
    import android_instrumentation as ai

PACKAGE = 'com.lamggm.rospocket.dev'
COMPONENT = PACKAGE + '/com.lamggm.rospocket.MainActivity'
JOURNAL_MAX_RECORDS = 20_000
JOURNAL_MAX_BYTES = 32 * 1024 * 1024


def text(raw):
    return raw.decode('utf-8', errors='strict')


def parse_stat(raw):
    match = re.fullmatch(r'([1-9][0-9]*) \((.*)\) ([^\n]+)\n?', text(raw))
    ai.require(match is not None, 'malformed process stat')
    fields = match[3].split()
    ai.require(len(fields) >= 22 and fields[0] in {'R','S','D','I'}, 'unsupported/dead process stat')
    values = [fields[k] for k in (11,12,19)]
    ai.require(all(re.fullmatch(r'[0-9]{1,19}', v) for v in values), 'invalid process tick counters')
    utime, stime, start = map(int, values)
    ai.require(start > 0, 'unknown process starttime')
    return dict(pid=int(match[1]), cpu_ticks=utime+stime, start_time_ticks=start)


def parse_pss(raw):
    source = text(raw)
    modern = re.findall(r'^\s*TOTAL PSS:\s*([0-9]+)\b', source, re.M)
    legacy = re.findall(r'^\s*TOTAL\s+([0-9]+)(?:\s+[0-9]+)+\s*$', source, re.M)
    values = modern+legacy
    ai.require(len(modern) <= 1 and len(legacy) <= 1 and len(values) >= 1
               and len(set(map(int,values))) == 1 and int(values[0]) > 0, 'unavailable/ambiguous TOTAL PSS KiB')
    return int(values[0])


def parse_battery(raw):
    source = text(raw)
    ai.require('UPDATES STOPPED' not in source.upper(), 'battery service updates stopped/simulated')
    def field(name, pattern):
        values = re.findall(r'^\s*'+re.escape(name)+r':\s*('+pattern+r')\s*$', source, re.M)
        ai.require(len(values) == 1, 'missing/ambiguous battery '+name)
        return values[0]
    powered = [field(name+' powered', 'true|false') for name in ('AC','USB','Wireless')]
    dock = re.findall(r'^\s*Dock powered:\s*(true|false)\s*$', source, re.M)
    ai.require(len(dock) <= 1, 'ambiguous battery dock')
    level, scale, status = (int(field(k, '[0-9]+')) for k in ('level','scale','status'))
    ai.require(scale > 0 and 0 <= level <= scale and status in (2,3,4,5), 'invalid battery level/status')
    # Unknown/future power sources cannot silently become unplugged.
    sources = re.findall(r'^\s*(\S+) powered:', source, re.M)
    ai.require(set(sources) <= {'AC','USB','Wireless','Dock'}, 'unknown battery power source')
    return level/scale*100, any(v == 'true' for v in powered+dock) or status == 2


def cpu_percent(previous, current, hz):
    delta = (current['host_ns']-previous['host_ns'])/1e9
    uptime = current['uptime_s']-previous['uptime_s']
    ai.require(type(hz) is int and 1 <= hz <= 100000 and 13 <= delta <= 17
               and abs(delta-uptime) <= 1 and current['cpu_ticks'] >= previous['cpu_ticks'],
               'CPU clock/counter gap, suspend or invalid CLK_TCK')
    return (current['cpu_ticks']-previous['cpu_ticks'])/hz/delta*100


class Collector:
    """Device reads are injected only at the transport/clock boundary for host tests."""
    def __init__(self, transport, metadata, apk, now=time.monotonic_ns, sleep=time.sleep):
        self.transport, self.metadata, self.apk = transport, metadata, Path(apk)
        self.now, self.sleep = now, sleep
        self.previous_qa = None
        self.identity = None
        self.last_metrics = None
        self.build = None
        self.hz = None

    def read(self, args, deadline, limit=65536):
        ai.require(self.now() < deadline, 'sample absolute five-second deadline')
        raw = self.transport.read(args, deadline, limit)
        ai.require(self.now() <= deadline and len(raw) <= limit, 'sample deadline/byte bound exceeded')
        return raw

    def shell(self, args, deadline, limit=65536):
        return self.read(['exec-out']+args, deadline, limit)

    def preflight(self):
        q.validate_metadata(self.metadata)
        q.verify_candidate_binary(self.apk, self.metadata)
        deadline = self.now()+5_000_000_000
        ai.require(text(self.read(['get-state'], deadline)).strip() == 'device', 'ADB device unavailable')
        api = text(self.shell(['getprop','ro.build.version.sdk'], deadline)).strip()
        ai.require(api == '36', 'OS instrumentation parser requires supported API36 transport')
        fingerprint = self.shell(['getprop','ro.build.fingerprint'], deadline, 8192).strip()
        ai.require(bool(fingerprint) and b'\x00' not in fingerprint, 'unknown Android build')
        self.build = hashlib.sha256(fingerprint).hexdigest()
        self.hz = self.clock_ticks(deadline)
        self.installed(deadline)
        identity = self.process_identity(deadline)
        self.identity = {k:identity[k] for k in ('pid','uid','start_time_ticks')}
        return dict(android_build_sha256=self.build, android_api=36, clk_tck=self.hz, **self.identity)

    def clock_ticks(self, deadline):
        raw = text(self.shell(['getconf','CLK_TCK'], deadline)).strip()
        ai.require(re.fullmatch(r'[0-9]{1,6}', raw) is not None and 1 <= int(raw) <= 100000,
                   'unavailable/invalid device CLK_TCK')
        return int(raw)

    def installed(self, deadline):
        raw = text(self.shell(['pm','path',PACKAGE], deadline)).strip()
        ai.require(re.fullmatch(r'package:/[A-Za-z0-9_./=+~-]+/base\.apk', raw) is not None,
                   'single monolithic installed APK required')
        path = raw[len('package:'):]
        digest = text(self.shell(['sha256sum',path], deadline)).strip()
        ai.require(re.fullmatch(re.escape(self.metadata['sha256'])+r'\s+'+re.escape(path), digest) is not None,
                   'installed candidate APK changed/hash unknown')

    def process_identity(self, deadline):
        start = self.now()
        pid_raw = text(self.shell(['pidof',PACKAGE], deadline)).strip()
        ai.require(re.fullmatch(r'[1-9][0-9]{0,9}',pid_raw) is not None, 'missing/ambiguous target PID')
        pid = int(pid_raw)
        cmdline = self.shell(['cat',f'/proc/{pid}/cmdline'], deadline, 1024)
        ai.require(cmdline == PACKAGE.encode()+b'\x00', 'foreign/ambiguous process cmdline')
        stat_start = self.now()
        state = parse_stat(self.shell(['cat',f'/proc/{pid}/stat'], deadline, 8192))
        stat_end = self.now()
        ai.require(state['pid'] == pid, 'stat PID mismatch')
        status = text(self.shell(['cat',f'/proc/{pid}/status'], deadline, 32768))
        uid = re.findall(r'^Uid:\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)\s+([0-9]+)\s*$',status,re.M)
        ai.require(len(uid) == 1 and len(set(uid[0])) == 1 and int(uid[0][0]) > 0, 'unknown/mixed process UID')
        result = dict(state,uid=int(uid[0][0]),host_start_ns=start,host_end_ns=self.now(),
                      host_ns=(stat_start+stat_end)//2)
        if self.identity is not None:
            ai.require(all(result[k] == self.identity[k] for k in self.identity), 'target PID/UID/starttime changed')
        return result

    def process_metrics(self, deadline):
        result = self.process_identity(deadline)
        raw = text(self.shell(['cat','/proc/uptime'],deadline)).strip()
        ai.require(re.fullmatch(r'[0-9]+(?:\.[0-9]+)? [0-9]+(?:\.[0-9]+)?',raw) is not None, 'invalid device uptime')
        result['uptime_s'] = float(raw.split()[0])
        ai.require(math.isfinite(result['uptime_s']) and result['uptime_s'] > 0, 'unknown device uptime')
        return result

    def read_qa(self, deadline):
        start = self.now()
        data = qa.parse_snapshot(self.shell(['dumpsys','activity',COMPONENT,'--pocket-qa-v1'], deadline, qa.MAX_BYTES), self.metadata)
        ai.require(data['pid'] == self.identity['pid'] and data['testHooks']['startTimeTicks'] == self.identity['start_time_ticks'],
                   'QA/OS process identity mismatch')
        return data, dict(host_start_ns=start,host_end_ns=self.now())

    def observe_os(self, deadline):
        start = self.now()
        before = self.process_identity(deadline)
        command = ['dumpsys','activity','--proto','processes',PACKAGE]
        raw = self.shell(command,deadline,ai.MAX_BYTES)
        parsed = ai.parse_processes(raw,PACKAGE,before['pid'],before['uid'])
        after = self.process_identity(deadline)
        ai.require(parsed['targetActive'] is False, 'OS-visible instrumentation active; collection refused')
        return dict(schema_version=1,parser=ai.PARSER,android_api=36,android_build_sha256=self.build,
                    command=command,exit_code=0,stderr_empty=True,truncated=False,package=PACKAGE,
                    **{k:before[k] for k in ('pid','uid','start_time_ticks')},
                    input_sha256=hashlib.sha256(raw).hexdigest(),input_size_bytes=len(raw),targetActive=False,
                    host_start_ns=start,host_end_ns=self.now(),identity_before=before,identity_after=after)

    def sample(self, previous_metrics):
        q.verify_candidate_binary(self.apk,self.metadata)
        started = self.now()
        deadline = started+5_000_000_000
        self.installed(deadline)
        ai.require(self.clock_ticks(deadline) == self.hz, 'device CLK_TCK changed')
        # For both OFF and ON, independent OS evidence brackets a truly newer Java worker capture.
        before = self.observe_os(deadline)
        fence, fence_read = self.read_qa(deadline)
        if self.previous_qa is not None:
            qa.continuity(self.previous_qa,fence)
        export_fence = fence['exportedMonotonicNs']
        for attempt in range(60):
            current, capture_read = self.read_qa(deadline)
            if current['capturedMonotonicNs'] > export_fence:
                qa.continuity(fence,current)
                break
            qa.continuity(fence,current,advancing=False)
            ai.require(current['capturedMonotonicNs'] >= fence['capturedMonotonicNs']
                       and current['exportedMonotonicNs'] >= export_fence, 'regressed Java pending capture')
            self.sleep(min(0.05,max(0,(deadline-self.now())/1e9)))
        else:
            raise ValueError('fresh Java worker capture unavailable within bounded attempts')
        after = self.observe_os(deadline)
        instance = current['testHooks']['processInstance']
        for observation in (before,after):
            observation['process_instance'] = instance
            ai.validate_observation(observation,PACKAGE,False,self.build)
        metrics = self.process_metrics(deadline)
        pss = parse_pss(self.shell(['dumpsys','meminfo','--local',str(self.identity['pid'])],deadline))
        battery, charging = parse_battery(self.shell(['dumpsys','battery'],deadline))
        ai.require(charging is False, 'physical power connected/charging; collection refused')
        self.process_identity(deadline)
        ai.require(self.now() <= deadline, 'sample absolute five-second deadline')
        sample = dict(package=PACKAGE,pid=self.identity['pid'],monotonic_s=(capture_read['host_start_ns']+capture_read['host_end_ns'])/2e9,
                      pss_kib=pss,cpu_percent=cpu_percent(previous_metrics,metrics,self.hz),battery_percent=battery,
                      charging=False,received_counts=[w['count'] for w in current['watches']],
                      process_metrics=metrics,clk_tck=self.hz,
                      collection_read=dict(host_start_ns=started,host_end_ns=self.now()),
                      diagnostic_proof=dict(os_before=before,os_after=after,fence_read=fence_read,
                                            capture_read=capture_read,export_fence_ns=export_fence,
                                            diagnostic=current['testHooks'],qa=current))
        self.previous_qa = current
        self.last_metrics = metrics
        return sample


def validate_collection(data, metadata):
    """Replay official collector proof for BOTH binary policies, without device I/O."""
    ai.require(type(data['collector_schema']) is int and data['collector_schema'] == 1
               and data['collection_status'] == 'complete', 'collector attempt incomplete/unknown')
    device = data['device']; hz = device['clk_tck']; build = device['android_build_sha256']
    ai.require(type(hz) is int and 1 <= hz <= 100000 and ai.digest(build)
               and type(device['android_api']) is int and device['android_api'] == 36, 'unknown collector clock/build')
    identity = tuple(device[k] for k in ('pid','uid','start_time_ticks'))
    ai.require(all(ai.integer(v,1) for v in identity) and identity[0] == data['pid'], 'collector process identity absent')
    def metric(record):
        ai.require(tuple(record[k] for k in ('pid','uid','start_time_ticks')) == identity
                   and all(ai.integer(record[k],1) for k in ('pid','uid','start_time_ticks','host_ns'))
                   and ai.integer(record['cpu_ticks']), 'collector metric PID/starttime/ticks invalid')
        a,b = ai.interval(record)
        ai.require(a <= record['host_ns'] <= b and type(record['uptime_s']) in (int,float)
                   and math.isfinite(record['uptime_s']) and record['uptime_s'] > 0, 'collector metric clock invalid')
    previous = data['cpu_baseline']; metric(previous)
    last_qa = None; last_fence = -1; last_after = -1; process_instance = None
    for sample in data['samples']:
        ai.require(type(sample['clk_tck']) is int and sample['clk_tck'] == hz, 'collector CLK_TCK changed')
        current = sample['process_metrics']; metric(current)
        measured_cpu = cpu_percent(previous,current,hz)
        ai.require(type(sample['cpu_percent']) in (int,float) and math.isfinite(sample['cpu_percent'])
                   and abs(sample['cpu_percent']-measured_cpu) <= 1e-9, 'collector CPU delta/units mismatch')
        start,end = ai.interval(sample['collection_read'])
        proof = sample['diagnostic_proof']; before,after = proof['os_before'],proof['os_after']
        a = ai.validate_observation(before,PACKAGE,False,build)
        b = ai.validate_observation(after,PACKAGE,False,build)
        if process_instance is None: process_instance = a[3]
        ai.require(a == b == identity+(process_instance,), 'collector OS process/instance changed')
        b0,b1 = ai.interval(before); f0,f1 = ai.interval(proof['fence_read'])
        c0,c1 = ai.interval(proof['capture_read']); a0,a1 = ai.interval(after)
        ai.require(last_after < b0 and start <= b0 <= b1 <= f0 <= f1 <= c0 <= c1 <= a0 <= a1 <= end
                   and a1 <= current['host_start_ns'] <= current['host_end_ns'] <= end,
                   'collector causal clock/order/deadline mismatch')
        ai.require(type(sample['monotonic_s']) in (int,float) and math.isfinite(sample['monotonic_s'])
                   and c0 <= sample['monotonic_s']*1e9 <= c1, 'collector sample clock not from QA read')
        observed = proof['qa']; qa.validate_snapshot(observed,metadata)
        diagnostic = observed['testHooks']; fence = proof['export_fence_ns']
        ai.require(diagnostic == proof['diagnostic'] and diagnostic['pid'] == identity[0]
                   and diagnostic['startTimeTicks'] == identity[2] and diagnostic['processInstance'] == process_instance,
                   'collector mixed QA/diagnostic/OS identity')
        ai.require(ai.integer(fence,1) and last_fence < fence < observed['capturedMonotonicNs'], 'collector stale Java export fence')
        if last_qa is not None:
            qa.continuity(last_qa,observed)
            ai.require(fence > last_qa['exportedMonotonicNs'], 'collector replayed Java export fence')
        ai.require(sample['received_counts'] == [w['count'] for w in observed['watches']], 'collector counts not genuine QA counters')
        previous = current; last_qa = observed; last_fence = fence; last_after = a1


class Adb:
    """Strictly bounded pipes and process lifetime; never shell=True/global adb."""
    def __init__(self, executable, serial, journal=None):
        ai.require(isinstance(serial,str) and re.fullmatch(r'[A-Za-z0-9_.:-]{1,256}',serial) is not None,
                   'explicit valid authorized device serial required')
        self.executable, self.serial, self.journal = str(executable), serial, journal

    def read(self, args, deadline, limit=65536):
        import signal
        start = time.monotonic_ns()
        deadline = min(deadline,start+5_000_000_000)
        ai.require(start < deadline, 'ADB deadline expired before command')
        output, errors = bytearray(), bytearray()
        process = None
        success = False
        reason = None
        try:
            process = subprocess.Popen([self.executable,'-s',self.serial,*args],stdin=subprocess.DEVNULL,
                                       stdout=subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True)
            with selectors.DefaultSelector() as selector:
                for stream, target, bound in ((process.stdout,output,limit),(process.stderr,errors,1024)):
                    os.set_blocking(stream.fileno(),False)
                    selector.register(stream,selectors.EVENT_READ,(target,bound))
                while selector.get_map():
                    remaining = (deadline-time.monotonic_ns())/1e9
                    ai.require(remaining > 0, 'ADB bounded read timeout')
                    for key, _ in selector.select(remaining):
                        chunk = os.read(key.fileobj.fileno(),8192)
                        if not chunk:
                            selector.unregister(key.fileobj); continue
                        target,bound = key.data
                        ai.require(len(target)+len(chunk) <= bound, 'ADB output byte limit exceeded')
                        target.extend(chunk)
                remaining = (deadline-time.monotonic_ns())/1e9
                ai.require(remaining > 0, 'ADB command exit deadline')
                try: code = process.wait(timeout=remaining)
                except subprocess.TimeoutExpired as error: raise ValueError('ADB command exit timeout') from error
            ai.require(code == 0 and not errors, 'ADB unsuccessful exit/stderr')
            success = True
            return bytes(output)
        except BaseException as error:
            reason = type(error).__name__+': '+str(error)
            raise
        finally:
            if process is not None:
                if not success:
                    try: os.killpg(process.pid,signal.SIGKILL)
                    except ProcessLookupError: pass
                    process.wait(timeout=1)
                process.stdout.close(); process.stderr.close()
            if self.journal is not None:
                # Protobuf can contain foreign instrumentation arguments: hashes only.
                import base64
                retained = {} if '--proto' in args or 'ro.build.fingerprint' in args else {'input_b64':base64.b64encode(output).decode()}
                self.journal.event('read',command=args,host_start_ns=start,host_end_ns=time.monotonic_ns(),
                                   success=success,error=reason,input_size_bytes=len(output),
                                   input_sha256=hashlib.sha256(output).hexdigest(),stderr_size_bytes=len(errors),**retained)


class Journal:
    """One fresh private run directory; fsync each bounded append, no resume/stitching."""
    def __init__(self, directory, repo):
        self.path, self.repo = Path(directory), Path(repo).resolve()
        ai.require(not self.path.is_symlink() and self.path.resolve().is_relative_to(self.repo/'build'),
                   'collector output must stay in repo build/')
        self.path.mkdir(mode=0o700, parents=True, exist_ok=False)
        self.bytes = self.records = 0
        self.fd = os.open(self.path/'events.jsonl', os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW, 0o600)

    def event(self, kind, **fields):
        raw = (json.dumps(dict(kind=kind,**fields),separators=(',',':'),allow_nan=False)+'\n').encode()
        ai.require(self.records < JOURNAL_MAX_RECORDS
                   and self.bytes+len(raw) <= JOURNAL_MAX_BYTES, 'progress journal budget exceeded')
        view = memoryview(raw)
        while view:
            written = os.write(self.fd,view)
            ai.require(written > 0, 'journal short write')
            view = view[written:]
        os.fsync(self.fd)
        self.bytes += len(raw); self.records += 1

    def save(self, name, data):
        q.write_report(self.path/name,data,self.repo)
        # write_report is atomic; add durability of the completed private snapshot.
        with (self.path/name).open('rb') as stream: os.fsync(stream.fileno())
        directory = os.open(self.path,os.O_DIRECTORY)
        try: os.fsync(directory)
        finally: os.close(directory)

    def close(self):
        if self.fd is not None:
            os.close(self.fd); self.fd = None


def run_campaign(collector, journal, transport_validation=None, preflight_only=False):
    """Never accepts shorter/relaxed schedules. Failure preserves every completed point."""
    data = dict(schema_version=collector.metadata['schema_version'],collector_schema=1,
                snapshot=collector.metadata,plan=q.SOAK_PLAN,package=PACKAGE,pid=None,watches=4,graph=True,
                collection_status='incomplete',samples=[])
    failure = None
    journal_warning = None
    try:
        journal.event('started',plan=q.SOAK_PLAN,preflight_only=preflight_only)
        journal.save('soak-input.json',data)
        identity = collector.preflight()
        data['pid'] = identity['pid']; data['device'] = identity
        if collector.metadata['schema_version'] == 2:
            data['os_transport_validation'] = transport_validation
            ai.validate_campaign(dict(data,schema_version=2),collector.metadata)
            ai.require(transport_validation['android_build_sha256'] == collector.build,
                       'OS positive/negative qualification belongs to different device build')
        journal.event('preflight',identity=identity)
        baseline = collector.process_metrics(collector.now()+5_000_000_000)
        data['cpu_baseline'] = baseline
        if preflight_only:
            deadline = collector.now()+5_000_000_000
            before = collector.observe_os(deadline)
            observed, read = collector.read_qa(deadline)
            after = collector.observe_os(deadline)
            journal.event('preflight-observation',qa=observed,os_before=before,os_after=after,read=read)
            failure = 'preflight only; no measured 30-minute soak'
        else:
            start = collector.now()+15_000_000_000  # first CPU window is measured, never zero-filled
            previous = baseline
            for index in range(122):
                due = start+index*15_000_000_000
                delay = (due-collector.now())/1e9
                ai.require(delay >= -2, 'absolute schedule gap; no catch-up/resume')
                if delay > 0: collector.sleep(delay)
                sample = collector.sample(previous)
                if data['samples']:
                    dt = sample['monotonic_s']-data['samples'][-1]['monotonic_s']
                    ai.require(13 <= dt <= 17, 'observed sample cadence gap')
                data['samples'].append(sample)
                journal.event('sample',index=index,sample=sample)
                journal.save('soak-input.json',data)
                previous = collector.last_metrics
                span = data['samples'][-1]['monotonic_s']-data['samples'][0]['monotonic_s']
                ai.require(span <= 1815, 'absolute campaign duration exceeded')
                if len(data['samples']) >= 121 and span >= 1800:
                    break
            data['collection_status'] = 'complete'
    except KeyboardInterrupt:
        failure = 'interrupted; run cannot resume'
    except (ValueError,OSError,TypeError,KeyError,OverflowError) as error:
        failure = str(error)
    try:
        if failure:
            data['collection_status'] = 'incomplete'; data['collection_error'] = failure
            try:
                journal.event('incomplete',reason=failure,completed_points=len(data['samples']))
            except (ValueError,OSError) as error:
                journal_warning = 'incomplete event not retained: '+str(error)
        journal.save('soak-input.json',data)
        analysis = q.analyze_soak(data,collector.metadata,collector.apk)
        if failure and analysis['status'] == 'passed':
            analysis = dict(status='incomplete',blockers=[failure],metrics=analysis['metrics'])
        if journal_warning is not None:
            analysis = dict(analysis,journal_warning=journal_warning)
        journal.save('analysis.json',analysis)
        try:
            journal.event('finished',analysis=analysis,completed_points=len(data['samples']))
        except (ValueError,OSError) as error:
            journal_warning = 'finished event not retained: '+str(error)
        result = dict(collection_status=data['collection_status'],analysis=analysis,points=len(data['samples']))
        if journal_warning is not None:
            result['journal_warning'] = journal_warning
        return result
    finally:
        journal.close()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command',required=True)
    for command in ('collect','preflight'):
        sub = commands.add_parser(command)
        sub.add_argument('--metadata',type=Path,required=True)
        sub.add_argument('--apk',type=Path,required=True)
        sub.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[1])
        sub.add_argument('--load-plan',type=Path,required=True,help='Explicit reviewed fixed SOAK_PLAN JSON, never configurable budgets')
        sub.add_argument('--serial',required=True)
        sub.add_argument('--adb',default='adb',help='ADB executable; no shell expansion')
        sub.add_argument('--output-dir',type=Path,required=True,help='Fresh private directory inside repo build/')
        sub.add_argument('--os-transport-validation',type=Path,help='Reviewed physical OS positive/negative attestation; mandatory for ON')
        sub.add_argument('--authorize-read-only',action='store_true',required=True,
                         help='Explicitly authorize reads of this exact selected device/package')
    args = parser.parse_args(argv)
    journal = None
    try:
        metadata = q.load_json(args.metadata)
        ai.require(q.snapshot_id(q.load_json(args.load_plan)) == q.snapshot_id(q.SOAK_PLAN),
                   'divergent workload/budgets; exact predeclared SOAK_PLAN required')
        q.validate_metadata(metadata)
        q.verify_candidate_binary(args.apk,metadata)
        try:
            from .artifact_metadata import source_provenance
        except ImportError:
            from artifact_metadata import source_provenance
        ai.require(source_provenance(args.repo) == metadata['source'], 'current source/candidate mismatch')
        q.verify_diagnostic_apk(args.apk,metadata,args.repo)
        validation = q.load_json(args.os_transport_validation) if args.os_transport_validation else None
        # Missing/unqualified ON transport is blocked BEFORE any device read.
        if metadata['schema_version'] == 2:
            ai.validate_campaign(dict(schema_version=2,os_transport_validation=validation,samples=[]),metadata)
        transport = Adb(args.adb,args.serial)
        journal = Journal(args.output_dir,args.repo)
        transport.journal = journal
        import signal
        def interrupted(signum, frame): raise KeyboardInterrupt()
        signal.signal(signal.SIGTERM,interrupted)
        result = run_campaign(Collector(transport,metadata,args.apk),journal,validation,args.command=='preflight')
        print(json.dumps(result,indent=2,allow_nan=False))
        return 0 if result['analysis']['status'] == 'passed' and result['collection_status'] == 'complete' else 1
    except (ValueError,OSError,KeyError,TypeError,OverflowError) as error:
        print('collector error: '+str(error),file=sys.stderr)
        return 2
    finally:
        if journal is not None: journal.close()


if __name__ == '__main__':
    raise SystemExit(main())
