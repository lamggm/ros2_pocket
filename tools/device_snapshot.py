#!/usr/bin/env python3
"""Bounded read-only consumer of the real Activity QA frame (no Android actions)."""
import json
import math

try:
    from . import qualification as q
    from .android_instrumentation import integer, require, validate_graph
except ImportError:
    import qualification as q
    from android_instrumentation import integer, require, validate_graph

MAX_BYTES = 32768  # Android headers plus the producer's 24 KiB frame
FRAME_MAX_BYTES = 24576
BEGIN = 'ROS_POCKET_QA_V1_BEGIN'
END = 'ROS_POCKET_QA_V1_END'


def parse_snapshot(raw, metadata):
    """Return exact integers and immutable-by-ownership decoded worker metadata."""
    require(isinstance(raw, bytes) and len(raw) <= MAX_BYTES, 'QA transport byte limit')
    try:
        lines = raw.decode('utf-8', errors='strict').splitlines()
        require(lines.count(BEGIN) == lines.count(END) == 1, 'QA single complete frame required')
        start, end = lines.index(BEGIN), lines.index(END)
        require(end == start + 2, 'QA one compact JSON line required')
        payload = lines[start+1]
        require(len(payload.encode()) <= FRAME_MAX_BYTES, 'QA frame byte limit')
        def unique(pairs):
            result = {}
            for key, value in pairs:
                require(key not in result, 'duplicate QA JSON key')
                result[key] = value
            return result
        def number(text):
            value = float(text)
            require(math.isfinite(value), 'nonfinite QA JSON number')
            return value
        def constant(text):
            raise ValueError('nonfinite QA JSON constant')
        data = json.loads(payload, object_pairs_hook=unique, parse_float=number, parse_constant=constant)
        q.validate_json_depth(data)
        validate_snapshot(data, metadata)
        return data
    except (UnicodeError, KeyError, TypeError, AttributeError, IndexError, RecursionError, OverflowError) as error:
        raise ValueError('invalid QA snapshot structure') from error


def validate_snapshot(data, metadata):
    q.validate_metadata(metadata)
    require(type(data['schemaVersion']) is int and data['schemaVersion'] == 1, 'unknown QA schema')
    require(all(data[k] is True for k in ('valid', 'foreground', 'debuggable')), 'invalid/background QA')
    require(data['applicationId'] == metadata['package'] and data['versionName'] == metadata['version_name']
            and type(data['versionCode']) is int and data['versionCode'] == metadata['version_code'], 'QA candidate version mismatch')
    require(integer(data['pid'], 1), 'invalid QA PID')
    require(data['clock'] == 'java.nanoTime', 'unknown QA clock')
    capture, export = data['capturedMonotonicNs'], data['exportedMonotonicNs']
    require(integer(capture, 1) and integer(export, 1) and capture <= export, 'QA clock regression')
    require(type(data['maxCaptureAgeNs']) is int and data['maxCaptureAgeNs'] == 2_000_000_000
            and integer(data['captureAgeNs']) and data['captureAgeNs'] == export-capture
            and export-capture <= 2_000_000_000, 'stale/incoherent QA age')
    require(integer(data['sessionGeneration'], 1) and integer(data['lifecycleGeneration'])
            and integer(data['policyGeneration']), 'invalid QA generations')
    watches = data['watches']
    require(isinstance(watches, list) and len(watches) == 4, 'four QA watches required')
    for i, w in enumerate(watches):
        require(type(w['slot']) is int and w['slot'] == i and all(w[k] is True for k in
                ('valid', 'readerActive', 'nativeFresh', 'freshAtExport')) and w['pending'] is False
                and w['hasError'] is False and w['state'] == 'receiving', 'non-live QA watch')
        require(integer(w['requestVersion']) and type(w['appliedVersion']) is int
                and w['requestVersion'] == w['appliedVersion'] and integer(w['readerGeneration'], 1)
                and integer(w['count'], 1) and integer(w['matched'], 1), 'invalid QA watch identity/counters')
        for key in ('receivedHz', 'sampleAgeAtCaptureSeconds'):
            require(type(w[key]) in (int, float) and math.isfinite(w[key]) and w[key] >= 0, 'invalid QA rate/age')
        require(w['sampleAgeAtCaptureSeconds'] + (export-capture)/1e9 <= 3,
                'stale QA source sample')
        require(all(type(w[k]) is bool for k in ('requestedReliable', 'requestedTransientLocal')), 'invalid QA QoS')
    validate_graph(data['plotDisplay'], export, watches, data['plotIntake'], data['sessionGeneration'])
    d = data['testHooks']
    require(type(d['schemaVersion']) is int and d['schemaVersion'] == 2 and d['valid'] is True,
            'diagnostic capability unknown')
    enabled = metadata['schema_version'] == 2
    require(d['capability'] is enabled and d['contract'] == (metadata['diagnostic_policy'] if enabled else None),
            'binary/runtime diagnostic policy mismatch')
    require(all(d[k] is False for k in ('runnerActive', 'armed', 'ticketPending', 'operationInFlight'))
            and d['leasePhase'] == 'INACTIVE' and type(d['leaseEpoch']) is int and d['leaseEpoch'] == 0,
            'diagnostic instrumentation/fault work active or unknown')
    import uuid
    uuid.UUID(d['processInstance'])
    require(integer(d['startTimeTicks'], 1) and type(d['pid']) is int and d['pid'] == data['pid'], 'diagnostic process mismatch')
    for key in ('revision', 'nativeRevision', 'captureSequence'):
        require(integer(d[key]), 'diagnostic integer invalid')
    for dk, qk in (('capturedMonotonicNs','capturedMonotonicNs'), ('exportedMonotonicNs','exportedMonotonicNs'),
                   ('sessionGeneration','sessionGeneration'), ('lifecycle','lifecycleGeneration'), ('policy','policyGeneration')):
        require(type(d[dk]) is int and d[dk] == data[qk], 'mixed QA/diagnostic capture')
    require(isinstance(d['requests'], list) and len(d['requests']) == 4, 'four diagnostic requests required')
    for i, (r, w) in enumerate(zip(d['requests'], watches)):
        require(type(r['slot']) is int and r['slot'] == i and type(r['version']) is int
                and type(r['appliedVersion']) is int and r['version'] == r['appliedVersion'] == w['requestVersion']
                and r['pending'] is False, 'mixed diagnostic requests')


def continuity(previous, current, advancing=True):
    """Reject replay and replacement, never stitch counters across identities."""
    a, b = previous['testHooks'], current['testHooks']
    for key in ('pid', 'startTimeTicks', 'processInstance', 'sessionGeneration', 'lifecycle', 'policy'):
        require(a[key] == b[key], 'QA process/session/lifecycle changed')
    for current_value, previous_value in ((current['capturedMonotonicNs'],previous['capturedMonotonicNs']),
                                           (current['exportedMonotonicNs'],previous['exportedMonotonicNs']),
                                           (b['captureSequence'],a['captureSequence'])):
        require(current_value > previous_value if advancing else current_value >= previous_value,
                'replayed QA clock/worker sequence')
    for old, new in zip(previous['watches'], current['watches']):
        for key in ('requestVersion', 'appliedVersion', 'readerGeneration', 'requestedReliable', 'requestedTransientLocal'):
            require(old[key] == new[key], 'QA watch identity/QoS changed')
        require(new['count'] >= old['count'], 'QA count reset')
    keys = ('seriesId', 'fieldPath', 'slot', 'sessionGeneration', 'readerGeneration')
    require([tuple(s[k] for k in keys) for s in previous['plotIntake']['series']] ==
            [tuple(s[k] for k in keys) for s in current['plotIntake']['series']], 'QA plot selection changed')
