"""Exact host-only reader failure verifier; not Android/device acceptance."""
import json
import subprocess
import sys


def verify_inactive(snapshot, stage):
    primary = snapshot['watches'][0]
    assert primary['slot'] == 0
    expected = dict(readerActive=False, subscription='', last='', message=None,
                    count=0, matched=0, age=-1, hz=0, fresh=False,
                    subscriptionState='error', numericFields=[],
                    receivedMonotonicNs=None, sourceTimestampNs=None)
    prefix = 'Falha ao assinar:' if stage == 'reader' else 'Falha ao criar topico:'
    for target in (snapshot, primary):
        for key, value in expected.items():
            assert key in target and type(target[key]) is type(value) and target[key] == value, (key, target)
        assert target['error'].startswith(prefix) and 'Bad Parameter' in target['error']
    assert primary['error'] == snapshot['error']


def main(binary):
    result = subprocess.run([binary], capture_output=True, text=True, timeout=14)
    print(result.stderr, end='', file=sys.stderr)
    assert result.returncode == 0, 'native fixture failed (stderr above)'
    events = [json.loads(line) for line in result.stdout.splitlines()]
    assert {event['stage'] for event in events} == {'topic', 'reader'}
    for stage in ('topic', 'reader'):
        failures = [e for e in events if e['stage'] == stage and e.get('kind') == 'failure']
        recoveries = [e for e in events if e['stage'] == stage and e.get('kind') == 'recovery']
        assert len(failures) >= 3, f'{stage}: multiple genuine polls required'
        assert len(recoveries) == 2, f'{stage}: new and advancing recovery data required'
        first = failures[0]
        before, receipt = first['before'], first['receipt']
        print('actual API receipt:', json.dumps(receipt, sort_keys=True))
        assert receipt['session'] == before['sessionGeneration']
        assert receipt['slot'] == receipt['targetSlot'] == 0
        assert receipt['attemptedGeneration'] == before['watches'][0]['generation'] + 1
        assert receipt['request'] == first['request']
        assert receipt['stage'] == stage
        assert receipt['topicApi'] == 'dds_create_topic'
        if stage == 'topic':
            assert receipt['topicReturn'] == -3 and receipt['readerReturn'] is None
            assert receipt['readerApi'] is None and receipt['readerParent'] is None
            assert receipt['topicArgumentOverridden'] is True
            assert receipt['readerArgumentOverridden'] is False
            assert receipt['topicName'] == 'rt/pocket_test/invalid topic'
        else:
            assert receipt['topicReturn'] > 0 and receipt['readerReturn'] == -3
            assert receipt['readerApi'] == 'dds_create_reader' and receipt['readerParent'] == 0
            assert receipt['topicArgumentOverridden'] is False
            assert receipt['readerArgumentOverridden'] is True
            assert receipt['topicName'] == 'rt/pocket_hook/reader_fail'
        for event in failures:
            snapshot = event['snapshot']
            verify_inactive(snapshot, stage)
            assert event['receipt'] == receipt
            assert snapshot['sessionGeneration'] == receipt['session']
            assert snapshot['watches'][0]['generation'] == receipt['attemptedGeneration']
            assert snapshot['readerGeneration'] == before['readerGeneration'] + 1
        previous_count, previous_received = 0, 0
        previous_value = before['last']
        for event in recoveries:
            snapshot = event['snapshot']
            primary = snapshot['watches'][0]
            assert snapshot['sessionGeneration'] == receipt['session']
            assert primary['generation'] == receipt['attemptedGeneration'] + 1
            assert snapshot['readerGeneration'] == before['readerGeneration'] + 2
            for target in (snapshot, primary):
                assert target['readerActive'] is True and target['fresh'] is True
                assert target['subscription'] == '/pocket_hook/live' and target['error'] == ''
                assert target['count'] > previous_count
                assert target['receivedMonotonicNs'] > previous_received
                assert target['last'] == event['sequence'] and target['last'] != previous_value
                assert target['message'] is not None
            assert event['receipt'] == '', 'receipt from an old request survived recovery'
            print('actual recovery:', stage, primary['generation'], primary['count'], primary['last'])
            previous_count = primary['count']
            previous_received = primary['receivedMonotonicNs']
            previous_value = primary['last']
    print(f'Verified {len(events)} actual native failure/recovery snapshots, both API receipts and fresh advancement')


if __name__ == '__main__':
    main(sys.argv[1])
