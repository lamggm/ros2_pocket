"""Synthetic subprocess-boundary controls for the real graph interop entrypoint."""
import importlib.util
import pathlib
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock


PATH = pathlib.Path(__file__).with_name('graph_format_interop.py')
SPEC = importlib.util.spec_from_file_location('graph_interop_timeout_subject', PATH)
assert SPEC is not None and SPEC.loader is not None
interop = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(interop)


class GraphInteropTimeoutTests(unittest.TestCase):
    def test_timeout_retains_partial_outputs_without_hiding_failure_or_cleanup(self):
        for stdout, stderr in [
            (b'{"partial":true}\n\xff', b'partial DDS diagnostic\n\xfe'),
            ('partial Unicode: π\n', 'diagnóstico parcial\n'),
            (None, None),
        ]:
            with self.subTest(stdout_type=type(stdout).__name__):
                with tempfile.TemporaryDirectory() as temporary:
                    root = pathlib.Path(temporary)
                    expired = subprocess.TimeoutExpired(['synthetic-probe', 'jazzy'], 20,
                                                        output=stdout, stderr=stderr)
                    producer = mock.Mock()
                    producer.poll.return_value = None
                    calls = []

                    def run(args, **kwargs):
                        calls.append((args, kwargs))
                        if args[0] == 'ldd':
                            return subprocess.CompletedProcess(args, 0, 'libddsc.so => /synthetic/ddsc.so\n', '')
                        raise expired

                    boundary = types.SimpleNamespace(run=run, Popen=mock.Mock(return_value=producer),
                                                     TimeoutExpired=subprocess.TimeoutExpired)
                    argv = ['graph_format_interop.py', '--probe', '/synthetic/probe', '--mode', 'jazzy',
                            '--distro', 'jazzy', '--out', str(root)]
                    with mock.patch.object(interop, 'subprocess', boundary), \
                            mock.patch.object(interop.socket, 'if_nameindex', return_value=[(1, 'lo')]), \
                            mock.patch.dict(interop.os.environ, {'ROS_DISTRO': 'jazzy',
                                                               'RMW_IMPLEMENTATION': 'rmw_fastrtps_cpp',
                                                               'LD_LIBRARY_PATH': '/synthetic/ros/lib'}), \
                            mock.patch.object(sys, 'argv', argv):
                        with self.assertRaises(subprocess.TimeoutExpired) as caught:
                            interop.main()
                    self.assertIs(caught.exception, expired)
                    producer.terminate.assert_called_once_with()
                    producer.wait.assert_called_once_with(timeout=5)
                    producer.kill.assert_not_called()
                    self.assertEqual(calls[-1][1]['timeout'], 20)
                    self.assertNotIn('LD_LIBRARY_PATH', calls[-1][1]['env'])
                    self.assertEqual(boundary.Popen.call_args.kwargs['env']['LD_LIBRARY_PATH'],
                                     '/synthetic/ros/lib')
                    directories = list(root.iterdir())
                    self.assertEqual(len(directories), 1)
                    out = directories[0]
                    self.assertTrue((out / 'native.jsonl').is_file(), 'partial stdout missing after timeout')
                    self.assertTrue((out / 'native.err').is_file(), 'partial stderr missing after timeout')
                    expected_stdout = stdout if isinstance(stdout, bytes) else (stdout or '').encode('utf-8')
                    expected_stderr = stderr if isinstance(stderr, bytes) else (stderr or '').encode('utf-8')
                    self.assertEqual((out / 'native.jsonl').read_bytes(), expected_stdout)
                    self.assertEqual((out / 'native.err').read_bytes(), expected_stderr)
                    self.assertFalse((out / 'result.json').exists(), 'timeout must not fabricate a result/PASS')

    def test_completed_capture_is_still_written_before_validation_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = pathlib.Path(temporary)
            producer = mock.Mock()
            producer.poll.return_value = None

            def run(args, **kwargs):
                if args[0] == 'ldd':
                    return subprocess.CompletedProcess(args, 0, 'libddsc.so => /synthetic/ddsc.so\n', '')
                return subprocess.CompletedProcess(args, 7, 'intentionally invalid JSON\n', 'completed diagnostic\n')

            boundary = types.SimpleNamespace(run=run, Popen=mock.Mock(return_value=producer),
                                             TimeoutExpired=subprocess.TimeoutExpired)
            argv = ['graph_format_interop.py', '--probe', '/synthetic/probe', '--mode', 'jazzy',
                    '--distro', 'jazzy', '--out', str(root)]
            with mock.patch.object(interop, 'subprocess', boundary), \
                    mock.patch.object(interop.socket, 'if_nameindex', return_value=[(1, 'lo')]), \
                    mock.patch.dict(interop.os.environ, {'ROS_DISTRO': 'jazzy',
                                                       'RMW_IMPLEMENTATION': 'rmw_fastrtps_cpp'}), \
                    mock.patch.object(sys, 'argv', argv):
                with self.assertRaises(interop.json.JSONDecodeError):
                    interop.main()
            out, = root.iterdir()
            self.assertEqual((out / 'native.jsonl').read_text(), 'intentionally invalid JSON\n')
            self.assertEqual((out / 'native.err').read_text(), 'completed diagnostic\n')
            producer.terminate.assert_called_once_with()
            producer.wait.assert_called_once_with(timeout=5)
            self.assertFalse((out / 'result.json').exists())


if __name__ == '__main__':
    unittest.main()
