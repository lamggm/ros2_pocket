#!/usr/bin/env python3
"""Parse the actual no-DDS native snapshots; never accept substring matches."""
import json
import subprocess
import sys


def assert_inactive_snapshot(snapshot):
    assert snapshot['running'] is False, snapshot
    watches = snapshot['watches']
    assert len(watches) == 4, watches
    slots = [watch['slot'] for watch in watches]
    assert all(type(slot) is int for slot in slots), slots
    assert sorted(slots) == list(range(4)), slots
    expected = {
        'generation': 0, 'subscription': '', 'type': '',
        'readerActive': False, 'subscriptionState': 'inactive', 'fresh': False,
        'last': '', 'count': 0, 'hz': 0, 'age': -1, 'matched': 0,
        'qos': 'best_effort / volatile', 'error': '',
    }
    for watch in watches:
        for field, value in expected.items():
            assert watch[field] == value, (watch['slot'], field, watch[field], value)
        assert type(watch['readerActive']) is bool and type(watch['fresh']) is bool
    # Legacy telemetry remains a projection of slot zero, not an inactive sibling.
    primary = next(watch for watch in watches if watch['slot'] == 0)
    for field in ('subscription', 'readerActive', 'subscriptionState',
                  'fresh', 'last', 'count', 'hz', 'age', 'matched', 'qos', 'error'):
        assert snapshot[field] == primary[field], (field, snapshot[field], primary[field])


def main():
    result = subprocess.run([sys.argv[1]], check=True, capture_output=True,
                            text=True, timeout=5)
    snapshots = [json.loads(line) for line in result.stdout.splitlines()]
    assert len(snapshots) == 2, 'expected initial and idempotently stopped snapshots'
    for snapshot in snapshots:
        assert_inactive_snapshot(snapshot)
    print('PASS parsed initial/stopped snapshots: exactly four unique inactive slots 0..3')


if __name__ == '__main__':
    main()
