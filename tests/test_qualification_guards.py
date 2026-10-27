"""Public CLI adversarial controls; synthetic attestations are NOT device evidence."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
import test_qualification as tq
from test_qualification import tool, run_cli


class QualificationGuardTests(unittest.TestCase):
    def analyze(self, data, root):
        metadata=root/'metadata.json'; source=root/'soak.json'
        metadata.write_text(json.dumps(data['snapshot']))
        source.write_text(json.dumps(data))
        return run_cli('analyze-soak','--metadata',metadata,'--input',source)

    def test_schema1_downgrade_cannot_authorize_on_apk_through_any_cli_consumer(self):
        import os, shutil, subprocess, sys, hashlib, zipfile
        from test_qualification import metadata
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory); repo=root/'private-source'; (repo/'tools').mkdir(parents=True)
            original=Path(__file__).resolve().parents[1]
            for p in (original/'tools').glob('*.py'):shutil.copy2(p,repo/'tools'/p.name)
            (repo/'.gitignore').write_text('build/\n__pycache__/\n')
            for args in (['init','-q'],['add','tools','.gitignore'],['-c','user.name=Synthetic Validator','-c','user.email=validator@invalid','commit','-qm','Explicit synthetic source snapshot']):
                subprocess.run(['git','-C',str(repo),*args],check=True,capture_output=True)
            evidence=repo/'build/evidence';evidence.mkdir(parents=True)
            apk=root/'unchanged-on.apk'; actual=os.environ.get('POCKET_POLICY_TEST_APK')
            if actual:shutil.copy2(actual,apk)
            else:
                with zipfile.ZipFile(apk,'w') as z:z.writestr('lib/arm64-v8a/librospocket.so',b'\x7fELF\0POCKET_TEST_HOOKS=ON\0POCKET_DIAGNOSTIC_POLICY=ros-pocket-dds-fault-v1\0')
            q=tool(); original_metadata=os.environ.get('POCKET_POLICY_TEST_METADATA')
            candidate=json.loads(Path(original_metadata).read_text()) if original_metadata else metadata()
            if original_metadata:
                self.assertEqual(candidate['sha256'],hashlib.sha256(apk.read_bytes()).hexdigest())
                self.assertIs(candidate['test_hooks'],True)
            candidate.update(schema_version=1,test_hooks=False,apk=str(apk),sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),size_bytes=apk.stat().st_size)
            candidate.pop('diagnostic_policy',None);candidate.pop('native_libraries',None)
            candidate['source']=json.loads(subprocess.check_output([sys.executable,str(repo/'tools/artifact_metadata.py'),'--source','--repo',str(repo)],text=True))
            self.assertFalse(candidate['source']['dirty'])
            meta=evidence/'metadata.json';meta.write_text(json.dumps(candidate))
            soak=tq.QualificationTests().synthetic_soak(q);soak['snapshot']=candidate
            (evidence/'soak.json').write_text(json.dumps(soak));(evidence/'installed.json').write_text(json.dumps(candidate))
            (evidence/'control.txt').write_text('SYNTHETIC ATTESTATIONS ONLY; NEVER PHYSICAL READINESS')
            report=q.new_report(candidate)
            for gate in report['gates']:
                name={'soak':'soak.json','installed-apk':'installed.json'}.get(gate['id'],'control.txt')
                gate.update(status='passed',snapshot_id=q.snapshot_id(candidate),operator='SYNTHETIC ONLY',note='Not physical evidence',evidence=[dict(path=name,sha256=hashlib.sha256((evidence/name).read_bytes()).hexdigest())])
            report_path=evidence/'report.json';report_path.write_text(json.dumps(report))
            invocations=[['init','--repo',repo,'--apk',apk,'--output',evidence/'new.json'],
                         ['analyze-soak','--input',evidence/'soak.json'],
                         ['evaluate','--repo',repo,'--apk',apk,'--report',report_path,'--evidence-root',evidence],
                         ['record','--repo',repo,'--report',report_path,'--evidence-root',evidence,'--gate','soak','--status','passed','--evidence','soak.json']]
            for gate_id in q.GATES:
                evidence_name={'soak':'soak.json','installed-apk':'installed.json'}.get(gate_id,'control.txt')
                for explicit in (False,True):
                    invocations.append(['record','--repo',repo,'--report',report_path,'--evidence-root',evidence,
                                        '--gate',gate_id,'--status','passed','--evidence',evidence_name,
                                        '--operator','SYNTHETIC ONLY','--note','Not physical evidence',
                                        *(['--apk',apk] if explicit else [])])
            original_report=report_path.read_bytes()
            for args in invocations:
                p=subprocess.run([sys.executable,str(repo/'tools/qualification.py'),*map(str,args),'--metadata',str(meta)],capture_output=True,text=True)
                with self.subTest(consumer=args[0],actual_apk=bool(actual)):
                    self.assertNotEqual(0,p.returncode,p.stdout)
                    self.assertIn('capability',p.stderr+p.stdout)
                    self.assertNotIn('source mismatch',p.stderr)
                    self.assertNotIn('Traceback',p.stderr)
                    self.assertEqual(original_report,report_path.read_bytes())

    def test_graph_requires_finite_drawn_series_and_coherent_actual_timestamps(self):
        data=tq.QualificationTests().diagnostic_soak(tool())
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            self.assertEqual(0,self.analyze(data,root).returncode)
            mutations=[('empty',None),('zero',None),('old-draw',None),('observed',None),
                       ('slot',-1),('slot',True),('seriesIndex',1),('pointCount',0),
                       ('finitePoints',0),('finitePoints',True),('gapPoints',1.0),
                       ('breakBeforePoints',3),('oldestReceiveNs',None),('newestReceiveNs',None),
                       ('newestReceiveNs',2**63-1)]
            for key,value in mutations:
                bad=copy.deepcopy(data); graph=bad['samples'][2]['diagnostic_proof']['qa']['plotDisplay']
                if key=='empty':graph['drawnSeries']=[{}]
                elif key=='zero':graph['drawnSeries'][0].update(pointCount=0,finitePoints=0,oldestReceiveNs=None,newestReceiveNs=None)
                elif key=='old-draw':graph.update(lastDrawMonotonicNs=0,drawAgeNs=0)
                elif key=='observed':graph['observedMonotonicNs']+=1
                else:graph['drawnSeries'][0][key]=value
                result=self.analyze(bad,root)
                with self.subTest(key=key,value=value):
                    self.assertEqual(1,result.returncode,result.stderr)
                    self.assertEqual('incomplete',json.loads(result.stdout)['status'])
                    self.assertIn('graph',str(json.loads(result.stdout)['blockers']))

    def test_each_sample_requires_positive_fresh_same_process_java_fence(self):
        data=tq.QualificationTests().diagnostic_soak(tool())
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            positive=self.analyze(data,root)
            self.assertEqual(0,positive.returncode,positive.stderr)
            for name in ('zero','replay','regressed','future','mixed'):
                bad=copy.deepcopy(data); p=bad['samples'][2]['diagnostic_proof']
                if name=='zero':p['export_fence_ns']=0
                elif name=='replay':p['export_fence_ns']=bad['samples'][0]['diagnostic_proof']['export_fence_ns']
                elif name=='regressed':p['export_fence_ns']=bad['samples'][1]['diagnostic_proof']['diagnostic']['capturedMonotonicNs']-1
                elif name=='future':p['export_fence_ns']=p['diagnostic']['exportedMonotonicNs']+1
                else:p['export_fence_ns']=bad['samples'][1]['diagnostic_proof']['diagnostic']['exportedMonotonicNs']
                result=self.analyze(bad,root)
                with self.subTest(name=name):
                    self.assertEqual(1,result.returncode,result.stderr)
                    self.assertEqual('incomplete',json.loads(result.stdout)['status'])
                    self.assertIn('fence',str(json.loads(result.stdout)['blockers']))

if __name__=='__main__':unittest.main()
