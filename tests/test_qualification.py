"""Tool-unit controls only: synthetic data is NOT physical acceptance."""
import copy
import importlib.util
import json
import hashlib
import subprocess
import sys
import tempfile
from pathlib import Path
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]


def tool():
    path = ROOT / 'tools/qualification.py'
    if not path.exists():
        raise AssertionError('qualification tool must exist')
    spec = importlib.util.spec_from_file_location('qualification', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def metadata():
    return dict(schema_version=1, apk='synthetic.apk', size_bytes=123,
                sha256='a' * 64, package='com.lamggm.rospocket.dev',
                version_code=2, version_name='0.2-unit-control', debuggable=True,
                abis=['arm64-v8a'], signing=dict(certificate_sha256='b' * 64,
                certificate_dn='CN=Android Debug', **{'class': 'debug'}),
                source=dict(head_sha='c' * 40, dirty=False, snapshot_sha256='d' * 64))


def cli_candidate(root):
    """Current-source binding and actual bytes, but explicitly not a device APK."""
    candidate = metadata()
    candidate['source'] = json.loads(subprocess.check_output(
        [sys.executable, str(ROOT / 'tools/artifact_metadata.py'), '--source', '--repo', str(ROOT)],
        text=True))
    apk = root / 'synthetic.apk'
    apk.write_bytes(b'TOOL UNIT TEST ONLY; not a real installable APK')
    candidate.update(sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), size_bytes=apk.stat().st_size)
    return candidate, apk


def run_cli(*args):
    return subprocess.run([sys.executable, str(ROOT / 'tools/qualification.py'), *map(str, args)],
                          capture_output=True, text=True, timeout=10)


class QualificationTests(unittest.TestCase):
    def test_cli_init_depth_boundary_is_reloadable_or_preserves_destination(self):
        q = tool()
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            candidate, apk = cli_candidate(root)
            meta = root / 'metadata.json'
            report = root / 'report.json'
            for lists in (62, 63):
                nested = 0
                for _ in range(lists):
                    nested = [nested]
                candidate['unknown'] = nested
                meta.write_text(json.dumps(candidate), encoding='utf-8')
                self.assertEqual(q.load_json(meta), candidate, 'Metadata itself is within 64 containers')
                for existing in (False, True):
                    with self.subTest(lists=lists, existing=existing):
                        report.unlink(missing_ok=True)
                        before = b'previous report must remain byte-identical\n'
                        if existing:
                            report.write_bytes(before)
                        result = run_cli('init', '--metadata', meta, '--apk', apk,
                                         '--repo', ROOT, '--output', report)
                        self.assertNotIn('Traceback', result.stderr)
                        if lists == 63:
                            self.assertEqual(result.returncode, 2, result.stderr)
                            self.assertIn('qualification error: JSON container depth exceeds 64', result.stderr)
                            self.assertEqual(result.stdout, '')
                            if existing:
                                self.assertEqual(report.read_bytes(), before)
                            else:
                                self.assertFalse(report.exists())
                        else:
                            self.assertEqual(result.returncode, 0, result.stderr)
                            self.assertEqual(q.load_json(report)['snapshot'], candidate)
                            recorded = run_cli('record', '--metadata', meta, '--report', report,
                                               '--evidence-root', root, '--repo', ROOT,
                                               '--gate', 'host-tests', '--status', 'blocked')
                            self.assertEqual(recorded.returncode, 0, recorded.stderr)
                            self.assertEqual(recorded.stderr, '')
                            stored = q.load_json(report)
                            self.assertEqual(stored['snapshot'], candidate, 'Never strip unknown fields')
                            self.assertEqual(stored['gates'][0]['status'], 'blocked')
                        self.assertFalse(list(root.glob('*.part')), 'No failed-write temporary files')

    def test_cli_output_byte_limit_rejects_wrapping_and_escape_expansion_without_replacing(self):
        q = tool()
        limit = 4 * 1024 * 1024
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            candidate, apk = cli_candidate(root)
            candidate['unknown'] = ''
            meta = root / 'metadata.json'
            report = root / 'report.json'
            empty_bytes = len(json.dumps(candidate, ensure_ascii=False, separators=(',', ':')).encode('utf-8'))
            for character in ('x', 'é'):
                candidate['unknown'] = character * ((limit - empty_bytes) // len(character.encode('utf-8')))
                meta.write_text(json.dumps(candidate, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
                self.assertLessEqual(meta.stat().st_size, limit)
                self.assertGreaterEqual(meta.stat().st_size, limit - 1)
                self.assertEqual(q.load_json(meta), candidate, 'Input transport admits this metadata')
                encoded_report = (json.dumps(q.new_report(candidate), indent=2, allow_nan=False) + '\n').encode('utf-8')
                self.assertGreater(len(encoded_report), limit, 'Generated output would not reload')
                for existing in (False, True):
                    with self.subTest(character=character, existing=existing):
                        report.unlink(missing_ok=True)
                        before = b'previous report must remain byte-identical\n'
                        if existing:
                            report.write_bytes(before)
                        result = run_cli('init', '--metadata', meta, '--apk', apk,
                                         '--repo', ROOT, '--output', report)
                        self.assertEqual(result.returncode, 2, result.stderr)
                        self.assertIn('qualification error: JSON exceeds bounded 4 MiB', result.stderr)
                        self.assertEqual(result.stdout, '')
                        self.assertNotIn('Traceback', result.stderr)
                        if existing:
                            self.assertEqual(report.read_bytes(), before)
                        else:
                            self.assertFalse(report.exists())
                        self.assertFalse(list(root.glob('*.part')))

    def test_cli_exact_output_byte_limit_reloads_and_record_growth_is_rejected_atomically(self):
        q = tool()
        limit = 4 * 1024 * 1024
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            candidate, apk = cli_candidate(root)
            candidate['unknown'] = ''
            overhead = len((json.dumps(q.new_report(candidate), indent=2, allow_nan=False) + '\n').encode('utf-8'))
            candidate['unknown'] = 'x' * (limit - overhead)
            meta = root / 'metadata.json'
            meta.write_text(json.dumps(candidate, separators=(',', ':')), encoding='utf-8')
            report = root / 'report.json'
            result = run_cli('init', '--metadata', meta, '--apk', apk, '--repo', ROOT, '--output', report)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stderr, '')
            self.assertEqual(report.stat().st_size, limit, 'Include the trailing newline in the byte bound')
            self.assertEqual(q.load_json(report)['snapshot'], candidate)
            before = report.read_bytes()
            recorded = run_cli('record', '--metadata', meta, '--report', report,
                               '--evidence-root', root, '--repo', ROOT,
                               '--gate', 'host-tests', '--status', 'blocked')
            self.assertEqual(recorded.returncode, 2, recorded.stderr)
            self.assertIn('qualification error: JSON exceeds bounded 4 MiB', recorded.stderr)
            self.assertEqual(recorded.stdout, '')
            self.assertNotIn('Traceback', recorded.stderr)
            self.assertEqual(report.read_bytes(), before)
            self.assertFalse(list(root.glob('*.part')))

    def test_initial_matrix_is_exact_pending_and_bound_to_metadata(self):
        q = tool()
        candidate = metadata()
        report = q.new_report(candidate)
        self.assertEqual(report['snapshot'], candidate)
        self.assertEqual([g['id'] for g in report['gates']], list(q.GATES))
        self.assertEqual(len(q.GATES), len(set(q.GATES)))
        self.assertEqual(len(q.GATES), 21)
        self.assertEqual(set(q.GATES), {
            'host-tests', 'host-lint', 'host-build', 'secret-scan', 'increment-review',
            'fastdds', 'cyclonedds', 'wrong-domain', 'lifecycle', 'reader-failure',
            'qos-removal', 'rosout', 'structured', 'multiwatch', 'graphs', 'recovery-plan',
            'wifi-loss-return', 'address-change', 'soak', 'installed-apk', 'operator-acceptance',
        })
        self.assertTrue(all(g['status'] == 'pending' and not g['evidence']
                            for g in report['gates']))
        candidate['source']['dirty'] = True
        self.assertFalse(report['snapshot']['source']['dirty'])

    def test_pending_stale_or_dirty_snapshot_never_ready(self):
        q = tool()
        candidate = metadata()
        report = q.new_report(candidate)
        with tempfile.TemporaryDirectory() as directory:
            result = q.evaluate_report(report, candidate, candidate['source'], Path(directory))
            self.assertFalse(result['ready'])
            self.assertEqual(len(result['blockers']), len(q.GATES))
            for key, value in [('sha256', 'e' * 64), ('version_code', 99),
                               ('signing', dict(candidate['signing'], certificate_sha256='f' * 64)),
                               ('source', dict(candidate['source'], snapshot_sha256='f' * 64))]:
                changed = copy.deepcopy(candidate)
                changed[key] = value
                with self.subTest(key=key):
                    result = q.evaluate_report(report, changed, changed['source'], Path(directory))
                    self.assertIn('snapshot mismatch', result['blockers'])
            current = dict(candidate['source'], head_sha='f' * 40)
            result = q.evaluate_report(report, candidate, current, Path(directory))
            self.assertIn('current source mismatch', result['blockers'])
            dirty = copy.deepcopy(candidate)
            dirty['source']['dirty'] = True
            result = q.evaluate_report(q.new_report(dirty), dirty, dirty['source'], Path(directory))
            self.assertIn('dirty source', result['blockers'])

    def test_rejects_unknown_duplicate_missing_gates_and_invalid_metadata(self):
        q = tool()
        report = q.new_report(metadata())
        for edit in ['unknown', 'duplicate', 'missing', 'status']:
            broken = copy.deepcopy(report)
            if edit == 'unknown':
                broken['gates'][0]['id'] = 'not-a-gate'
            elif edit == 'duplicate':
                broken['gates'].append(broken['gates'][0])
            elif edit == 'missing':
                broken['gates'].pop()
            else:
                broken['gates'][0]['status'] = 'skipped'
            with self.subTest(edit=edit), self.assertRaises(ValueError):
                q.evaluate_report(broken, metadata(), metadata()['source'], ROOT)
        for key in ['source', 'signing', 'sha256', 'package', 'version_code', 'abis']:
            broken = metadata()
            del broken[key]
            with self.subTest(key=key), self.assertRaises(ValueError):
                q.new_report(broken)
        for field, value in [('package', 'unrelated.app'), ('sha256', 'not-a-hash'),
                             ('debuggable', False)]:
            broken = metadata()
            broken[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                q.new_report(broken)
        broken = metadata()
        broken['source']['dirty'] = 'false'
        with self.assertRaises(ValueError):
            q.new_report(broken)

    def test_explicit_record_binds_hashes_and_manual_signoff(self):
        q = tool()
        candidate = metadata()
        report = q.new_report(candidate)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'host.txt').write_text('synthetic host control, not acceptance')
            recorded = q.record_result(report, candidate, 'host-tests', 'passed',
                                       ['host.txt'], root)
            self.assertEqual(report['gates'][0]['status'], 'pending', 'No in-place mutation')
            self.assertEqual(recorded['gates'][0]['status'], 'passed')
            result = q.evaluate_report(recorded, candidate, candidate['source'], root)
            self.assertFalse(any(b.startswith('host-tests:') for b in result['blockers']))
            for status in ['blocked', 'failed', 'pending']:
                changed = q.record_result(recorded, candidate, 'host-tests', status, [], root)
                self.assertIn('host-tests: ' + status,
                              q.evaluate_report(changed, candidate, candidate['source'], root)['blockers'])
            with self.assertRaises(ValueError):
                q.record_result(report, candidate, 'operator-acceptance', 'passed', ['host.txt'], root)
            accepted = q.record_result(report, candidate, 'operator-acceptance', 'passed',
                                       ['host.txt'], root, operator='UNIT TEST ONLY',
                                       note='Synthetic report validation, not tablet acceptance')
            self.assertFalse(q.evaluate_report(accepted, candidate, candidate['source'], root)['ready'])
            for invalid in ['unknown', '']:
                with self.assertRaises(ValueError):
                    q.record_result(report, candidate, invalid, 'passed', ['host.txt'], root)
            stale = dict(candidate, sha256='e' * 64)
            with self.assertRaises(ValueError):
                q.record_result(report, stale, 'host-tests', 'passed', ['host.txt'], root)
            (root / 'host.txt').write_text('changed after recording')
            self.assertTrue(any(b.startswith('host-tests:') for b in
                                q.evaluate_report(recorded, candidate, candidate['source'], root)['blockers']))
            (root / 'host.txt').unlink()
            self.assertFalse(q.evaluate_report(recorded, candidate, candidate['source'], root)['ready'])

    def test_record_rejects_resolved_evidence_aliases_without_mutation(self):
        q = tool()
        candidate = metadata()
        report = q.new_report(candidate)
        before = copy.deepcopy(report)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'file.json').write_text('{"unit": "synthetic evidence only"}')
            (root / 'alias.json').symlink_to('file.json')
            for alias in ('./file.json', 'alias.json'):
                self.assertEqual(q.evidence_file(root, alias), (root / 'file.json').resolve())
                for status in ('passed', 'pending', 'failed', 'blocked'):
                    with self.subTest(alias=alias, status=status):
                        with self.assertRaisesRegex(ValueError, 'duplicate evidence'):
                            q.record_result(report, candidate, 'host-tests', status,
                                            ['file.json', alias], root)
                        self.assertEqual(report, before, 'Rejected records must not mutate input')

    def test_cli_record_evidence_alias_rejection_preserves_report_atomically(self):
        q = tool()
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            candidate, _ = cli_candidate(root)
            meta = root / 'metadata.json'
            meta.write_text(json.dumps(candidate))
            report = root / 'report.json'
            before = (json.dumps(q.new_report(candidate), indent=2) + '\n').encode()
            (root / 'file.json').write_text('{"unit": "synthetic evidence only"}')
            (root / 'alias.json').symlink_to('file.json')
            for alias in ('./file.json', 'alias.json'):
                with self.subTest(alias=alias):
                    report.write_bytes(before)
                    result = run_cli('record', '--metadata', meta, '--report', report,
                                     '--evidence-root', root, '--repo', ROOT,
                                     '--gate', 'host-tests', '--status', 'passed',
                                     '--evidence', 'file.json', '--evidence', alias)
                    self.assertEqual(result.returncode, 2, result.stderr)
                    self.assertIn('duplicate evidence', result.stderr)
                    self.assertEqual(result.stdout, '')
                    self.assertNotIn('Traceback', result.stderr)
                    self.assertEqual(report.read_bytes(), before)
                    self.assertFalse(list(root.glob('*.part')))

    def test_evaluate_blocks_tampered_resolved_evidence_aliases(self):
        q = tool()
        candidate = metadata()
        report = q.new_report(candidate)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'file.json').write_text('{"unit": "synthetic evidence only"}')
            (root / 'alias.json').symlink_to('file.json')
            (root / 'soak.json').write_text(json.dumps(self.synthetic_soak(q)))
            (root / 'installed.json').write_text(json.dumps(candidate))
            for gate_id in q.GATES:
                evidence = {'soak': 'soak.json', 'installed-apk': 'installed.json'}.get(gate_id, 'file.json')
                report = q.record_result(report, candidate, gate_id, 'passed', [evidence], root,
                                         operator='UNIT TEST ONLY', note='Synthetic validation control')
            self.assertTrue(q.evaluate_report(report, candidate, candidate['source'], root)['ready'])
            for alias in ('./file.json', 'alias.json'):
                with self.subTest(alias=alias):
                    tampered = copy.deepcopy(report)
                    evidence = tampered['gates'][0]['evidence']
                    evidence.append(dict(evidence[0], path=alias))
                    result = q.evaluate_report(tampered, candidate, candidate['source'], root)
                    self.assertFalse(result['ready'])
                    self.assertEqual(result['blockers'], ['host-tests: invalid evidence (duplicate evidence)'])

    def test_missing_evidence_traversal_empty_file_and_forged_binding_fail_closed(self):
        q = tool()
        candidate = metadata()
        report = q.new_report(candidate)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'empty.txt').touch()
            (root / 'ok.txt').write_text('unit control')
            (root / 'escape').symlink_to(ROOT)
            for paths in [[], ['missing'], ['empty.txt'], ['../outside'], [str(ROOT / 'README.md')],
                          ['escape/README.md'], ['ok.txt', 'ok.txt']]:
                with self.subTest(paths=paths), self.assertRaises(ValueError):
                    q.record_result(report, candidate, 'host-tests', 'passed', paths, root)
            changed = q.record_result(report, candidate, 'host-tests', 'passed', ['ok.txt'], root)
            changed['gates'][0]['snapshot_id'] = 'e' * 64
            self.assertTrue(any('binding' in b for b in
                                q.evaluate_report(changed, candidate, candidate['source'], root)['blockers']))
            for gate in report['gates']:
                gate['status'] = 'passed'
            self.assertFalse(q.evaluate_report(report, candidate, candidate['source'], root)['ready'])

    def synthetic_soak(self, q):
        """Explicit synthetic positive control for analysis, never device evidence."""
        return dict(snapshot=metadata(), plan=copy.deepcopy(q.SOAK_PLAN),
                    package='com.lamggm.rospocket.dev', pid=1234, watches=4, graph=True,
                    samples=[dict(monotonic_s=i * 15, pss_kib=100000 + i * 10,
                                  cpu_percent=10, received_counts=[i * 30] * 4,
                                  battery_percent=90 - i / 30, charging=False,
                                  package='com.lamggm.rospocket.dev', pid=1234)
                             for i in range(121)])

    def test_soak_short_missing_unknown_identity_and_timing_never_pass(self):
        q = tool()
        good = self.synthetic_soak(q)
        self.assertEqual(q.analyze_soak(good, metadata())['status'], 'passed')
        for edit in ['short', 'missing-cpu', 'missing-pss', 'missing-counts', 'missing-battery',
                     'nan', 'duplicate-time', 'gap', 'wrong-package', 'pid-change',
                     'no-graph', 'three-watches', 'charging', 'relaxed-plan', 'stale-apk']:
            broken = copy.deepcopy(good)
            if edit == 'short':
                broken['samples'].pop()
            elif edit.startswith('missing-'):
                field = {'missing-cpu': 'cpu_percent', 'missing-pss': 'pss_kib',
                         'missing-counts': 'received_counts', 'missing-battery': 'battery_percent'}[edit]
                broken['samples'][20][field] = None
            elif edit == 'nan':
                broken['samples'][20]['cpu_percent'] = float('nan')
            elif edit == 'duplicate-time':
                broken['samples'][20]['monotonic_s'] = broken['samples'][19]['monotonic_s']
            elif edit == 'gap':
                broken['samples'][20]['monotonic_s'] += 5
            elif edit == 'wrong-package':
                broken['package'] = 'unrelated.app'
            elif edit == 'pid-change':
                broken['samples'][20]['pid'] += 1
            elif edit == 'no-graph':
                broken['graph'] = False
            elif edit == 'three-watches':
                broken['watches'] = 3
            elif edit == 'charging':
                broken['samples'][20]['charging'] = True
            elif edit == 'relaxed-plan':
                broken['plan']['duration_s'] = 1
            elif edit == 'stale-apk':
                broken['snapshot']['sha256'] = 'e' * 64
            with self.subTest(edit=edit):
                result = q.analyze_soak(broken, metadata())
                self.assertEqual(result['status'], 'incomplete')
                self.assertTrue(result['blockers'])
        missing = copy.deepcopy(good)
        missing['samples'][20]['cpu_percent'] = None
        self.assertIsNone(q.analyze_soak(missing, metadata())['metrics']['cpu_mean_percent'])

    def test_soak_measured_budget_violations_fail(self):
        q = tool()
        good = self.synthetic_soak(q)
        self.assertEqual(q.new_report(metadata())['soak_plan'], q.SOAK_PLAN)
        for edit in ['cpu-mean', 'cpu-peak', 'pss-growth', 'pss-peak', 'intake-stall',
                     'count-reset', 'battery-loss']:
            broken = copy.deepcopy(good)
            if edit == 'cpu-mean':
                for sample in broken['samples']:
                    sample['cpu_percent'] = 30
            elif edit == 'cpu-peak':
                broken['samples'][30]['cpu_percent'] = 81
            elif edit == 'pss-growth':
                broken['samples'][-1]['pss_kib'] += 32769
            elif edit == 'pss-peak':
                broken['samples'][30]['pss_kib'] = 262145
            elif edit == 'intake-stall':
                broken['samples'][30]['received_counts'] = broken['samples'][29]['received_counts']
            elif edit == 'count-reset':
                broken['samples'][30]['received_counts'][0] = 0
            else:
                broken['samples'][-1]['battery_percent'] = 70
            with self.subTest(edit=edit):
                self.assertEqual(q.analyze_soak(broken, metadata())['status'], 'failed')

    def test_full_explicit_synthetic_report_validation_control_not_physical_acceptance(self):
        q = tool()
        candidate = metadata()
        report = q.new_report(candidate)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'unit.txt').write_text('UNIT TEST ONLY, not a physical qualification')
            (root / 'soak.json').write_text(json.dumps(self.synthetic_soak(q)))
            (root / 'installed.json').write_text(json.dumps(candidate))
            for gate_id in q.GATES:
                evidence = {'soak': 'soak.json', 'installed-apk': 'installed.json'}.get(gate_id, 'unit.txt')
                report = q.record_result(report, candidate, gate_id, 'passed',
                                         [evidence], root,
                                         operator='UNIT TEST ONLY', note='Synthetic validation control')
            self.assertTrue(q.evaluate_report(report, candidate, candidate['source'], root)['ready'])
            for field, value in [('sha256', 'e' * 64), ('source', dict(candidate['source'], dirty=True))]:
                changed = copy.deepcopy(candidate)
                changed[field] = value
                with self.subTest(field=field):
                    self.assertFalse(q.evaluate_report(report, changed, changed['source'], root)['ready'])
            stale_installed = dict(candidate, sha256='e' * 64)
            (root / 'stale-installed.json').write_text(json.dumps(stale_installed))
            for path in ['stale-installed.json', 'unit.txt']:
                with self.subTest(installed=path), self.assertRaises(ValueError):
                    q.record_result(report, candidate, 'installed-apk', 'passed', [path], root,
                                    operator='UNIT TEST ONLY', note='Must not accept stale installed APK')
            with self.assertRaises(ValueError):
                q.record_result(report, candidate, 'installed-apk', 'passed', ['installed.json', 'unit.txt'], root,
                                operator='UNIT TEST ONLY', note='Must use exact identity JSON')
            for status in ['pending', 'failed', 'blocked']:
                for gate_id in q.GATES:
                    broken = copy.deepcopy(report)
                    next(g for g in broken['gates'] if g['id'] == gate_id)['status'] = status
                    with self.subTest(status=status, gate=gate_id):
                        self.assertFalse(q.evaluate_report(broken, candidate, candidate['source'], root)['ready'])
            for gate in report['gates']:
                broken = copy.deepcopy(report)
                next(g for g in broken['gates'] if g['id'] == gate['id'])['evidence'] = []
                with self.subTest(missing_evidence=gate['id']):
                    self.assertFalse(q.evaluate_report(broken, candidate, candidate['source'], root)['ready'])
            current = dict(candidate['source'], snapshot_sha256='e' * 64)
            self.assertFalse(q.evaluate_report(report, candidate, current, root)['ready'])
            dirty = copy.deepcopy(candidate)
            dirty['source']['dirty'] = True
            dirty_report = q.new_report(dirty)
            soak = self.synthetic_soak(q)
            soak['snapshot'] = dirty
            (root / 'dirty-soak.json').write_text(json.dumps(soak))
            (root / 'dirty-installed.json').write_text(json.dumps(dirty))
            for gate_id in q.GATES:
                evidence = {'soak': 'dirty-soak.json', 'installed-apk': 'dirty-installed.json'}.get(gate_id, 'unit.txt')
                dirty_report = q.record_result(dirty_report, dirty, gate_id, 'passed', [evidence], root,
                                               operator='UNIT TEST ONLY', note='Dirty positive control')
            self.assertEqual(q.evaluate_report(dirty_report, dirty, dirty['source'], root),
                             {'ready': False, 'blockers': ['dirty source']})
            broken = copy.deepcopy(report)
            next(g for g in broken['gates'] if g['id'] == 'operator-acceptance')['operator'] = ''
            self.assertFalse(q.evaluate_report(broken, candidate, candidate['source'], root)['ready'])
            broken = copy.deepcopy(report)
            next(g for g in broken['gates'] if g['id'] == 'soak')['evidence'] = [
                report['gates'][0]['evidence'][0]]
            self.assertFalse(q.evaluate_report(broken, candidate, candidate['source'], root)['ready'])
            short = self.synthetic_soak(q)
            short['samples'].pop()
            (root / 'short.json').write_text(json.dumps(short))
            with self.assertRaises(ValueError):
                q.record_result(report, candidate, 'soak', 'passed', ['short.json'], root)

    def test_cli_generates_records_and_evaluates_real_files_without_device_calls(self):
        q = tool()
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            candidate = metadata()
            candidate['source'] = json.loads(subprocess.check_output(
                [sys.executable, str(ROOT / 'tools/artifact_metadata.py'), '--source', '--repo', str(ROOT)],
                text=True))
            apk = root / 'synthetic.apk'
            apk.write_bytes(b'TOOL UNIT TEST ONLY; not a real installable APK')
            candidate.update(sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), size_bytes=apk.stat().st_size)
            meta = root / 'metadata.json'
            meta.write_text(json.dumps(candidate))
            report = root / 'report.json'
            def run(*args):
                return subprocess.run([sys.executable, str(ROOT / 'tools/qualification.py'), *map(str, args)],
                                      capture_output=True, text=True, timeout=10)
            self.assertNotEqual(run().returncode, 0, 'No default actions')
            matrix = run('matrix')
            self.assertEqual(matrix.returncode, 0, matrix.stderr)
            self.assertEqual(set(json.loads(matrix.stdout)['gates']), set(q.GATES))
            common = ['--metadata', meta, '--apk', apk, '--repo', ROOT]
            initialized = run('init', *common, '--output', report)
            self.assertEqual(initialized.returncode, 0, initialized.stderr)
            self.assertTrue(report.is_file())
            (root / 'evidence.txt').write_text('Synthetic CLI control only')
            recorded = run('record', '--metadata', meta, '--report', report, '--evidence-root', root,
                           '--repo', ROOT, '--gate', 'host-tests', '--status', 'passed',
                           '--evidence', 'evidence.txt')
            self.assertEqual(recorded.returncode, 0, recorded.stderr)
            stored = json.loads(report.read_text())
            self.assertEqual(stored['gates'][0]['status'], 'passed')
            evaluated = run('evaluate', *common, '--report', report, '--evidence-root', root)
            self.assertEqual(evaluated.returncode, 1, evaluated.stderr)
            self.assertFalse(json.loads(evaluated.stdout)['ready'])
            before = report.read_bytes()
            rejected = run('record', '--metadata', meta, '--report', report, '--evidence-root', root,
                           '--repo', ROOT, '--gate', 'operator-acceptance', '--status', 'passed',
                           '--evidence', 'evidence.txt')
            self.assertNotEqual(rejected.returncode, 0)
            self.assertEqual(report.read_bytes(), before, 'Rejected writes preserve report')
            apk.write_bytes(b'wrong APK')
            mismatch = run('evaluate', *common, '--report', report, '--evidence-root', root)
            self.assertNotEqual(mismatch.returncode, 0)
            self.assertIn('APK', mismatch.stderr)
            absent = run('init', '--metadata', meta, '--apk', root / 'absent.apk',
                         '--repo', ROOT, '--output', root / 'absent-report.json')
            self.assertNotEqual(absent.returncode, 0)
            self.assertFalse((root / 'absent-report.json').exists())
            # Soak command is pure analysis; the JSON below is explicitly synthetic.
            soak = root / 'soak.json'
            soak.write_text(json.dumps(self.synthetic_soak(q)))
            synthetic_meta = root / 'synthetic-meta.json'
            synthetic_meta.write_text(json.dumps(metadata()))
            result = run('analyze-soak', '--metadata', synthetic_meta, '--input', soak)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(result.stdout)['status'], 'passed')

    def test_cli_rejects_duplicate_json_keys_stale_source_and_public_output(self):
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            meta = root / 'meta.json'
            meta.write_text('{"schema_version": 1, "schema_version": 1}')
            result = subprocess.run([sys.executable, str(ROOT / 'tools/qualification.py'),
                                     'init', '--metadata', str(meta), '--apk', str(root / 'absent'),
                                     '--output', str(root / 'report.json')], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('duplicate JSON key', result.stderr)
            candidate = metadata()
            apk = root / 'unit.apk'
            apk.write_bytes(b'unit')
            candidate.update(sha256=hashlib.sha256(b'unit').hexdigest(), size_bytes=4)
            meta.write_text(json.dumps(candidate))
            result = subprocess.run([sys.executable, str(ROOT / 'tools/qualification.py'),
                                     'init', '--metadata', str(meta), '--apk', str(apk), '--repo', str(ROOT),
                                     '--output', str(root / 'report.json')], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('source mismatch', result.stderr)
            candidate['source'] = json.loads(subprocess.check_output(
                [sys.executable, str(ROOT / 'tools/artifact_metadata.py'), '--source'], text=True))
            meta.write_text(json.dumps(candidate))
            result = subprocess.run([sys.executable, str(ROOT / 'tools/qualification.py'),
                                     'init', '--metadata', str(meta), '--apk', str(apk), '--repo', str(ROOT),
                                     '--output', str(ROOT / 'PUBLIC-QUALIFICATION.json')], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('build/', result.stderr)
            self.assertFalse((ROOT / 'PUBLIC-QUALIFICATION.json').exists())

    def test_report_temp_symlink_cannot_overwrite_external_file(self):
        q = tool()
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            repo = root / 'repo'
            output = repo / 'build/report.json'
            output.parent.mkdir(parents=True)
            output.write_text('previous report')
            victim = root / 'outside-build.txt'
            victim.write_text('external file must not change')
            legacy_temp = output.with_suffix('.json.part')
            legacy_temp.symlink_to(victim)
            # An already-open reader keeps the old report across atomic replacement.
            with output.open() as old_reader:
                q.write_report(output, {'new': 'report'}, repo)
                self.assertEqual(old_reader.read(), 'previous report')
            self.assertEqual(victim.read_text(), 'external file must not change')
            self.assertTrue(legacy_temp.is_symlink())
            self.assertEqual(json.loads(output.read_text()), {'new': 'report'})
            self.assertEqual(set(output.parent.iterdir()), {output, legacy_temp})

    def test_report_write_failures_preserve_existing_report_and_clean_temp(self):
        q = tool()
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            repo = Path(directory)
            output = repo / 'build/report.json'
            output.parent.mkdir()
            output.write_text('previous report')
            with self.assertRaises(ValueError):
                q.write_report(output, {'invalid': float('nan')}, repo)
            self.assertEqual(output.read_text(), 'previous report')
            self.assertEqual(list(output.parent.iterdir()), [output])
            with mock.patch.object(Path, 'replace', side_effect=OSError('replace failed')):
                with self.assertRaisesRegex(OSError, 'replace failed'):
                    q.write_report(output, {'new': 'report'}, repo)
            self.assertEqual(output.read_text(), 'previous report')
            self.assertEqual(list(output.parent.iterdir()), [output])

    def test_json_container_depth_limit_covers_lists_dicts_and_unknown_fields(self):
        q = tool()
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'nested.json'
            for kind in ('list', 'dict', 'mixed'):
                for depth in (63, 64, 65, 600):
                    text = '0'
                    for index in range(depth):
                        text = ('[' + text + ']' if kind == 'list' or
                                (kind == 'mixed' and index % 2 == 0)
                                else '{"child":' + text + '}')
                    source.write_text(text)
                    with self.subTest(kind=kind, depth=depth):
                        if depth <= 64:
                            loaded = q.load_json(source)
                            self.assertEqual(loaded, json.loads(text))
                            self.assertEqual(copy.deepcopy(loaded), loaded)
                            self.assertEqual(json.loads(json.dumps(loaded)), loaded)
                        else:
                            with self.assertRaisesRegex(ValueError, 'JSON container depth exceeds 64'):
                                q.load_json(source)
            # The root object counts as one container; unknown fields are not skipped.
            source.write_text('{"known": [], "unknown":' + '[' * 64 + '0' + ']' * 64 + '}')
            with self.assertRaisesRegex(ValueError, 'JSON container depth exceeds 64'):
                q.load_json(source)
            shallow = {'unknown': [None, True, 'text', {'number': 1.25}], 'empty': {}}
            source.write_text(json.dumps(shallow))
            self.assertEqual(q.load_json(source), shallow, 'Preserve all admitted fields')

    def test_cli_record_rejects_decodable_deep_report_before_copy_without_mutation(self):
        q = tool()
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            candidate = metadata()
            candidate['source'] = json.loads(subprocess.check_output(
                [sys.executable, str(ROOT / 'tools/artifact_metadata.py'), '--source', '--repo', str(ROOT)],
                text=True))
            meta = root / 'metadata.json'
            meta.write_text(json.dumps(candidate))
            report = q.new_report(candidate)
            report['gates'][0]['evidence'] = ['nested-evidence']
            path = root / 'report.json'
            path.write_text(json.dumps(report).replace('"nested-evidence"', '[' * 600 + '0' + ']' * 600))
            before = path.read_bytes()
            result = subprocess.run(
                [sys.executable, str(ROOT / 'tools/qualification.py'), 'record',
                 '--metadata', str(meta), '--report', str(path), '--evidence-root', str(root),
                 '--repo', str(ROOT), '--gate', 'host-lint', '--status', 'blocked'],
                capture_output=True, text=True, timeout=10)
            self.assertEqual(path.read_bytes(), before, 'Rejected writes preserve exact report bytes')
            self.assertEqual(result.stdout, '')
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertIn('qualification error: JSON container depth exceeds 64', result.stderr)
            self.assertNotIn('Traceback', result.stderr)

    def test_deep_json_is_rejected_in_loader_and_cli(self):
        q = tool()
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            meta = root / 'metadata.json'
            meta.write_text(json.dumps(metadata()))
            source = root / 'deep.json'
            for kind, opening, closing in (('list', '[', ']'), ('dict', '{"child":', '}')):
                for malformed in (False, True):
                    source.write_text(opening * 100000 + '0' + ('' if malformed else closing * 100000))
                    self.assertLess(source.stat().st_size, 4 * 1024 * 1024)
                    with self.subTest(kind=kind, malformed=malformed, path='loader'):
                        with self.assertRaisesRegex(ValueError, 'JSON nesting exceeds decoder limit'):
                            q.load_json(source)
                    with self.subTest(kind=kind, malformed=malformed, path='cli'):
                        result = subprocess.run(
                            [sys.executable, str(ROOT / 'tools/qualification.py'), 'analyze-soak',
                             '--metadata', str(meta), '--input', str(source)],
                            capture_output=True, text=True, timeout=10)
                        self.assertEqual(result.returncode, 2, result.stderr)
                        self.assertIn('qualification error: JSON nesting exceeds decoder limit', result.stderr)
                        self.assertNotIn('Traceback', result.stderr)
                        self.assertEqual(result.stdout, '')

    def test_json_exponent_overflow_is_rejected_in_loader_and_cli(self):
        q = tool()
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            meta = root / 'metadata.json'
            meta.write_text(json.dumps(metadata()))
            source = root / 'soak.json'
            for token in ('1e400', '-1e400', '1e+400', '-1e+400'):
                soak = self.synthetic_soak(q)
                soak['samples'][20]['cpu_percent'] = 'overflow-number'
                source.write_text(json.dumps(soak).replace('"overflow-number"', token))
                with self.subTest(token=token, path='loader'):
                    with self.assertRaisesRegex(ValueError, 'nonfinite JSON number'):
                        q.load_json(source)
                with self.subTest(token=token, path='cli'):
                    result = subprocess.run(
                        [sys.executable, str(ROOT / 'tools/qualification.py'), 'analyze-soak',
                         '--metadata', str(meta), '--input', str(source)],
                        capture_output=True, text=True, timeout=10)
                    self.assertEqual(result.returncode, 2, result.stderr)
                    self.assertIn('qualification error: nonfinite JSON number', result.stderr)
                    self.assertNotIn('Traceback', result.stderr)
                    self.assertEqual(result.stdout, '')
            source.write_text('{"finite": 1.25e2, "count": ' + str(10 ** 400) + '}')
            self.assertEqual(q.load_json(source), {'finite': 125.0, 'count': 10 ** 400})

    def test_oversized_soak_metrics_are_incomplete_without_traceback(self):
        q = tool()
        scratch = ROOT / 'build/qualification/unit'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory)
            meta = root / 'metadata.json'
            meta.write_text(json.dumps(metadata()))
            source = root / 'soak.json'
            cases = [(key, value) for key in
                     ('monotonic_s', 'pss_kib', 'cpu_percent', 'battery_percent')
                     for value in (10 ** 400, -(10 ** 400))]
            cases += [('cpu_percent', 1e308), ('received_counts', 10 ** 400),
                      ('received_counts', -(10 ** 400)), ('received_counts', 2 ** 64)]
            for key, value in cases:
                soak = self.synthetic_soak(q)
                soak['samples'][20][key] = [value] * 4 if key == 'received_counts' else value
                source.write_text(json.dumps(soak))
                with self.subTest(key=key, value=str(value), path='analysis'):
                    result = q.analyze_soak(soak, metadata())
                    self.assertEqual(result['status'], 'incomplete')
                    self.assertTrue(result['blockers'])
                    self.assertTrue(all(v is None for v in result['metrics'].values()))
                with self.subTest(key=key, value=str(value), path='cli'):
                    result = subprocess.run(
                        [sys.executable, str(ROOT / 'tools/qualification.py'), 'analyze-soak',
                         '--metadata', str(meta), '--input', str(source)],
                        capture_output=True, text=True, timeout=10)
                    self.assertEqual(result.returncode, 1, result.stderr)
                    self.assertEqual(result.stderr, '')
                    analysis = json.loads(result.stdout)
                    self.assertEqual(analysis['status'], 'incomplete')
                    self.assertTrue(all(v is None for v in analysis['metrics'].values()))
            # uint64 cumulative counts stay integers: a large baseline must not
            # round away the observed 30-message deltas before ratio calculation.
            good = self.synthetic_soak(q)
            baseline = 2 ** 64 - 1 - 3600
            for i, sample in enumerate(good['samples']):
                sample['received_counts'] = [baseline + i * 30] * 4
            analysis = q.analyze_soak(good, metadata())
            self.assertEqual(analysis['status'], 'passed')
            self.assertEqual(analysis['metrics']['ingestion_min_ratio'], 1)

    def test_ambiguous_structured_evidence_json_and_unbounded_input_rejected(self):
        q = tool()
        candidate = metadata()
        report = q.new_report(candidate)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            valid = json.dumps(self.synthetic_soak(q))
            (root / 'ambiguous.json').write_text(valid[:-1] + ', "pid": 1234}')
            with self.assertRaisesRegex(ValueError, 'duplicate JSON key'):
                q.record_result(report, candidate, 'soak', 'passed', ['ambiguous.json'], root)
            (root / 'large.json').write_bytes(b' ' * (4 * 1024 * 1024 + 1))
            with self.assertRaises(ValueError):
                q.load_json(root / 'large.json')
            (root / 'nan.json').write_text('{"value": NaN}')
            with self.assertRaises(ValueError):
                q.load_json(root / 'nan.json')


if __name__ == '__main__':
    unittest.main()
