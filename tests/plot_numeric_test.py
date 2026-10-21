"""Real typed DDS samples. Run only inside an isolated network namespace."""
import json
import subprocess
import sys
p = subprocess.run([sys.argv[1]], text=True, capture_output=True, timeout=15)
assert p.returncode == 0, p.stderr
rows = [json.loads(s) for s in p.stdout.splitlines() if s.startswith('{')]
assert len(rows) == 12, rows
assert rows[0].get('numericFields') == [], 'inactive snapshot needs explicit empty numeric fields'
for a, b in zip(rows[1:9:2], rows[2:9:2]):
    assert a['numericFields'] == b['numericFields']
    assert a['receivedMonotonicNs'] == b['receivedMonotonicNs'] > 0, 'poll must not synthesize receive time'
    assert a['sourceTimestampNs'] == b['sourceTimestampNs'] == 1234567890
    assert a['count'] == b['count']
    assert len(a['numericFields']) == 1
    assert a['numericFields'][0]['path'] == 'data'
    assert a['numericFields'][0]['unit'] is None
assert rows[1]['numericFields'][0]['value'] == 42.123456789012345
assert rows[3]['numericFields'][0]['value'] is None and rows[3]['numericFields'][0]['state'] == 'nan'
assert rows[5]['numericFields'][0]['value'] is None and rows[5]['numericFields'][0]['state'] == 'positive_infinity'
assert rows[7]['numericFields'][0]['value'] is None and rows[7]['numericFields'][0]['state'] == 'negative_infinity'
assert rows[9]['numericFields'][0]['value'] == 9007199254740993, 'typed integer must retain exact JSON value'
for row in rows[-2:]:
    assert row['numericFields'] == [] and row['receivedMonotonicNs'] is None and row['sourceTimestampNs'] is None
assert rows[-2]['readerGeneration'] > rows[1]['readerGeneration']
print('PASS typed precision, nonfinite gaps, stable receive/source times, error and stop clearing')
