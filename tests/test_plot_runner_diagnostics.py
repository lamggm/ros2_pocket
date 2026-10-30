"""Container transport failure must retain the actual fixture error in CI."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('plot_runner_under_test', ROOT / 'tests/run_plot_host.py')
assert spec is not None and spec.loader is not None
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


class PlotRunnerDiagnosticsTests(unittest.TestCase):
    def test_failed_fixture_exposes_internal_log_and_still_removes_container(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ('tests/device_plot_fixture.py', 'tests/device_plot_writer.cpp',
                         'tests/plot_phase_host.py', 'tests/plot_batch_selection.py', 'tests/fixture-loopback.xml',
                         'build/plot-stamped-writer', 'build/native-host/multiwatch_wire'):
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'synthetic fixture input')
            image, cid, calls = 'sha256:fixture', 'a' * 64, []
            removed = False

            def docker(command, **kwargs):
                nonlocal removed
                calls.append(command)
                action = command[1]
                if action == 'image':
                    value = json.dumps([{'Id': image}])
                elif action == 'run':
                    self.assertEqual(command[command.index('--network') + 1], 'none')
                    value = cid
                elif action == 'inspect':
                    if removed:
                        return subprocess.CompletedProcess(command, 1, '', 'Error: No such object: ' + cid)
                    value = json.dumps([{'Image': image, 'HostConfig': {'NetworkMode': 'none'}}])
                elif action == 'wait':
                    value = '1\n'
                elif action == 'logs':
                    return subprocess.CompletedProcess(command, 0, 'fixture started\n', 'ValueError: Invalid control file\n')
                elif action == 'rm':
                    removed, value = True, cid
                else:
                    self.fail('Unexpected Docker action: ' + action)
                return subprocess.CompletedProcess(command, 0, value, '')

            out = root / 'reports'
            argv = ['run_plot_host.py', '--image', image, '--toolchains', str(root / 'toolchains'), '--out', str(out)]
            with patch.object(runner, '__file__', str(root / 'tests/run_plot_host.py')), \
                    patch.object(sys, 'argv', argv), patch.object(runner.subprocess, 'run', side_effect=docker):
                with self.assertRaisesRegex(RuntimeError, 'Invalid control file'):
                    runner.main()
            self.assertTrue(removed)
            self.assertTrue(json.loads((out / 'rmw_fastrtps_cpp/cleanup.json').read_text())['inspectExit'])
            self.assertIn('Invalid control file', (out / 'rmw_fastrtps_cpp/container.log').read_text())


if __name__ == '__main__':
    unittest.main()
