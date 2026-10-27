"""Synthetic QA controls, never Android/physical evidence."""
import copy
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import test_qualification as fixtures
from test_qualification import tool, diagnostic_metadata, metadata


def snapshot(diagnostic=True):
    data = fixtures.QualificationTests().diagnostic_soak(tool())
    qa = copy.deepcopy(data['samples'][0]['diagnostic_proof']['qa'])
    qa['testHooks'] = copy.deepcopy(data['samples'][0]['diagnostic_proof']['diagnostic'])
    qa.update(clock='java.nanoTime', maxCaptureAgeNs=2_000_000_000,
              captureAgeNs=qa['exportedMonotonicNs']-qa['capturedMonotonicNs'],
              versionName='0.2-unit-control', versionCode=2)
    for w in qa['watches']:
        w.update(receivedHz=2.0, sampleAgeAtCaptureSeconds=0.1,
                 requestedReliable=False, requestedTransientLocal=False)
    if not diagnostic:
        qa['testHooks'].update(capability=False, contract=None)
    return qa


def frame(qa):
    return ('Android scoped dump header\nROS_POCKET_QA_V1_BEGIN\n' + json.dumps(qa) +
            '\nROS_POCKET_QA_V1_END\n').encode()


class SnapshotTests(unittest.TestCase):
    def test_real_parser_accepts_framed_current_contract_without_mutation(self):
        import device_snapshot as s
        for diagnostic, meta in ((True, diagnostic_metadata()), (False, metadata())):
            qa = snapshot(diagnostic)
            self.assertEqual(s.parse_snapshot(frame(qa), meta), qa)

    def test_framing_types_clocks_diagnostic_and_graph_negatives(self):
        import device_snapshot as s
        base = snapshot()
        for raw in (b'', frame(base)[:-24], frame(base)*2,
                    frame(base).replace(b'"schemaVersion": 1', b'"schemaVersion": 1, "schemaVersion": 1', 1),
                    frame(base).replace(b'2.0', b'1e999', 1), b'x'*32769):
            with self.subTest(raw=raw[:32]), self.assertRaises(ValueError):
                s.parse_snapshot(raw, diagnostic_metadata())
        cases = [('clock','host.monotonic'), ('captureAgeNs', True), ('versionCode', True),
                 ('pid', True), ('capturedMonotonicNs', -1), ('foreground', False)]
        for key, value in cases:
            qa = copy.deepcopy(base); qa[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                s.parse_snapshot(frame(qa), diagnostic_metadata())
        for key, value in [('armed',True), ('capability',False), ('runnerActive',None),
                           ('operationInFlight',True), ('leasePhase','CLOSING'), ('captureSequence',True)]:
            qa = copy.deepcopy(base); qa['testHooks'][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                s.parse_snapshot(frame(qa), diagnostic_metadata())
        for group, key, value in [('plotDisplay','drawPending',True), ('plotDisplay','paused',True),
                                   ('plotDisplay','chartFullyVisible',False), ('plotIntake','capacityPerSeries',601)]:
            qa = copy.deepcopy(base); qa[group][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                s.parse_snapshot(frame(qa), diagnostic_metadata())
        for key, value in [('count',True), ('matched',0), ('pending',True), ('readerGeneration',0), ('state','stale')]:
            qa = copy.deepcopy(base); qa['watches'][2][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                s.parse_snapshot(frame(qa), diagnostic_metadata())

    def test_continuity_rejects_reader_reset_replay_and_changed_plot_selection(self):
        import device_snapshot as s
        old = snapshot(); new = copy.deepcopy(old)
        for key in ('capturedMonotonicNs','exportedMonotonicNs'):
            new[key] += 15_000_000_000; new['testHooks'][key] = new[key]
        new['testHooks']['captureSequence'] += 1
        s.continuity(old, new)
        for mutate in (lambda x: x['testHooks'].update(pid=999),
                       lambda x: x['watches'][0].update(readerGeneration=99),
                       lambda x: x['watches'][0].update(count=0),
                       lambda x: x['testHooks'].update(captureSequence=old['testHooks']['captureSequence']),
                       lambda x: x['plotIntake']['series'][0].update(fieldPath='other')):
            bad = copy.deepcopy(new); mutate(bad)
            with self.assertRaises(ValueError): s.continuity(old, bad)


if __name__ == '__main__':
    unittest.main()
