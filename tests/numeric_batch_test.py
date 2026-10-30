"""Real native Session batch contract; in-process DDS control, not RMW/Android acceptance."""
import json
import subprocess
import sys


def verify(rows):
    assert len(rows) == 5
    configured, burst, repeat, removed, stopped = rows
    assert configured['numericBatch']['epoch'] == 7
    for snapshot in rows:
        batch = snapshot['numericBatch']
        assert batch['schema'] == 1 and len(batch['slots']) == 4
        assert len(',"numericBatch":' + json.dumps(batch, separators=(',', ':'))) <= 16384
        assert sum(len(slot['paths']) for slot in batch['slots']) <= 4
        for i, slot in enumerate(batch['slots']):
            assert slot['slot'] == i and slot['readerGeneration'] == snapshot['watches'][i]['generation']
            assert len(slot['samples']) <= 32
            assert all(len(point) == 3 + len(slot['paths']) for point in slot['samples'])
            assert slot['droppedLocal'] >= 0
    slot = burst['numericBatch']['slots'][0]
    points = slot['samples']
    assert burst['count'] == 40 and burst['numericBatch']['epoch'] == 9
    assert slot['paths'] == ['data'] and len(points) == 32 and slot['droppedLocal'] == 8
    assert [point[0] for point in points] == list(range(9, 41))
    assert [point[2] for point in points] == list(range(1008, 1040))
    assert all(type(point[1]) is int and point[1] > 0 for point in points)
    assert [point[1] for point in points] == sorted(point[1] for point in points)
    for value, point in zip(range(8, 40), points):
        assert point[3] is None if value in (36, 38) else point[3] == value
    assert repeat['count'] == 40 and repeat['numericBatch']['slots'][0]['samples'] == []
    assert repeat['numericBatch']['slots'][0]['droppedLocal'] == 8
    assert removed['numericBatch']['slots'][0]['samples'] == []
    assert removed['numericBatch']['slots'][0]['droppedLocal'] == 0
    assert removed['watches'][0]['generation'] > burst['watches'][0]['generation']
    assert stopped['numericBatch']['epoch'] == 0
    assert all(not slot['paths'] and not slot['samples'] for slot in stopped['numericBatch']['slots'])


if __name__ == '__main__':
    process = subprocess.run([sys.argv[1]], capture_output=True, text=True, timeout=12)
    if process.returncode:
        sys.stderr.write(process.stderr)
        raise SystemExit(process.returncode)
    rows = [json.loads(line) for line in process.stdout.splitlines()]
    verify(rows)
    print('NATIVE_NUMERIC_BATCH_PASS taken=40 kept=32 known_local_dropped=8; clocks/gaps/identity/drain/reset verified')
