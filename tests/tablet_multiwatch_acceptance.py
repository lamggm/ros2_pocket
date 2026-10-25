#!/usr/bin/env python3
"""Future physical front-13 campaign. NEVER installs, creates publishers or approves a release.

Requires an explicitly controlled publisher container and already installed, hash-bound
DEV/test APKs. No default serial; imports tablet_ui only after validating all inputs.
Injected transports are HOST tests, never physical evidence.
"""
import argparse
import ipaddress
import json
import os
from pathlib import Path
import re
import selectors
import subprocess
import time
import uuid
from device_multiwatch_fixture import profiles

if not __debug__:
    raise SystemExit('Python optimization disables qualification oracles; refused')

PKG='com.lamggm.rospocket.dev'
RUNNER='com.lamggm.rospocket.MultiwatchInstrumentation'
COMPONENT=PKG+'.test/'+RUNNER
REQUIRED_CASES={'mixed_rates','fifth_rejected','negative_qos','qos_recovery',
    'topic_failure_0','reader_failure_0','topic_failure_2','reader_failure_2',
    'same_topic','remove_same_topic','recreate','rosout_new'}


def validate(config):
    profiles(config.owner)
    if not re.fullmatch(r'[A-Za-z0-9._:\-]{1,160}',config.serial): raise ValueError('invalid serial')
    if config.domain not in (67,68): raise ValueError('reserved explicit domain required')
    if config.peer and str(ipaddress.IPv4Address(config.peer))!=config.peer: raise ValueError('canonical IPv4 required')
    for field in ('fixture_container','apk_sha256','test_apk_sha256'):
        if not re.fullmatch('[0-9a-f]{64}',getattr(config,field)): raise ValueError('invalid '+field)
    if not config.output.is_dir(): raise ValueError('existing private output directory required')


def qa_identity(qa):
    """Zero-intake identity on Java's clock; no host/Android clock comparison."""
    integer=lambda v: type(v) is int and 0<=v<2**63
    assert qa['schemaVersion']==1 and type(qa['schemaVersion']) is int
    assert qa['valid'] is True and qa['foreground'] is True and qa['debuggable'] is True
    assert qa['applicationId']==PKG and qa['clock']=='java.nanoTime'
    for k in ('pid','lifecycleGeneration','policyGeneration','sessionGeneration','capturedMonotonicNs','exportedMonotonicNs','captureAgeNs'):
        assert integer(qa[k]),k
    assert 0<=qa['captureAgeNs']<=2_000_000_000
    assert qa['capturedMonotonicNs']<=qa['exportedMonotonicNs']
    assert len(qa['watches'])==4
    w=qa['watches'][0]
    for k in ('slot','count','matched','requestVersion','appliedVersion','readerGeneration'):
        assert integer(w[k]),k
    assert w['slot']==0 and w['valid'] is True and w['pending'] is False
    assert w['requestedReliable'] is True and w['requestedTransientLocal'] is False
    assert w['requestVersion']==w['appliedVersion'] and w['readerActive'] is True
    assert w['count']==w['matched']==0 and w['state']=='waiting' and w['hasError'] is False
    assert w['nativeFresh'] is False and w['freshAtExport'] is False
    assert type(w['receivedHz']) in (float,int) and w['receivedHz']==0
    assert type(w['sampleAgeAtCaptureSeconds']) in (float,int) and w['sampleAgeAtCaptureSeconds']==-1
    return tuple(qa[k] for k in ('pid','lifecycleGeneration','policyGeneration','sessionGeneration'))+(w['requestVersion'],w['readerGeneration'])


class DeviceTransport:
    kind='adb-instrumentation-unaccepted-physical-campaign'
    def __init__(self,config):
        # tablet_ui has a legacy import-time serial guard; set only after validation.
        os.environ['POCKET_TABLET']=config.serial
        import tablet_ui
        self.c=config
        self.client=tablet_ui.Client(config.serial,out=config.run_dir)
        self.adb=tablet_ui.ADB

    def verify(self):
        self.client.verify()
        for package,expected in ((PKG,self.c.apk_sha256),(PKG+'.test',self.c.test_apk_sha256)):
            paths=self.client.adb('shell','pm','path','--user','0',package).splitlines()
            if len(paths)!=1 or not re.fullmatch(r'package:/data/app/[A-Za-z0-9_./+=\-]+\.apk',paths[0]):
                raise RuntimeError('exact single installed APK required')
            path=paths[0][8:]
            value=self.client.adb('shell','sha256sum',path).strip()
            if value!=expected+'  '+path: raise RuntimeError('installed APK hash mismatch: '+package)
        listing=self.client.adb('shell','pm','list','instrumentation').splitlines()
        if 'instrumentation:'+COMPONENT+' (target='+PKG+')' not in listing:
            raise RuntimeError('packaged multiwatch runner is not registered')
        self._events()  # Ownership and publisher protocol before starting the runner.

    def _events(self):
        def docker(*args):
            return subprocess.run(['docker',*args],capture_output=True,text=True,check=True,timeout=5).stdout
        metadata=json.loads(docker('inspect',self.c.fixture_container))[0]
        expected=str(Path(__file__).with_name('device_multiwatch_fixture.py').resolve())
        if (metadata['Id']!=self.c.fixture_container or metadata['State']['Running'] is not True
                or metadata['Config']['Labels'].get('io.lamggm.multiwatch13')!=self.c.owner
                or not any(m['Source']==expected for m in metadata['Mounts'])
                or 'ROS_DOMAIN_ID='+str(self.c.domain) not in metadata['Config']['Env']):
            raise RuntimeError('publisher container ownership/mount/domain unavailable')
        raw=docker('logs','--tail','1024',self.c.fixture_container)
        if len(raw.encode())>1_048_576: raise RuntimeError('publisher history bound')
        events=[json.loads(line.split(' ',1)[1]) for line in raw.splitlines() if line.startswith('MULTIWATCH13_EVENT ')]
        if not events: raise RuntimeError('real publication history absent')
        topic=profiles(self.c.owner)[0]['topic']
        return [e for e in events if e['owner']==self.c.owner and e['topic']==topic]

    def negative_window(self,status):
        # First zero QA precedes the chosen future producer sequence window.
        first=self.client.qa();identity=qa_identity(first)
        baseline=self._events()[-1]['sequence'];observations=[first]
        end=time.monotonic()+13
        while time.monotonic()<end:
            time.sleep(.5)
            events=[e for e in self._events() if e['sequence']>baseline]
            q=self.client.qa();assert qa_identity(q)==identity
            assert q['capturedMonotonicNs']>observations[-1]['capturedMonotonicNs'],'replayed QA'
            observations.append(q)
            if len(events)>=6 and events[-1]['monotonicNs']-events[0]['monotonicNs']>=5_000_000_000: break
        else: raise RuntimeError('contiguous publisher window deadline')
        # History frozen BEFORE an export fence; closure must be a newer worker.
        frozen=events
        fence=q['exportedMonotonicNs']
        while time.monotonic()<end:
            time.sleep(.5);q=self.client.qa();assert qa_identity(q)==identity
            observations.append(q)
            if q['capturedMonotonicNs']>fence and q['capturedMonotonicNs']-first['capturedMonotonicNs']>=5_000_000_000: break
        else: raise RuntimeError('post-export QA capture deadline')
        gid=status['writerGid']
        assert re.fullmatch('[0-9a-f]{32}',gid)
        for e in frozen:
            assert e['writerGid']==gid and e['reliability']=='best_effort'
            assert type(e['sequence']) is int and type(e['monotonicNs']) is int
        for a,b in zip(frozen,frozen[1:]):
            assert b['sequence']==a['sequence']+1
            assert 0<b['monotonicNs']-a['monotonicNs']<=1_500_000_000,'publication gap'
        proof=dict(publicationSpanNs=frozen[-1]['monotonicNs']-frozen[0]['monotonicNs'],
                   qaCaptureSpanNs=q['capturedMonotonicNs']-first['capturedMonotonicNs'],
                   historyFenceNs=fence,closingCaptureNs=q['capturedMonotonicNs'],writerGid=gid,
                   observations=observations,publications=frozen)
        (self.c.run_dir/'negative-window.json').write_text(json.dumps(proof,indent=2)+'\n')
        return proof

    def instrument(self,on_phase):
        args=[self.adb,'-s',self.c.serial,'shell','am','instrument','--user','0','-w','-r',
              '-e','owner',self.c.owner,'-e','domain',str(self.c.domain)]
        if self.c.peer: args.extend(['-e','peer',self.c.peer])
        args.append(COMPONENT)
        child=subprocess.Popen(args,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,bufsize=0)
        transcript=bytearray();pending=b'';status={};report=None;code=None;failure=None
        deadline=time.monotonic()+240
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(child.stdout,selectors.EVENT_READ)
                while time.monotonic()<deadline:
                    if not selector.select(.5):
                        if child.poll() is not None: break
                        continue
                    chunk=os.read(child.stdout.fileno(),65536)
                    if not chunk: break
                    transcript.extend(chunk);pending+=chunk
                    if len(transcript)>1_048_576: raise RuntimeError('instrumentation output bound')
                    while b'\n' in pending:
                        line,pending=pending.split(b'\n',1);line=line.decode('utf-8',errors='strict').strip()
                        if line.startswith('INSTRUMENTATION_STATUS: '):
                            key,sep,value=line[24:].partition('=')
                            if sep: status[key]=value
                        if line=='INSTRUMENTATION_STATUS_CODE: 2':
                            try: on_phase(dict(phase=status.get('multiwatch13_phase'),writerGid=status.get('writerGid')))
                            except BaseException as ex: failure=ex
                            status={}
                        if line.startswith('INSTRUMENTATION_RESULT: multiwatch13_result='):
                            report=json.loads(line.split('=',1)[1])
                        if line.startswith('INSTRUMENTATION_CODE: '): code=int(line.split(':',1)[1])
                        if line.startswith('INSTRUMENTATION_RESULT: ') and any(k in line for k in ('failure=','Failure=')):
                            failure=RuntimeError(line)
            child.wait(timeout=5)
            if failure: raise failure
            if child.returncode!=0 or code!=-1 or report is None: raise RuntimeError('runner failed/incomplete')
            return report
        finally:
            (self.c.run_dir/'instrumentation.txt').write_bytes(transcript)
            if child.poll() is None:
                child.kill();child.wait(timeout=5)
                self.client.adb('shell','am','force-stop','--user','0',PKG)
                if self.client.adb('shell','pidof',PKG).strip(): raise RuntimeError('runner abort cleanup unverified')
            child.stdout.close()


def main(argv=None,*,transport_factory=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--owner',required=True)
    parser.add_argument('--domain',type=int,choices=(67,68),required=True)
    parser.add_argument('--peer',default='')
    parser.add_argument('--fixture-container',required=True)
    parser.add_argument('--apk-sha256',required=True)
    parser.add_argument('--test-apk-sha256',required=True)
    parser.add_argument('--output',type=Path,required=True)
    config=parser.parse_args(argv);validate(config)
    config.run_dir=config.output/('multiwatch13-'+uuid.uuid4().hex)
    config.run_dir.mkdir(mode=0o700)
    factory=DeviceTransport if transport_factory is None else transport_factory
    transport=factory(config)
    result=dict(schemaVersion=1,classification=(transport.kind if transport_factory is None else 'synthetic-host-transport-not-android'),owner=config.owner,
                physicalAcceptance=False,softwareFlowComplete=False)
    try:
        transport.verify();proofs=[]
        def on_phase(status):
            if status.get('phase')!='negative' or proofs: raise RuntimeError('unknown/duplicate runner phase')
            proof=transport.negative_window(status)
            if (proof['publicationSpanNs']<5_000_000_000 or proof['qaCaptureSpanNs']<5_000_000_000
                    or proof['closingCaptureNs']<=proof['historyFenceNs']): raise RuntimeError('unclosed negative proof')
            proofs.append(proof)
        report=transport.instrument(on_phase)
        if (report.get('schemaVersion')!=1 or report.get('passed') is not True or report.get('owner')!=config.owner
                or type(report.get('cases')) is not list or len(report['cases'])!=len(REQUIRED_CASES)
                or set(report['cases'])!=REQUIRED_CASES or len(proofs)!=1):
            raise RuntimeError('incomplete/foreign runner cases or negative proof')
        result.update(softwareFlowComplete=True,runner=report,negativeWindow=proofs[0])
        return result
    except BaseException as ex:
        result['failure']=type(ex).__name__+': '+str(ex)
        raise
    finally:
        (config.run_dir/'result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')


if __name__=='__main__':
    try: print(json.dumps(main(),sort_keys=True))
    except (ValueError,RuntimeError,AssertionError,subprocess.SubprocessError) as ex:
        raise SystemExit(str(ex))
