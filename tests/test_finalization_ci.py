"""Real CLI regression controls for CI registration, not 11/13/14 acceptance."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class FinalizationCiTests(unittest.TestCase):
    def registry(self, state='pending_integration', files=None):
        return {'schema_version': 1, 'gates': [
            {'id': gate, 'state': state, 'test_files': files or [], 'minimum_tests': 1}
            for gate in ('11-soak', '13-multiwatch', '14-plot')]}

    def run_cli(self, registry, files=None):
        # A task-private source sandbox with explicitly synthetic unittest controls.
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'tools').mkdir()
            (root / 'tests').mkdir()
            script = ROOT / 'tools/verify_finalization_ci.py'
            if script.exists():
                shutil.copy2(script, root / 'tools/verify_finalization_ci.py')
            (root / 'gates.json').write_text(json.dumps(registry))
            for name, source in (files or {}).items():
                (root / name).write_text(source)
            return subprocess.run([sys.executable, 'tools/verify_finalization_ci.py',
                                   '--registry', 'gates.json'], cwd=root,
                                  text=True, capture_output=True, timeout=10)

    def registered_controls(self, body):
        registry = self.registry('registered')
        files = {}
        for index, gate in enumerate(registry['gates']):
            name = f'tests/test_control_{index}.py'
            gate['test_files'] = [name]
            files[name] = 'import unittest\nclass Control(unittest.TestCase):\n' + body
        return registry, files

    def test_registered_nonempty_real_unittest_suites_execute_per_gate(self):
        registry, files = self.registered_controls('    def test_control(self): self.assertEqual(4, 2+2)\n')
        result = self.run_cli(registry, files)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        for gate in ('11-soak', '13-multiwatch', '14-plot'):
            self.assertIn(gate + ': 1 tests, 0 skips', result.stdout)
        self.assertEqual(3, result.stderr.count('Ran 1 test'))

    def test_empty_missing_below_minimum_or_skipped_selection_never_passes(self):
        for mode in ('empty-files', 'missing-file', 'zero-tests', 'below-minimum', 'skipped', 'failed', 'expected-failure'):
            with self.subTest(mode=mode):
                registry, files = self.registered_controls('    def test_control(self): pass\n')
                first = registry['gates'][0]
                if mode == 'empty-files': first['test_files'] = []
                elif mode == 'missing-file': files.pop(first['test_files'][0])
                elif mode == 'zero-tests': files[first['test_files'][0]] = 'import unittest\n'
                elif mode == 'below-minimum': first['minimum_tests'] = 2
                elif mode == 'skipped': files[first['test_files'][0]] = 'import unittest\n@unittest.skip("synthetic control")\nclass Control(unittest.TestCase):\n    def test_control(self): pass\n'
                elif mode == 'failed': files[first['test_files'][0]] = 'import unittest\nclass Control(unittest.TestCase):\n    def test_control(self): self.fail("synthetic failure")\n'
                else: files[first['test_files'][0]] = 'import unittest\nclass Control(unittest.TestCase):\n    @unittest.expectedFailure\n    def test_control(self): self.fail("synthetic expected failure")\n'
                result = self.run_cli(registry, files)
                self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertNotIn('14-plot: 1 tests', result.stdout)

    def test_malformed_or_ambiguous_registration_blocks_before_execution(self):
        for mode in ('duplicate-gate', 'missing-gate', 'boolean-version', 'boolean-minimum', 'zero-minimum', 'duplicate-file', 'outside-tests', 'unknown-key', 'non-list-files'):
            with self.subTest(mode=mode):
                registry, files = self.registered_controls('    def test_control(self): pass\n')
                first = registry['gates'][0]
                if mode == 'duplicate-gate': registry['gates'].append(dict(first))
                elif mode == 'missing-gate': registry['gates'].pop()
                elif mode == 'boolean-version': registry['schema_version'] = True
                elif mode == 'boolean-minimum': first['minimum_tests'] = True
                elif mode == 'zero-minimum': first['minimum_tests'] = 0
                elif mode == 'duplicate-file': registry['gates'][1]['test_files'] = first['test_files']
                elif mode == 'outside-tests': first['test_files'] = ['tools/verify_finalization_ci.py']
                elif mode == 'unknown-key': first['skip'] = True
                else: first['test_files'] = first['test_files'][0]
                result = self.run_cli(registry, files)
                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertNotIn('Ran ', result.stderr)

    def test_pending_registration_blocks_before_any_test_execution(self):
        result = self.run_cli(self.registry())
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        for gate in ('11-soak', '13-multiwatch', '14-plot'):
            self.assertIn(gate + ': pending integration', result.stderr)
        self.assertNotIn('Ran ', result.stderr)
