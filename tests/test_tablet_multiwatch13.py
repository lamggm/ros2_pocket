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
                               ('--serial','x;id'),('--fixture-container','name'),('--apk-sha256','00')]:
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


if __name__=='__main__': unittest.main()
