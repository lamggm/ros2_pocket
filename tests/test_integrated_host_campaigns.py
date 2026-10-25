"""Synthetic runner controls; real DDS/Canvas results come from the opt-in pipeline."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def module():
    spec = importlib.util.spec_from_file_location('host_campaigns', ROOT / 'tools/host_campaigns.py')
    assert spec is not None and spec.loader is not None
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


class IntegratedHostTests(unittest.TestCase):
    def test_plan_uses_explicit_pinned_prefix_and_all_new_real_gates(self):
        m = module()
        plan = m.commands(Path('/source'), Path('/existing/pins'), 'sha256:'+'a'*64, Path('/source/build/run'))
        text = json.dumps(plan)
        self.assertNotIn('/home/lamggm/Documents', text)
        self.assertIn('POCKET_ENABLE_TEST_HOOKS=ON', text)
        self.assertIn('multiwatch13_native.cpp', text)
        self.assertIn('device_plot_writer.cpp', text)
        self.assertEqual(sum('tests/run_multiwatch13.py' in command for command in plan), 2)
        self.assertIn('rmw_fastrtps_cpp', text)
        self.assertIn('rmw_cyclonedds_cpp', text)
        self.assertEqual(sum('tests/run_plot_host.py' in command for command in plan), 1)
        self.assertTrue(any(':app:testDebugUnitTest' in command and '--rerun-tasks' in command for command in plan))
        self.assertNotIn('assembleRelease', text)
        self.assertNotIn('adb', text)

    def test_multiwatch_runner_honors_the_explicit_toolchain_root(self):
        import os
        from unittest import mock
        with mock.patch.dict(os.environ, POCKET_TOOLCHAINS='/existing/pins with spaces'):
            spec = importlib.util.spec_from_file_location('explicit_multiwatch_prefix', ROOT/'tests/run_multiwatch13.py')
            self.assertIsNotNone(spec)
            assert spec is not None and spec.loader is not None
            m = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(m)
        self.assertEqual(m.PREFIX, Path('/existing/pins with spaces/host'))

    def test_empty_failed_skipped_or_missing_replay_output_is_not_success(self):
        m = module()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for attrs in ('tests="0" failures="0" errors="0" skipped="0"',
                          'tests="1" failures="1" errors="0" skipped="0"',
                          'tests="1" failures="0" errors="0" skipped="1"'):
                (root/'replay.xml').write_text('<testsuite '+attrs+'/>')
                with self.subTest(attrs=attrs), self.assertRaises(ValueError):
                    m.verify_replay(root/'replay.xml')
            with self.assertRaises((ValueError,OSError)):
                m.verify_replay(root/'missing.xml')
            (root/'replay.xml').write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"><testcase name="emptySelectedControlsAreNotHealthyCanvasEvidence"/><testcase name="bothRealRmwStreamsTraverseModelWindowAndCompletedCanvas"/></testsuite>')
            m.verify_replay(root/'replay.xml')

    def test_multiwatch_result_requires_exact_real_case_set(self):
        m = module()
        cases = ['mixed_rates', 'fifth_rejected', 'negative_qos', 'qos_recovery',
                 'topic_failure_0', 'reader_failure_0', 'topic_failure_2', 'reader_failure_2',
                 'same_topic', 'remove_same_topic', 'recreate', 'rosout_clear', 'stop']
        record = dict(cleanupVerified=True, exitCode=0, result=dict(cases=cases,
                      classification='host-isolated-real-dds-not-android'))
        m.verify_multiwatch(record)
        for bad in ([], cases[:-1], cases+[cases[0]], 13):
            with self.subTest(cases=bad), self.assertRaises(ValueError):
                m.verify_multiwatch(dict(record, result=dict(record['result'], cases=bad)))

    def test_registered_integrated_front_tests_are_required_and_common_ci_runs_them(self):
        registry = json.loads((ROOT/'ci/finalization-gates.json').read_text())
        for gate in registry['gates']:
            self.assertEqual(gate['state'], 'registered')
            self.assertGreaterEqual(gate['minimum_tests'], 10)
            self.assertTrue(gate['test_files'])
        runner = (ROOT/'tools/test.sh').read_text()
        self.assertIn('python3 tools/verify_finalization_ci.py', runner)
        self.assertIn('python3 tools/host_campaigns.py', runner)
        # Preserve the existing hosted workflow; no credential-scope escalation
        # is required to make common entrypoints execute integrated gates.
        workflow = (ROOT/'.github/workflows/verify.yml').read_text()
        self.assertIn('bash tools/test.sh --fixture', workflow)
        self.assertIn('bash tools/build.sh --online', workflow)


if __name__ == '__main__':
    unittest.main()
