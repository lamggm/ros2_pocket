#!/usr/bin/env python3
"""Offline, fail-closed preparation of v0.2 qualification; never touches a device."""
import copy
import hashlib
import json
import math
from pathlib import Path
import re
import tempfile

JSON_MAX_BYTES = 4 * 1024 * 1024
JSON_MAX_CONTAINER_DEPTH = 64

# Stable required IDs; branch existence and old tablet checks satisfy none of these.
GATES = {
    'host-tests': 'Final source: portable/native/Java/Python integration tests',
    'host-lint': 'Final source: Android lint',
    'host-build': 'Final source: verified debug APK and reproducible build',
    'secret-scan': 'Final history AND current source secret scan (redacted)',
    'increment-review': 'Explicit review of all eight integrated increments',
    'fastdds': 'Physical Jazzy Fast DDS discovery, fresh values and real nodes',
    'cyclonedds': 'Physical Jazzy Cyclone DDS discovery, fresh values and real nodes',
    'wrong-domain': 'Wrong domain isolates; correct domain restores fresh intake',
    'lifecycle': 'Stop/Home/process restart; cleanup and new samples, not cached status',
    'reader-failure': 'Actual topic AND reader creation failure; persistent inactive state and recovery',
    'qos-removal': 'QoS rejection/recovery, endpoint/node removal/replacement, multiple graph writers',
    'rosout': 'Severities, UTF-8, long lines, burst bound, pause/search/clear',
    'structured': 'Vector3/Quaternion/Twist/Pose/Odometry, headers/frames/covariance, NaN/Inf, unknown types',
    'multiwatch': 'Four independent watches, fifth refused, mixed rates/QoS, one failure, idempotent teardown',
    'graphs': 'Real numeric fields, bounded buffers, gaps/NaN/Inf not zero, pause/resume/selection',
    'recovery-plan': 'Operator-approved alternate control and tested recovery before network changes',
    'wifi-loss-return': 'Physical Wi-Fi loss/return with fresh intake and alternate-control recovery',
    'address-change': 'Physical address change/rebind with fresh intake and alternate-control recovery',
    'soak': 'Measured 30-minute four-watch plus graph run against predeclared budgets',
    'installed-apk': 'Installed package/version/hash/signer equals candidate; fresh real reading',
    'operator-acceptance': 'Separate final manual operator acceptance of this exact candidate',
}


def validate_metadata(data):
    """Validate the artifact_metadata.py v1 contract, without inventing defaults."""
    try:
        source = data['source']
        signing = data['signing']
        valid = (type(data['schema_version']) is int and data['schema_version'] == 1
                 and data['package'] == 'com.lamggm.rospocket.dev'
                 and type(data['version_code']) is int and data['version_code'] > 0
                 and isinstance(data['version_name'], str) and bool(data['version_name'])
                 and isinstance(data['apk'], str) and bool(data['apk'])
                 and type(data['size_bytes']) is int and data['size_bytes'] > 0
                 and data['debuggable'] is True
                 and isinstance(data['abis'], list) and 'arm64-v8a' in data['abis']
                 and signing['class'] == 'debug'
                 and isinstance(signing['certificate_dn'], str)
                 and 'CN=Android Debug' in signing['certificate_dn']
                 and type(source['dirty']) is bool
                 and re.fullmatch(r'[0-9a-f]{40}', source['head_sha'])
                 and all(re.fullmatch(r'[0-9a-f]{64}', value) for value in
                         [source['snapshot_sha256'], data['sha256'], signing['certificate_sha256']]))
    except (KeyError, TypeError, AttributeError):
        valid = False
    if not valid:
        raise ValueError('invalid artifact_metadata v1 snapshot')


def validate_report(report):
    try:
        validate_metadata(report['snapshot'])
        gates = report['gates']
        ids = [gate['id'] for gate in gates]
        valid = (type(report['schema_version']) is int and report['schema_version'] == 1
                 and report['soak_plan'] == SOAK_PLAN
                 and isinstance(gates, list) and len(ids) == len(GATES)
                 and len(set(ids)) == len(ids) and set(ids) == set(GATES)
                 and all(gate['status'] in {'pending', 'passed', 'failed', 'blocked'}
                         and isinstance(gate['evidence'], list) for gate in gates))
    except (KeyError, TypeError, AttributeError):
        valid = False
    if not valid:
        raise ValueError('invalid report: exact unique gates and explicit statuses required')


def new_report(metadata):
    validate_metadata(metadata)
    return {'schema_version': 1, 'snapshot': copy.deepcopy(metadata),
            'soak_plan': copy.deepcopy(SOAK_PLAN),
            'gates': [{'id': gate, 'status': 'pending', 'evidence': []} for gate in GATES]}


SOAK_PLAN = {
    'duration_s': 1800, 'interval_s': 15, 'interval_tolerance_s': 2,
    'watches': 4, 'graph_required': True, 'expected_hz': [2, 2, 2, 2],
    'pss_peak_kib': 262144, 'pss_growth_kib': 32768,
    'cpu_mean_percent': 25, 'cpu_peak_percent': 80,
    'ingestion_min_ratio': 0.90, 'battery_loss_percentage_points': 10,
}


def analyze_soak(data, metadata):
    """Analyze measured input only; absent or invalid metrics remain explicitly unknown."""
    validate_metadata(metadata)
    unknown = dict.fromkeys(('duration_s', 'pss_peak_kib', 'pss_growth_kib', 'cpu_mean_percent',
                             'cpu_peak_percent', 'ingestion_min_ratio', 'battery_loss_percentage_points'))
    incomplete = []
    if not isinstance(data, dict):
        return {'status': 'incomplete', 'blockers': ['invalid soak object'], 'metrics': unknown}
    if data.get('snapshot') != metadata:
        incomplete.append('soak snapshot mismatch')
    if data.get('plan') != SOAK_PLAN:
        incomplete.append('predeclared plan mismatch')
    pid = data.get('pid')
    if data.get('package') != metadata['package'] or type(pid) is not int or pid <= 0:
        incomplete.append('exact app/pid identity missing')
    if data.get('watches') != 4 or data.get('graph') is not True:
        incomplete.append('four watches plus graph not observed')
    samples = data.get('samples')
    if not isinstance(samples, list) or not 121 <= len(samples) <= 122:
        incomplete.append('bounded complete 30-minute sample set required (121-122)')
    if incomplete:
        return {'status': 'incomplete', 'blockers': incomplete, 'metrics': unknown}
    for sample in samples:
        if not isinstance(sample, dict):
            incomplete.append('invalid sample')
            continue
        if sample.get('package') != metadata['package'] or sample.get('pid') != pid:
            incomplete.append('app/pid changed or unknown')
        for key in ('monotonic_s', 'pss_kib', 'cpu_percent', 'battery_percent'):
            value = sample.get(key)
            try:
                valid = type(value) in (int, float) and math.isfinite(value) and value >= 0
            except OverflowError:
                valid = False
            if not valid:
                incomplete.append(f'{key}: unknown/invalid')
        battery = sample.get('battery_percent')
        if type(battery) in (int, float) and battery > 100:
            incomplete.append('invalid battery percentage')
        if sample.get('charging') is not False:
            incomplete.append('battery requires observed unplugged state')
        counts = sample.get('received_counts')
        if (not isinstance(counts, list) or len(counts) != 4
                or any(type(count) is not int or not 0 <= count <= 2 ** 64 - 1 for count in counts)):
            # Native intake counters are uint64; subtract integers before computing ratios.
            incomplete.append('four uint64 received counts required')
    if incomplete:
        return {'status': 'incomplete', 'blockers': sorted(set(incomplete)), 'metrics': unknown}
    intervals = [b['monotonic_s'] - a['monotonic_s'] for a, b in zip(samples, samples[1:])]
    duration = samples[-1]['monotonic_s'] - samples[0]['monotonic_s']
    if not 1800 <= duration <= 1815 or any(not 13 <= interval <= 17 for interval in intervals):
        return {'status': 'incomplete', 'blockers': ['duration/cadence incomplete or nonmonotonic'],
                'metrics': unknown}
    # CPU samples represent the preceding measured interval, normalized to one core.
    try:
        cpu_mean = sum(b['cpu_percent'] * interval for b, interval in zip(samples[1:], intervals)) / duration
    except OverflowError:
        cpu_mean = None
    if cpu_mean is None or not math.isfinite(cpu_mean):
        return {'status': 'incomplete', 'blockers': ['cpu_percent: arithmetic out of range'],
                'metrics': unknown}
    ratios = [(b['received_counts'][index] - a['received_counts'][index]) / (2 * interval)
              for a, b, interval in zip(samples, samples[1:], intervals) for index in range(4)]
    metrics = dict(duration_s=duration, pss_peak_kib=max(s['pss_kib'] for s in samples),
                   pss_growth_kib=samples[-1]['pss_kib'] - samples[0]['pss_kib'],
                   cpu_mean_percent=cpu_mean, cpu_peak_percent=max(s['cpu_percent'] for s in samples),
                   ingestion_min_ratio=min(ratios),
                   battery_loss_percentage_points=max(0, samples[0]['battery_percent'] - samples[-1]['battery_percent']))
    failed = [f'{key}: budget exceeded' for key, value in metrics.items()
              if key not in {'duration_s', 'ingestion_min_ratio'} and value > SOAK_PLAN[key]]
    if metrics['ingestion_min_ratio'] < SOAK_PLAN['ingestion_min_ratio']:
        failed.append('ingestion_min_ratio: stall/reset or insufficient measured intake')
    return {'status': 'failed' if failed else 'passed', 'blockers': failed, 'metrics': metrics}


MANUAL_GATES = set(GATES) - {'host-tests', 'host-lint', 'host-build', 'secret-scan', 'soak'}


def snapshot_id(metadata):
    return hashlib.sha256(json.dumps(metadata, sort_keys=True, separators=(',', ':'),
                                     allow_nan=False).encode()).hexdigest()


def evidence_file(root, name):
    """Only nonempty local files contained within the explicit evidence root."""
    if not isinstance(name, str) or not name or Path(name).is_absolute() or '..' in Path(name).parts:
        raise ValueError('evidence path must be relative and contained')
    path = (Path(root) / name).resolve()
    if not path.is_relative_to(Path(root).resolve()) or not path.is_file() or not path.stat().st_size:
        raise ValueError('missing, empty or escaped evidence')
    return path


def record_result(report, metadata, gate_id, status, evidence, evidence_root, operator='', note=''):
    validate_report(report)
    validate_metadata(metadata)
    if report['snapshot'] != metadata:
        raise ValueError('snapshot mismatch')
    if gate_id not in GATES or status not in {'pending', 'passed', 'failed', 'blocked'}:
        raise ValueError('unknown gate or status')
    if not isinstance(evidence, list) or any(not isinstance(p, str) for p in evidence):
        raise ValueError('evidence must be a list of paths')
    if len(set(evidence)) != len(evidence) or (status == 'passed' and not evidence):
        raise ValueError('passed requires unique nonempty evidence')
    if status == 'passed' and gate_id in MANUAL_GATES and not (
            isinstance(operator, str) and operator.strip() and isinstance(note, str) and note.strip()):
        raise ValueError('manual gate requires explicit operator and attestation note')
    if gate_id in {'soak', 'installed-apk'} and status == 'passed':
        validate_structured_evidence(gate_id, evidence, evidence_root, metadata)
    resolved_paths = [evidence_file(evidence_root, name) for name in evidence]
    if len(set(resolved_paths)) != len(resolved_paths):
        raise ValueError('duplicate evidence')
    files = [{'path': name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
             for name, path in zip(evidence, resolved_paths)]
    result = copy.deepcopy(report)
    for gate in result['gates']:
        if gate['id'] == gate_id:
            gate.update(status=status, evidence=files, snapshot_id=snapshot_id(metadata),
                        operator=operator, note=note)
    return result


def evaluate_report(report, metadata, current_source, evidence_root):
    validate_report(report)
    validate_metadata(metadata)
    blockers = []
    if report['snapshot'] != metadata:
        blockers.append('snapshot mismatch')
    if metadata['source'] != current_source:
        blockers.append('current source mismatch')
    if metadata['source']['dirty'] or current_source.get('dirty') is not False:
        blockers.append('dirty source')
    for gate in report['gates']:
        gate_id = gate['id']
        if gate['status'] != 'passed':
            blockers.append(f"{gate_id}: {gate['status']}")
            continue
        if gate.get('snapshot_id') != snapshot_id(metadata):
            blockers.append(f'{gate_id}: result binding mismatch')
        if gate_id in MANUAL_GATES and not all(
                isinstance(gate.get(key), str) and gate[key].strip() for key in ('operator', 'note')):
            blockers.append(f'{gate_id}: manual attestation missing')
        try:
            if not gate['evidence']:
                raise ValueError('evidence missing')
            paths = [item['path'] for item in gate['evidence']]
            if len(set(paths)) != len(paths):
                raise ValueError('duplicate evidence')
            resolved_paths = [evidence_file(evidence_root, path) for path in paths]
            if len(set(resolved_paths)) != len(resolved_paths):
                raise ValueError('duplicate evidence')
            for item, path in zip(gate['evidence'], resolved_paths):
                actual = hashlib.sha256(path.read_bytes()).hexdigest()
                if actual != item['sha256']:
                    raise ValueError('evidence hash mismatch')
            if gate_id in {'soak', 'installed-apk'}:
                validate_structured_evidence(gate_id, paths, evidence_root, metadata)
        except (ValueError, KeyError, TypeError, OSError) as error:
            blockers.append(f'{gate_id}: invalid evidence ({error})')
    return {'ready': not blockers, 'blockers': blockers}


def load_json(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f'duplicate JSON key: {key}')
            result[key] = value
        return result

    def invalid_constant(value):
        raise ValueError('nonfinite JSON number: ' + value)

    def finite_float(value):
        number = float(value)
        if not math.isfinite(number):
            invalid_constant(value)
        return number

    if Path(path).stat().st_size > JSON_MAX_BYTES:
        raise ValueError('JSON exceeds bounded 4 MiB input limit')
    try:
        data = json.loads(Path(path).read_text(encoding='utf-8'), object_pairs_hook=unique,
                          parse_constant=invalid_constant, parse_float=finite_float)
    except RecursionError as error:
        raise ValueError('JSON nesting exceeds decoder limit') from error
    validate_json_depth(data)
    return data


def validate_json_depth(data):
    """Count all containers, including unknown fields, before recursive consumers."""
    # Iterator frames bound traversal memory by depth, not the number of siblings.
    stack = [iter((data,))]
    while stack:
        try:
            value = next(stack[-1])
        except StopIteration:
            stack.pop()
            continue
        if isinstance(value, (dict, list)):
            if len(stack) > JSON_MAX_CONTAINER_DEPTH:
                raise ValueError(f'JSON container depth exceeds {JSON_MAX_CONTAINER_DEPTH}')
            stack.append(iter(value.values() if isinstance(value, dict) else value))


def validate_structured_evidence(gate_id, paths, root, metadata):
    if len(paths) != 1:
        raise ValueError(f'{gate_id}: exactly one input JSON required')
    data = load_json(evidence_file(root, paths[0]))
    if gate_id == 'soak':
        analysis = analyze_soak(data, metadata)
        if analysis['status'] != 'passed':
            raise ValueError('measured soak not passed: ' + '; '.join(analysis['blockers']))
    else:
        validate_metadata(data)
        # Installed base APK may have a different local filename, never a different identity.
        if {k: v for k, v in data.items() if k != 'apk'} != {
                k: v for k, v in metadata.items() if k != 'apk'}:
            raise ValueError('installed APK metadata mismatch')


def write_report(path, data, repo):
    destination = Path(path).resolve()
    if not destination.is_relative_to((Path(repo) / 'build').resolve()):
        raise ValueError('qualification outputs must stay under repo build/')
    validate_json_depth(data)
    serialized = (json.dumps(data, indent=2, allow_nan=False) + '\n').encode('utf-8')
    if len(serialized) > JSON_MAX_BYTES:
        raise ValueError('JSON exceeds bounded 4 MiB output limit')
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        # Exclusive, unpredictable creation: never follow a pre-existing .part symlink.
        with tempfile.NamedTemporaryFile(mode='wb', dir=destination.parent,
                                         prefix=f'.{destination.name}.', suffix='.part',
                                         delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(serialized)
        temporary.replace(destination)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def main():
    import argparse
    import sys
    from artifact_metadata import source_provenance

    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('matrix', help='Print exact required IDs and predeclared soak plan')
    for command in ('init', 'record', 'evaluate', 'analyze-soak'):
        sub = commands.add_parser(command)
        sub.add_argument('--metadata', type=Path, required=True)
        if command == 'analyze-soak':
            sub.add_argument('--input', type=Path, required=True)
            continue
        sub.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[1])
        if command in {'init', 'evaluate'}:
            sub.add_argument('--apk', type=Path, required=True)
        if command == 'init':
            sub.add_argument('--output', type=Path, required=True)
        else:
            sub.add_argument('--report', type=Path, required=True)
            sub.add_argument('--evidence-root', type=Path, required=True)
        if command == 'record':
            sub.add_argument('--gate', choices=list(GATES), required=True)
            sub.add_argument('--status', choices=['pending', 'passed', 'failed', 'blocked'], required=True)
            sub.add_argument('--evidence', action='append', default=[])
            sub.add_argument('--operator', default='')
            sub.add_argument('--note', default='')
    args = parser.parse_args()
    try:
        if args.command == 'matrix':
            result = {'gates': GATES, 'manual_gates': sorted(MANUAL_GATES), 'soak_plan': SOAK_PLAN}
        else:
            metadata = load_json(args.metadata)
            validate_metadata(metadata)
            if args.command == 'analyze-soak':
                result = analyze_soak(load_json(args.input), metadata)
                print(json.dumps(result, indent=2, allow_nan=False))
                return 0 if result['status'] == 'passed' else 1
            current_source = source_provenance(args.repo)
            if current_source != metadata['source']:
                raise ValueError('current source mismatch; regenerate metadata and rerun affected gates')
            if args.command in {'init', 'evaluate'}:
                if (not args.apk.is_file() or args.apk.stat().st_size != metadata['size_bytes']
                        or hashlib.sha256(args.apk.read_bytes()).hexdigest() != metadata['sha256']):
                    raise ValueError('APK bytes/hash/size mismatch or absent APK')
            if args.command == 'init':
                result = new_report(metadata)
                write_report(args.output, result, args.repo)
            else:
                if not args.evidence_root.resolve().is_relative_to((args.repo / 'build').resolve()):
                    raise ValueError('evidence root must stay under repo build/')
                report = load_json(args.report)
                if args.command == 'record':
                    result = record_result(report, metadata, args.gate, args.status, args.evidence,
                                           args.evidence_root, args.operator, args.note)
                    write_report(args.report, result, args.repo)
                else:
                    result = evaluate_report(report, metadata, current_source, args.evidence_root)
                    print(json.dumps(result, indent=2, allow_nan=False))
                    return 0 if result['ready'] else 1
        print(json.dumps(result, indent=2, allow_nan=False))
        return 0
    except (ValueError, OSError, TypeError, KeyError) as error:
        print(f'qualification error: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
