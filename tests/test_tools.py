"""Portable command contracts; all subprocesses use temporary user caches."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class ToolTests(unittest.TestCase):
    def test_build_rejects_unknown_mode_before_toolchain_work(self):
        result = subprocess.run(['bash', str(ROOT / 'tools/build.sh'), '--typo'],
                                text=True, capture_output=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn('Usage:', result.stderr)

    def test_build_uses_checkout_wrapper_online_by_default(self):
        self.check_build_mode([], False)

    def test_build_offline_is_explicit(self):
        self.check_build_mode(['--offline'], True)

    def test_offline_bootstrap_empty_cache_fails_without_network(self):
        with tempfile.TemporaryDirectory() as directory:
            for command in ('curl', 'git'):
                fake = Path(directory) / command
                fake.write_text('#!/bin/sh\nexit 99\n')
                fake.chmod(0o755)
            env = dict(os.environ, POCKET_TOOLCHAINS=directory, TMPDIR=directory,
                       PATH=directory + ':/usr/bin:/bin')
            result = subprocess.run(['bash', str(ROOT / 'tools/bootstrap.sh'), '--offline'],
                                    env=env, capture_output=True, text=True, timeout=5)
            self.assertEqual(result.returncode, 2)
            self.assertIn('Offline bootstrap requires', result.stderr)
            self.assertFalse(list(Path(directory).glob('*.part')))

    def test_test_runner_rejects_unknown_mode(self):
        result = subprocess.run(['bash', str(ROOT / 'tools/test.sh'), '--typo'],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn('Usage:', result.stderr)

    def test_offline_build_empty_wrapper_cache_fails_before_network(self):
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run(['bash', str(ROOT / 'tools/build.sh'), '--offline'],
                                    env=dict(os.environ, GRADLE_USER_HOME=directory,
                                             JAVA_HOME=directory, POCKET_TOOLCHAINS=directory),
                                    text=True, capture_output=True)
            self.assertEqual(result.returncode, 2)
            self.assertIn('Offline build requires Gradle 8.7 wrapper distribution', result.stderr)

    def test_fixture_runner_isolates_loopback_in_own_network_namespace(self):
        import shutil
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'tools').mkdir()
            shutil.copy(ROOT / 'tools/test.sh', root / 'tools/test.sh')
            (root / 'tests').mkdir()
            (root / 'tests/fixture.Dockerfile').write_text('test recipe')
            (root / 'tests/fixture.py').write_text('test publisher')
            (root / 'tests/fixture-loopback.xml').write_text('test discovery')
            (root / 'build/native-host').mkdir(parents=True)
            (root / 'bin').mkdir()
            for command in ('cmake', 'idlc', 'ctest'):
                path = root / 'bin' / command
                path.write_text('#!/bin/sh\nexit 0\n')
                path.chmod(0o755)
            (root / 'host/bin').mkdir(parents=True)
            shutil.copy(root / 'bin/idlc', root / 'host/bin/idlc')
            docker = root / 'bin/docker'
            docker.write_text('#!/bin/sh\nprintf "%s " "$@" >> "$DOCKER_CALLS"\nprintf "\\n" >> "$DOCKER_CALLS"\n')
            docker.chmod(0o755)
            calls = root / 'docker-calls'
            result = subprocess.run(['bash', str(root / 'tools/test.sh'), '--fixture'],
                                    env=dict(os.environ, POCKET_TOOLCHAINS=str(root),
                                             DOCKER_CALLS=str(calls), PATH=str(root / 'bin') + ':' + os.environ['PATH']),
                                    text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            runs = [line for line in calls.read_text().splitlines() if line.startswith('run ')]
            self.assertEqual(len(runs), 2)
            for run in runs:
                self.assertIn('--network none', run)
                self.assertNotIn('--network host', run)
                self.assertNotIn('--rm ', run, 'Retain a failed fixture until diagnostics and cleanup')
                self.assertNotIn('--name ros2-pocket-fixture ', run)
            self.assertEqual(sum(line.startswith('rm -f ') for line in calls.read_text().splitlines()), 2)
            self.assertFalse(any(line.startswith('cp ') for line in calls.read_text().splitlines()),
                             'Read tmpfs reports through exec, not docker cp')

    def test_fixture_image_hash_covers_every_build_input(self):
        runner = (ROOT / 'tools/test.sh').read_text()
        self.assertIn('sha256sum tests/fixture.Dockerfile tests/fixture.py tests/fixture-loopback.xml', runner)
        dockerfile = (ROOT / 'tests/fixture.Dockerfile').read_text()
        copied = [line.split()[1] for line in dockerfile.splitlines() if line.startswith('COPY ')]
        self.assertEqual(copied, ['fixture.py', 'fixture-loopback.xml'])

    def test_portable_runner_rejects_empty_ctest_selection(self):
        result, _, ctest_calls = self.run_ctest_gate('--portable')
        self.assertIn('No tests were found', result.stdout + result.stderr)
        self.assertEqual(len(ctest_calls), 1, 'The real CTest gate must run')
        self.assertNotEqual(result.returncode, 0, 'An empty portable label must fail')

    def test_portable_runner_executes_connection_policy_contract(self):
        result, _, _ = self.run_ctest_gate('--portable', 'success')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('ConnectionPolicyTest', result.stdout)
        self.assertIn('ConnectionPolicy.java', result.stdout)

    def test_fixture_runner_rejects_empty_ctest_selection(self):
        result, calls, ctest_calls = self.run_ctest_gate('--fixture')
        self.assertIn('No tests were found', result.stdout + result.stderr)
        self.assertEqual(len(ctest_calls), 1, 'Stop before starting the second RMW')
        self.assertNotEqual(result.returncode, 0, 'An empty fixture label must fail')
        self.assert_fixture_failure_cleanup(calls)

    def test_fixture_runner_stops_before_ctest_when_manifest_copy_fails(self):
        result, calls, ctest_calls = self.run_ctest_gate('--fixture', 'missing-manifest')
        self.assertIn('CTestTestfile.cmake', result.stderr)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(ctest_calls, [], 'Failed setup must not reach CTest')
        self.assertFalse(any(call[0] == 'exec' and call[2] == 'cat' for call in calls))
        self.assert_fixture_failure_cleanup(calls)

    def test_fixture_runner_stops_before_ctest_when_tmpfs_setup_fails(self):
        result, calls, ctest_calls = self.run_ctest_gate('--fixture', 'mkdir-failure')
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn('mkdir:', result.stderr)
        self.assertEqual(ctest_calls, [])
        self.assertFalse(any(call[0] == 'exec' and call[2] == 'cat' for call in calls))
        self.assert_fixture_failure_cleanup(calls)

    def test_fixture_runner_propagates_exec_error_then_cleans_up(self):
        result, calls, ctest_calls = self.run_ctest_gate('--fixture', 'exec-failure')
        self.assertEqual(result.returncode, 77, result.stderr)
        self.assertEqual(ctest_calls, [])
        self.assertEqual(sum(call[0] == 'logs' for call in calls), 1)
        self.assertFalse(any(call[0] == 'exec' and call[2] == 'cat' for call in calls))
        self.assert_fixture_failure_cleanup(calls)

    def test_fixture_runner_propagates_real_ctest_failure_then_cleans_up(self):
        result, calls, ctest_calls = self.run_ctest_gate('--fixture', 'ctest-failure')
        self.assertEqual(result.returncode, 8, result.stderr)
        self.assertIn('0% tests passed, 1 tests failed', result.stdout)
        self.assertEqual(len(ctest_calls), 1)
        self.assertEqual(sum(call[0] == 'logs' for call in calls), 1)
        self.assertFalse(any(call[0] == 'exec' and call[2] == 'cat' for call in calls))
        self.assert_fixture_failure_cleanup(calls)

    def test_fixture_runner_propagates_report_error_then_cleans_up(self):
        result, calls, ctest_calls = self.run_ctest_gate('--fixture', 'report-failure')
        self.assertEqual(result.returncode, 78, result.stderr)
        self.assertIn('100% tests passed', result.stdout)
        self.assertEqual(len(ctest_calls), 2)
        self.assertEqual(sum(call[0] == 'exec' and call[2] == 'cat' for call in calls), 1)
        self.assert_fixture_failure_cleanup(calls)

    def test_portable_runner_propagates_real_ctest_failure(self):
        result, _, ctest_calls = self.run_ctest_gate('--portable', 'ctest-failure')
        self.assertEqual(result.returncode, 8, result.stderr)
        self.assertIn('0% tests passed, 1 tests failed', result.stdout)
        self.assertEqual(len(ctest_calls), 1)

    def test_runner_accepts_real_passing_ctest_and_reads_fixture_reports(self):
        for mode, expected in (('--portable', 1), ('--fixture', 4)):
            with self.subTest(mode=mode):
                result, calls, ctest_calls = self.run_ctest_gate(mode, 'success')
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(len(ctest_calls), expected)
                self.assertEqual(result.stdout.count('100% tests passed'), expected)
                if mode == '--fixture':
                    runs = [call for call in calls if call[0] == 'run']
                    names = [call[call.index('--name') + 1] for call in runs]
                    self.assertEqual(len(set(names)), 2)
                    self.assertEqual([call for call in calls if call[0] == 'rm'],
                                     [['rm', '-f', name] for name in names])
                    self.assertEqual(sum(call[0] == 'exec' and call[2] == 'cat'
                                         for call in calls), 4)

    def assert_fixture_failure_cleanup(self, calls):
        runs = [call for call in calls if call[0] == 'run']
        self.assertEqual(len(runs), 1, 'Do not continue to the second RMW on failure')
        name = runs[0][runs[0].index('--name') + 1]
        self.assertTrue(name.startswith('ros2-pocket-ci-'))
        self.assertEqual([call for call in calls if call[0] == 'rm'], [['rm', '-f', name]])
        self.assertEqual(calls[-1], ['rm', '-f', name], 'Cleanup must run last')

    def run_ctest_gate(self, mode, scenario='empty'):
        """Stub build/Docker transport only; run the actual inner sh and CTest."""
        real_ctest = shutil.which('ctest')
        self.assertIsNotNone(real_ctest, 'Behavioral gate tests require real CTest')
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for path in ('tools', 'tests', 'bin', 'host/bin', 'build/native-host',
                         'container-tmp'):
                (root / path).mkdir(parents=True)
            shutil.copy(ROOT / 'tools/test.sh', root / 'tools/test.sh')
            if scenario == 'mkdir-failure':
                (root / 'container-tmp/native').write_text('not a directory')
            (root / 'tests/fixture.Dockerfile').write_text('test recipe')
            (root / 'tests/fixture.py').write_text('test publisher')
            (root / 'tests/fixture-loopback.xml').write_text('test discovery')
            # Register a real test, but not under either selected label by default.
            label = 'unselected' if scenario == 'empty' else 'portable;fixture'
            command = '/bin/false' if scenario == 'ctest-failure' else '/bin/true'
            if scenario != 'missing-manifest':
                if scenario == 'empty':
                    entries = f'add_test(unselected "{command}")\nset_tests_properties(unselected PROPERTIES LABELS "{label}")\n'
                elif mode == '--fixture':
                    entries = (f'add_test(rosout_jazzy_interop "{command}")\n'
                               f'set_tests_properties(rosout_jazzy_interop PROPERTIES LABELS "{label}")\n'
                               f'add_test(probe "{command}")\n'
                               f'set_tests_properties(probe PROPERTIES LABELS "{label}")\n')
                else:
                    entries = f'add_test(probe "{command}")\nset_tests_properties(probe PROPERTIES LABELS "{label}")\n'
                (root / 'build/native-host/CTestTestfile.cmake').write_text(entries)
            for command in ('cmake', 'python3', 'javac', 'java'):
                path = root / 'bin' / command
                path.write_text('#!/bin/sh\nprintf "%s\\n" "$*"\nexit 0\n'
                                if command in ('javac', 'java') else '#!/bin/sh\nexit 0\n')
                path.chmod(0o755)
            idlc = root / 'host/bin/idlc'
            idlc.write_text('#!/bin/sh\nexit 0\n')
            idlc.chmod(0o755)
            ctest = root / 'bin/ctest'
            ctest.write_text(f'#!{sys.executable}\n' + '''import json, os, sys
with open(os.environ['CTEST_CALLS'], 'a') as log:
    log.write(json.dumps(sys.argv[1:]) + '\\n')
os.execv(os.environ['REAL_CTEST'], [os.environ['REAL_CTEST'], *sys.argv[1:]])
''')
            ctest.chmod(0o755)
            docker = root / 'bin/docker'
            docker.write_text(f'#!{sys.executable}\n' + '''import json, os, subprocess, sys
args = sys.argv[1:]
with open(os.environ['DOCKER_CALLS'], 'a') as log:
    log.write(json.dumps(args) + '\\n')
if args[0] == 'exec':
    command = args[2:]
    if command[0] == 'sh':
        if os.environ['GATE_SCENARIO'] == 'exec-failure':
            sys.exit(77)
        # Only relocate the container tmpfs; preserve/execute the runner's shell.
        command = [arg.replace('/tmp/native', os.environ['CONTAINER_TMP'] + '/native')
                   .replace('/tmp/fixture.xml', os.environ['CONTAINER_TMP'] + '/fixture.xml')
                   .replace('/tmp/rosout.xml', os.environ['CONTAINER_TMP'] + '/rosout.xml')
                   for arg in command]
    else:
        if os.environ['GATE_SCENARIO'] == 'report-failure':
            sys.exit(78)
        command = [arg.replace('/tmp/native', os.environ['CONTAINER_TMP'] + '/native')
                   .replace('/tmp/fixture.xml', os.environ['CONTAINER_TMP'] + '/fixture.xml')
                   .replace('/tmp/rosout.xml', os.environ['CONTAINER_TMP'] + '/rosout.xml')
                   for arg in command]
    sys.exit(subprocess.run(command).returncode)
''')
            docker.chmod(0o755)
            env = dict(os.environ, JAVA_HOME=str(root), POCKET_TOOLCHAINS=str(root),
                       TMPDIR=str(root / 'tmp'),
                       PATH=str(root / 'bin') + ':' + os.environ['PATH'],
                       DOCKER_CALLS=str(root / 'docker-calls'),
                       CTEST_CALLS=str(root / 'ctest-calls'), REAL_CTEST=real_ctest,
                       CONTAINER_TMP=str(root / 'container-tmp'),
                       GATE_SCENARIO=scenario)
            result = subprocess.run(['bash', str(root / 'tools/test.sh'), mode],
                                    env=env, text=True, capture_output=True, timeout=15)
            def calls(path):
                return [json.loads(line) for line in path.read_text().splitlines()] \
                    if path.exists() else []
            return result, calls(root / 'docker-calls'), calls(root / 'ctest-calls')

    def check_build_mode(self, args, offline):
        import shutil
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'tools').mkdir()
            shutil.copy(ROOT / 'tools/build.sh', root / 'tools/build.sh')
            (root / 'bin').mkdir()
            for command in ('cmake', 'c++', 'javac', 'java'):
                path = root / 'bin' / command
                path.write_text('#!/bin/sh\nexit 0\n')
                path.chmod(0o755)
            tc = root / 'toolchains'
            (tc / 'host/bin').mkdir(parents=True)
            (tc / 'host/bin/idlc').write_text('#!/bin/sh\nexit 0\n')
            (tc / 'host/bin/idlc').chmod(0o755)
            (tc / 'android/lib').mkdir(parents=True)
            (tc / 'android/lib/libddsc.so').touch()
            (root / 'build/native-android').mkdir(parents=True)
            (root / 'build/native-android/librospocket.so').touch()
            # Stub only external build tools; exercise the real orchestration.
            (root / 'gradlew').write_text('#!/bin/sh\nprintf "%s\\n" "$@" > gradle-args\n')
            (root / 'gradlew').chmod(0o755)
            (root / 'tools/test.sh').write_text('#!/bin/sh\nexit 0\n')
            (root / 'tools/artifact_metadata.py').write_text('')
            distribution = root / 'gradle-home/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu'
            (distribution / 'gradle-8.7/bin').mkdir(parents=True)
            (distribution / 'gradle-8.7-bin.zip.ok').touch()
            executable = distribution / 'gradle-8.7/bin/gradle'
            executable.touch()
            executable.chmod(0o755)
            env = dict(os.environ, JAVA_HOME=str(root / 'bin/..'),
                       GRADLE_USER_HOME=str(root / 'gradle-home'),
                       PATH=str(root / 'bin') + ':' + os.environ['PATH'],
                       POCKET_TOOLCHAINS=str(tc))
            result = subprocess.run(['bash', str(root / 'tools/build.sh'), *args],
                                    env=env, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            flags = (root / 'gradle-args').read_text().splitlines()
            self.assertEqual('--offline' in flags, offline)
            self.assertIn(':app:lintDebug', flags)
            self.assertIn(':app:testDebugUnitTest', flags)
            self.assertIn(':app:assembleDebug', flags)

if __name__ == '__main__':
    unittest.main()
