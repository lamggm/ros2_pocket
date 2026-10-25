"""SYNTHETIC validator controls, never physical evidence or readiness."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
from test_qualification import tool, metadata, diagnostic_metadata
import test_qualification as tq


class QualificationRecordingTests(unittest.TestCase):
    def test_every_passed_gate_reinspects_actual_candidate_without_mutating_report(self):
        q = tool()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for kind in ('missing', 'changed', 'downgraded-on'):
                candidate = metadata() if kind != 'downgraded-on' else diagnostic_metadata()
                if kind == 'downgraded-on':
                    candidate.update(schema_version=1, test_hooks=False)
                    candidate.pop('diagnostic_policy'); candidate.pop('native_libraries')
                elif kind == 'missing':
                    candidate['apk'] = str(root / 'absent.apk')
                else:
                    changed = root / 'changed.apk'
                    changed.write_bytes(Path(candidate['apk']).read_bytes() + b'CHANGED')
                    candidate['apk'] = str(changed)
                q.validate_metadata(candidate)
                report = q.new_report(candidate)
                before = copy.deepcopy(report)
                (root / 'installed.json').write_text(json.dumps(candidate))
                (root / 'control.txt').write_text('SYNTHETIC ONLY, not hardware acceptance')
                soak = tq.QualificationTests().synthetic_soak(q); soak['snapshot'] = candidate
                (root / 'soak.json').write_text(json.dumps(soak))
                for gate in q.GATES:
                    evidence = {'installed-apk':'installed.json', 'soak':'soak.json'}.get(gate, 'control.txt')
                    with self.subTest(kind=kind, gate=gate):
                        with self.assertRaisesRegex(ValueError, 'capability'):
                            q.record_result(report, candidate, gate, 'passed', [evidence], root,
                                            operator='SYNTHETIC', note='Not physical evidence')
                        self.assertEqual(before, report)

    def test_installed_structured_api_requires_actual_bytes_with_explicit_path_precedence(self):
        q = tool()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for candidate in (metadata(), diagnostic_metadata()):
                actual = Path(candidate['apk'])
                candidate['apk'] = str(root / 'absent.apk')
                (root / 'installed.json').write_text(json.dumps(candidate))
                with self.subTest(schema=candidate['schema_version']):
                    with self.assertRaisesRegex(ValueError, 'capability'):
                        q.validate_structured_evidence('installed-apk', ['installed.json'], root, candidate)
                    q.validate_structured_evidence('installed-apk', ['installed.json'], root, candidate, actual)
                    report = q.record_result(q.new_report(candidate), candidate, 'host-tests', 'passed',
                                             ['installed.json'], root, apk=actual)
                    self.assertEqual('passed', report['gates'][0]['status'])
                    with self.assertRaisesRegex(ValueError, 'capability'):
                        q.record_result(report, candidate, 'host-tests', 'passed', ['installed.json'], root,
                                        apk=root / 'absent-explicit.apk')

    def test_public_record_cli_rejects_missing_changed_and_explicit_missing_bytes(self):
        q = tool()
        scratch = tq.ROOT / 'build/qualification/unit'; scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory); candidate, actual = tq.cli_candidate(root)
            changed = root/'changed.apk'; changed.write_bytes(actual.read_bytes()+b'CHANGED SYNTHETIC')
            (root/'control.txt').write_text('SYNTHETIC ONLY, not physical evidence')
            for kind in ('missing-local','explicit-missing','explicit-changed'):
                current = copy.deepcopy(candidate)
                if kind == 'missing-local':current['apk'] = str(root/'absent.apk')
                explicit = [] if kind == 'missing-local' else ['--apk', root/'absent.apk' if kind == 'explicit-missing' else changed]
                meta = root/'metadata.json'; meta.write_text(json.dumps(current))
                report = root/'report.json'; report.write_text(json.dumps(q.new_report(current)))
                original = report.read_bytes()
                (root/'installed.json').write_text(json.dumps(current))
                soak = tq.QualificationTests().synthetic_soak(q); soak['snapshot'] = current
                (root/'soak.json').write_text(json.dumps(soak))
                for gate in q.GATES:
                    evidence = {'installed-apk':'installed.json','soak':'soak.json'}.get(gate,'control.txt')
                    result = tq.run_cli('record','--metadata',meta,*explicit,'--repo',tq.ROOT,
                                        '--report',report,'--evidence-root',root,'--gate',gate,
                                        '--status','passed','--evidence',evidence,
                                        '--operator','SYNTHETIC','--note','Not physical evidence')
                    with self.subTest(kind=kind,gate=gate):
                        self.assertEqual(2,result.returncode,result.stderr)
                        self.assertEqual('',result.stdout)
                        self.assertIn('capability',result.stderr)
                        self.assertNotIn('source mismatch',result.stderr)
                        self.assertNotIn('Traceback',result.stderr)
                        self.assertEqual(original,report.read_bytes())

    def test_pending_plan_and_negative_records_do_not_claim_binary_evidence(self):
        q = tool(); candidate = metadata(); candidate['apk'] = '/SYNTHETIC/absent.apk'
        report = q.new_report(candidate)
        self.assertTrue(all(g['status'] == 'pending' and not g['evidence'] for g in report['gates']))
        with tempfile.TemporaryDirectory() as directory:
            for status in ('pending', 'blocked', 'failed'):
                recorded = q.record_result(report, candidate, 'host-tests', status, [], Path(directory))
                self.assertEqual(status, recorded['gates'][0]['status'])


if __name__ == '__main__':
    unittest.main()
