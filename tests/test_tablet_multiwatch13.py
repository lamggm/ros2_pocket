"""Front-13 public CLI controls, injected HOST transports, not hardware."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import tablet_multiwatch_acceptance as harness


class PublicHarnessTests(unittest.TestCase):
    def args(self, directory):
        return ['--serial','HOST_ONLY','--owner','a'*32,'--domain','67','--peer','',
                '--fixture-container','ab'*32,'--apk-sha256','cd'*32,'--test-apk-sha256','ef'*32,
                '--fixture-source',str(Path(harness.__file__).with_name('device_multiwatch_fixture.py').resolve()),
                '--fixture-destination','/fixture/device_multiwatch_fixture.py',
                '--fixture-script-sha256','01'*32,'--fixture-image-id','sha256:'+'02'*32,
                '--fixture-rmw','rmw_cyclonedds_cpp',
                '--output',directory]

    def test_no_device_cli_fails_closed_before_adb_or_ros(self):
        env = dict(os.environ)
        env.pop('POCKET_TABLET',None)
        result = subprocess.run(['python3',str(Path(harness.__file__))],env=env,capture_output=True,text=True)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('--serial',result.stderr)
        self.assertNotIn('Traceback',result.stderr)

    def test_optimized_cli_is_rejected_instead_of_disabling_oracles(self):
        result=subprocess.run(['python3','-O',str(Path(harness.__file__))],capture_output=True,text=True)
        self.assertNotEqual(result.returncode,0)
        self.assertIn('optimization',result.stderr)

    def test_invalid_inputs_never_create_a_transport(self):
        from unittest.mock import Mock
        with tempfile.TemporaryDirectory() as directory:
            for flag,value in [('--owner','robot'),('--peer','192.0.2.999'),('--domain','0'),
                               ('--serial','x;id'),('--fixture-container','name'),('--apk-sha256','00'),
                               ('--fixture-source','/tmp/wrong.py'),('--fixture-destination','/tmp/wrong.py'),
                               ('--fixture-script-sha256','00'),('--fixture-image-id','latest'),
                               ('--fixture-rmw','rmw_bad')]:
                args=self.args(directory);args[args.index(flag)+1]=value
                factory=Mock()
                with self.subTest(flag=flag),self.assertRaises((ValueError,SystemExit)):
                    harness.main(args,transport_factory=factory)
                factory.assert_not_called()

    def test_public_entrypoint_marks_injected_success_host_and_requires_every_case(self):
        class HostTransport:
            kind='synthetic-host-transport-not-android'
            def __init__(self,config,omit=False): self.config,self.omit=config,omit
            def verify(self): pass
            def instrument(self,phase):
                phase(dict(phase='negative',writerGid='ab'*16))
                cases=sorted(harness.REQUIRED_CASES)
                if self.omit: cases.remove('fifth_rejected')
                return dict(schemaVersion=1,owner='a'*32,cases=cases,passed=True)
            def negative_window(self,status):
                return dict(publicationSpanNs=6_000_000_000,qaCaptureSpanNs=6_000_000_000,
                            historyFenceNs=10,closingCaptureNs=11,writerGid=status['writerGid'])
        with tempfile.TemporaryDirectory() as directory:
            result=harness.main(self.args(directory),transport_factory=HostTransport)
            self.assertEqual(result['classification'],'synthetic-host-transport-not-android')
            self.assertFalse(result['physicalAcceptance'])
            self.assertTrue(result['softwareFlowComplete'])
            HostTransport.kind='adb-instrumentation-unaccepted-physical-campaign'
            injected=harness.main(self.args(directory),transport_factory=HostTransport)
            self.assertEqual(injected['classification'],'synthetic-host-transport-not-android')
            with self.assertRaisesRegex(RuntimeError,'cases'):
                harness.main(self.args(directory),transport_factory=lambda c:HostTransport(c,True))


class PhysicalWindowHostControls(unittest.TestCase):
    def test_real_device_transport_window_rejects_intake_during_slow_history_read(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        for late_intake in (False,True):
            now=[1_000_000_000];receiving=[False]
            class Clock:
                @staticmethod
                def monotonic(): return now[0]/1e9
                @staticmethod
                def sleep(seconds): now[0]+=int(seconds*1e9)
            def qa():
                w=dict(slot=0,valid=True,pending=False,requestedReliable=True,requestedTransientLocal=False,
                       requestVersion=3,appliedVersion=3,readerGeneration=4,readerActive=True,
                       count=int(receiving[0]),matched=0,state='waiting',hasError=False,nativeFresh=False,
                       freshAtExport=False,receivedHz=0.0,sampleAgeAtCaptureSeconds=-1)
                return dict(schemaVersion=1,valid=True,foreground=True,debuggable=True,applicationId=harness.PKG,
                            clock='java.nanoTime',pid=100,lifecycleGeneration=1,policyGeneration=2,
                            sessionGeneration=3,capturedMonotonicNs=now[0],exportedMonotonicNs=now[0]+100,
                            captureAgeNs=100,watches=[w,{}, {}, {}])
            def history():
                if late_intake and now[0]>=6_000_000_000:
                    now[0]+=2_000_000_000;receiving[0]=True
                return [dict(owner='a'*32,topic=harness.profiles('a'*32)[0]['topic'],
                    sequence=i,monotonicNs=i*1_000_000_000,writerGid='ab'*16,reliability='best_effort')
                    for i in range(1,int(now[0]/1e9)+1)]
            with tempfile.TemporaryDirectory() as directory,patch.object(harness,'time',Clock):
                transport=object.__new__(harness.DeviceTransport)
                transport.c=SimpleNamespace(owner='a'*32,run_dir=Path(directory))
                transport.client=SimpleNamespace(qa=qa)
                transport._events=history
                if late_intake:
                    with self.assertRaises(AssertionError): transport.negative_window(dict(writerGid='ab'*16))
                else:
                    proof=transport.negative_window(dict(writerGid='ab'*16))
                    self.assertGreater(proof['closingCaptureNs'],proof['historyFenceNs'])
                    self.assertGreaterEqual(proof['publicationSpanNs'],5_000_000_000)


class InstrumentationStreamHostControls(unittest.TestCase):
    def test_actual_stream_parser_accepts_only_complete_success_with_phase(self):
        from types import SimpleNamespace
        for code in (-1,0):
            with tempfile.TemporaryDirectory() as directory:
                path=Path(directory)
                report=dict(schemaVersion=1,owner='a'*32,cases=sorted(harness.REQUIRED_CASES),passed=True)
                lines=['INSTRUMENTATION_STATUS: multiwatch13_phase=negative',
                       'INSTRUMENTATION_STATUS: writerGid='+'ab'*16,'INSTRUMENTATION_STATUS_CODE: 2',
                       'INSTRUMENTATION_RESULT: multiwatch13_result='+json.dumps(report),
                       'INSTRUMENTATION_CODE: '+str(code)]
                fake=path/'synthetic_adb_NOT_android'
                fake.write_text('#!/usr/bin/env python3\nimport sys\nassert all(sys.argv[1:]), "empty adb argument cannot survive remote shell"\nprint('+repr('\n'.join(lines))+', flush=True)\n')
                fake.chmod(0o700)
                t=object.__new__(harness.DeviceTransport)
                t.adb=str(fake)
                t.c=SimpleNamespace(serial='HOST_ONLY',owner='a'*32,domain=67,peer='',run_dir=path)
                phases=[]
                if code==-1:
                    self.assertEqual(t.instrument(phases.append),report)
                    self.assertEqual(phases,[dict(phase='negative',writerGid='ab'*16)])
                else:
                    with self.assertRaisesRegex(RuntimeError,'incomplete'): t.instrument(phases.append)


class InstrumentationCleanupHostControls(unittest.TestCase):
    def test_incomplete_runner_cleans_up_after_exit_and_closes_stream_when_transcript_save_fails(self):
        from types import SimpleNamespace
        from unittest.mock import patch

        class Stream:
            def __init__(self): self.closed = False
            def fileno(self): return 99
            def close(self): self.closed = True

        class Process:
            def __init__(self): self.stdout = Stream(); self.returncode = 1
            def poll(self): return self.returncode
            def wait(self, timeout): return self.returncode

        class Selector:
            def __enter__(self): return self
            def __exit__(self, *unused): pass
            def register(self, *unused): pass
            def select(self, timeout): return []

        process = Process()
        calls = []
        def adb(*args):
            calls.append(args)
            if args[1:3] == ('am','force-stop'):
                raise RuntimeError('synthetic force-stop failure')
            return ''
        client = SimpleNamespace(adb=adb)
        t = object.__new__(harness.DeviceTransport)
        t.adb = 'synthetic-adb-not-android'
        t.client = client
        t.c = SimpleNamespace(serial='HOST_ONLY', owner='a'*32, domain=67, peer='', run_dir=Path(tempfile.mkdtemp()))
        with patch.object(harness.subprocess, 'Popen', return_value=process), \
             patch.object(harness.selectors, 'DefaultSelector', return_value=Selector()), \
             patch.object(Path, 'write_bytes', side_effect=OSError('synthetic transcript failure')):
            with self.assertRaisesRegex(RuntimeError, 'runner failed/incomplete'):
                t.instrument(lambda unused: None)
        self.assertTrue(process.stdout.closed)
        self.assertIn(('shell','am','force-stop','--user','0',harness.PKG), calls)
        self.assertIn(('shell','pidof',harness.PKG), calls)


class FixtureProvenanceHostControls(unittest.TestCase):
    def test_events_require_exact_mount_hash_command_image_and_rmw(self):
        from types import SimpleNamespace
        from unittest.mock import patch

        owner = 'a'*32
        destination = '/fixture/device_multiwatch_fixture.py'
        image_id = 'sha256:'+'02'*32
        script_hash = '01'*64
        source = str(Path(harness.__file__).with_name('device_multiwatch_fixture.py').resolve())
        event = dict(owner=owner, topic=harness.profiles(owner)[0]['topic'], sequence=1,
                     monotonicNs=1, writerGid='ab'*16, reliability='best_effort')
        metadata = dict(Id='ab'*32, Image=image_id,
            State=dict(Running=True),
            Config=dict(Labels={'io.lamggm.multiwatch13':owner},
                         Env=['ROS_DOMAIN_ID=67','RMW_IMPLEMENTATION=rmw_cyclonedds_cpp'],
                         Entrypoint=[],
                         Cmd=['python3',destination,'--publish','--owner',owner,
                              '--domain','67','--seconds','240']),
            Mounts=[dict(Type='bind',Source=source,Destination=destination,RW=False,Mode='ro')])
        config = SimpleNamespace(fixture_container=metadata['Id'], owner=owner, domain=67,
                                 fixture_source=source, fixture_destination=destination,
                                 fixture_script_sha256=script_hash, fixture_image_id=image_id,
                                 fixture_rmw='rmw_cyclonedds_cpp')
        t = object.__new__(harness.DeviceTransport)
        t.c = config

        def run(args, **kwargs):
            if args[1:3] == ['inspect', metadata['Id']]:
                return SimpleNamespace(stdout=json.dumps([metadata]))
            if args[1:3] == ['exec', metadata['Id']]:
                return SimpleNamespace(stdout=script_hash+'  '+destination+'\n')
            if args[1:3] == ['logs', '--tail']:
                return SimpleNamespace(stdout='MULTIWATCH13_EVENT '+json.dumps(event)+'\n')
            raise AssertionError(args)

        with patch.object(harness.subprocess, 'run', side_effect=run):
            self.assertEqual(t._events(), [event])
            t.c.fixture_script_sha256 = '03'*32
            with self.assertRaisesRegex(RuntimeError, 'hash'):
                t._events()
            t.c.fixture_script_sha256 = script_hash
            for mutation in (
                lambda m: m['Mounts'][0].update(Destination='/tmp/wrong.py'),
                lambda m: m['Mounts'][0].update(RW=True),
                lambda m: m.update(Image='sha256:'+'03'*32),
                lambda m: m['Config'].update(Cmd=m['Config']['Cmd'][:-1]+['239']),
                lambda m: m['Config']['Env'].__setitem__(1, 'RMW_IMPLEMENTATION=rmw_fastrtps_cpp'),
            ):
                bad = json.loads(json.dumps(metadata)); mutation(bad)
                with self.subTest(mutation=mutation), patch.object(harness.subprocess, 'run', side_effect=lambda args, **kwargs: (
                    SimpleNamespace(stdout=json.dumps([bad])) if args[1:3] == ['inspect', metadata['Id']] else
                    SimpleNamespace(stdout=script_hash+'  '+destination+'\n') if args[1:3] == ['exec', metadata['Id']] else
                    SimpleNamespace(stdout='MULTIWATCH13_EVENT '+json.dumps(event)+'\n'))):
                    with self.assertRaisesRegex(RuntimeError, 'provenance'):
                        t._events()


if __name__=='__main__': unittest.main()
