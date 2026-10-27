"""Synthetic host controls for front 13; NEVER Android/physical evidence."""
import unittest
import device_multiwatch_fixture as fixture


class FixtureProfileTests(unittest.TestCase):
    def test_owned_profiles_are_four_distinct_sequenced_rates_and_mixed_qos(self):
        profiles = fixture.profiles('a' * 32)
        self.assertEqual([p['rateHz'] for p in profiles], [1, 2, 5, 10])
        self.assertEqual([p['reliable'] for p in profiles], [False, True, False, True])
        self.assertEqual([p['transientLocal'] for p in profiles], [False, False, False, True])
        self.assertEqual(len({p['topic'] for p in profiles}), 4)
        self.assertTrue(all('a' * 32 in p['topic'] for p in profiles))
        import re
        for p in fixture.profiles('1'*32):
            self.assertTrue(all(re.fullmatch('[A-Za-z_][A-Za-z_0-9]*',token) for token in p['topic'].split('/')[1:]))

    def test_bad_owner_rejected_without_importing_ros(self):
        for owner in ('', 'robot', '../x', 'A' * 32, 'a' * 31):
            with self.subTest(owner=owner), self.assertRaises(ValueError):
                fixture.profiles(owner)


class IsolationTests(unittest.TestCase):
    def test_survivors_must_advance_and_preserve_identity_and_new_logs(self):
        before = dict(sessionGeneration=1, watches=[dict(slot=i, generation=i+1, count=5,
            readerActive=True, error='') for i in range(4)],
            rosout=dict(active=True, error='', entries=[dict(logger='mw13.'+'a'*32, line=1)]))
        import copy
        after = copy.deepcopy(before)
        for w in after['watches']: w['count'] += 1
        after['rosout']['entries'][0]['line'] = 2
        fixture.verify_survivors(before, after, (0, 1, 3), 'a'*32)
        for mutation in ('stalled', 'generation', 'session', 'logs'):
            bad = copy.deepcopy(after)
            if mutation == 'stalled': bad['watches'][1]['count'] = 5
            if mutation == 'generation': bad['watches'][1]['generation'] += 1
            if mutation == 'session': bad['sessionGeneration'] += 1
            if mutation == 'logs': bad['rosout']['entries'][0]['line'] = 1
            with self.subTest(mutation=mutation), self.assertRaises(AssertionError):
                fixture.verify_survivors(before, bad, (0, 1, 3), 'a'*32)


class NegativeWindowTests(unittest.TestCase):
    def test_window_requires_actual_contiguous_publications_and_a_later_capture(self):
        import copy
        owner = 'a'*32
        p = fixture.profiles(owner)[0]
        def observation(t):
            w = dict(slot=0, generation=2, count=0, matched=0, readerActive=True,
                     fresh=False, age=-1, hz=0, last='', error='', subscription=p['topic'],
                     qos='reliable / volatile', subscriptionState='waiting')
            return dict(capturedNs=t, exportedNs=t+1,
                        snapshot=dict(sessionGeneration=1, watches=[w, {}, {}, {}]))
        observations = [observation(t) for t in (1, 2_000_000_001, 6_000_000_001)]
        events = [dict(owner=owner, topic=p['topic'], sequence=i+1,
                       monotonicNs=i*1_000_000_000+10, writerGid='ab'*16,
                       reliability='best_effort') for i in range(6)]
        endpoint = dict(id='ab'*16, writer=True, nodes=['/multiwatch13_'+owner], qos=dict(reliability='best_effort'))
        for o in observations:
            o['snapshot']['topics'] = [dict(wireName='rt'+p['topic'], endpoints=[endpoint])]
        fixture.verify_negative(events, observations, owner, 'ab'*16, 5_500_000_000)
        for defect in ('gap', 'missing', 'foreign', 'intake', 'reader', 'fence', 'bool', 'endpoint', 'node'):
            ee, oo, fence = copy.deepcopy(events), copy.deepcopy(observations), 5_500_000_000
            if defect == 'endpoint': oo[-1]['snapshot']['topics'][0]['endpoints'][0]['id'] = 'cd'*16
            if defect == 'node': oo[-1]['snapshot']['topics'][0]['endpoints'][0]['nodes'] = ['/foreign']
            if defect == 'gap': ee[3]['monotonicNs'] += 800_000_000
            if defect == 'missing': del ee[3]
            if defect == 'foreign': ee[3]['writerGid'] = 'cd'*16
            if defect == 'intake': oo[-1]['snapshot']['watches'][0]['count'] = 1
            if defect == 'reader': oo[-1]['snapshot']['watches'][0]['generation'] += 1
            if defect == 'fence': fence = oo[-1]['capturedNs']
            if defect == 'bool': oo[0]['snapshot']['watches'][0]['count'] = False
            with self.subTest(defect=defect), self.assertRaises(AssertionError):
                fixture.verify_negative(ee, oo, owner, 'ab'*16, fence)


class ContainerCleanupTests(unittest.TestCase):
    def test_daemon_failure_is_not_absence_but_exact_missing_object_is(self):
        import run_multiwatch13 as runner
        from types import SimpleNamespace
        cid = 'ab'*32
        runner.verify_removed(SimpleNamespace(returncode=1, stdout='[]\n', stderr='error: no such object: '+cid+'\n'), cid)
        for code,err in ((0,''), (1,'Cannot connect to the Docker daemon'), (1,'error: no such object: other')):
            with self.subTest(code=code,err=err), self.assertRaises(RuntimeError):
                runner.verify_removed(SimpleNamespace(returncode=code,stdout='[]',stderr=err),cid)

    def _run_with_synthetic_docker(self, failure=None, foreign=False):
        import json
        import tempfile
        from pathlib import Path
        from types import SimpleNamespace
        from unittest import mock
        import run_multiwatch13 as runner

        cid = 'ab' * 32
        class SyntheticDocker:
            def __init__(self):
                self.calls = []
                self.owner = None

            def __call__(self, *args, timeout=20, check=True):
                self.calls.append((args, timeout, check))
                command = args[0]
                if args[:2] == ('image', 'inspect'):
                    return SimpleNamespace(returncode=0, stdout='[{"Id":"sha256:image"}]', stderr='')
                if command == 'create':
                    label = args[args.index('--label') + 1]
                    self.owner = label.split('=', 1)[1]
                    return SimpleNamespace(returncode=0, stdout=cid + '\n', stderr='')
                if command == 'inspect' and check is False:
                    return SimpleNamespace(returncode=1, stdout='', stderr='error: no such object: '+cid+'\n')
                if command == 'inspect':
                    label = 'foreign' if foreign else self.owner
                    item = {'Id': cid, 'Config': {'Labels': {'io.lamggm.multiwatch13': label}},
                            'HostConfig': {'NetworkMode': 'none'}, 'State': {'Status': 'exited'}}
                    return SimpleNamespace(returncode=0, stdout=json.dumps([item]), stderr='')
                if command == 'logs' and failure == 'read':
                    raise OSError('synthetic log read failure')
                if command == 'logs':
                    return SimpleNamespace(returncode=0,
                                           stdout='MULTIWATCH13_RESULT {"cases": []}\n', stderr='')
                if command == 'wait':
                    return SimpleNamespace(returncode=0, stdout='0\n', stderr='')
                return SimpleNamespace(returncode=0, stdout='', stderr='')

        docker = SyntheticDocker()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            binary = root / 'binary'
            binary.write_text('synthetic', encoding='utf-8')
            output = root / 'output'
            output.mkdir()
            original_write_text = Path.write_text

            def write_text(path, data, *args, **kwargs):
                if failure == 'write' and path.suffix == '.log':
                    raise OSError('synthetic log write failure')
                return original_write_text(path, data, *args, **kwargs)

            with mock.patch.object(runner, 'docker', docker), mock.patch.object(Path, 'write_text', write_text):
                with self.assertRaises(OSError if failure else RuntimeError):
                    runner.run('local:image', binary, output, 'rmw_cyclonedds_cpp')
        return docker

    def test_log_read_failure_still_removes_and_reads_back_owned_container(self):
        docker = self._run_with_synthetic_docker(failure='read')
        self.assertIn((('rm', '-f', 'ab'*32), 20, True), docker.calls)
        self.assertIn((('inspect', 'ab'*32), 20, False), docker.calls)

    def test_log_write_failure_still_removes_and_reads_back_owned_container(self):
        docker = self._run_with_synthetic_docker(failure='write')
        self.assertIn((('rm', '-f', 'ab'*32), 20, True), docker.calls)
        self.assertIn((('inspect', 'ab'*32), 20, False), docker.calls)

    def test_foreign_container_is_never_removed(self):
        docker = self._run_with_synthetic_docker(foreign=True)
        self.assertNotIn((('rm', '-f', 'ab'*32), 20, True), docker.calls)


if __name__ == '__main__':
    unittest.main()
