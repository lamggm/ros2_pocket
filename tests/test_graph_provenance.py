"""Actual portable Java exporter -> real policy consumers. All native/draw inputs synthetic."""
import copy
import json
import os

import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

def exports(source=ROOT):
    home=Path(os.environ.get('GRADLE_USER_HOME',str(Path.home()/'.gradle')))
    jars=sorted((home/'caches/modules-2/files-2.1/org.json/json').glob('*/*/json-*.jar'))
    if not jars:raise unittest.SkipTest('portable Java export control requires the Gradle org.json test dependency cache')
    java_home=Path(os.environ.get('JAVA_HOME','/usr'))
    with tempfile.TemporaryDirectory(prefix='graph-export-',dir=os.environ.get('TMPDIR')) as out:
        production=source/'app/src/main/java/com/lamggm/rospocket'
        subprocess.run([str(java_home/'bin/javac'),'-cp',str(jars[-1]),'-d',out,
                        *[str(production/f'{name}.java') for name in ('PlotController','PlotHistory','QaSnapshot','WatchSlots','ConnectionPolicy')],
                        str(ROOT/'tests/GraphProvenanceExports.java')],check=True,capture_output=True,text=True)
        run=subprocess.run([str(java_home/'bin/java'),'-cp',out+os.pathsep+str(jars[-1]),'com.lamggm.rospocket.GraphProvenanceExports'],check=True,capture_output=True,text=True)
        return json.loads(run.stdout)

class GraphProvenanceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.export=exports(Path(os.environ.get('GRAPH_SOURCE',str(ROOT))))
        if os.environ.get('GRAPH_EXPORT_JSON'):
            Path(os.environ['GRAPH_EXPORT_JSON']).write_text(json.dumps(cls.export,indent=2)+'\n')

    def campaign(self):
        # Official analyzer controls supply synthetic native counters/OS brackets.
        # Actual Java-exported series bytes/identities/statistics are retained,
        # with a declared receive-clock translation into each synthetic envelope.
        from test_qualification import QualificationTests,tool
        q=tool();data=QualificationTests().diagnostic_soak(q)
        for sample in data['samples']:
            proof=sample['diagnostic_proof'];qa=proof['qa'];d=proof['diagnostic']
            qa['plotIntake']=copy.deepcopy(self.export['qa']['plotIntake'])
            delta=proof['export_fence_ns']-2_000_000_000
            for s in qa['plotIntake']['series']:
                s['oldestReceiveNs']+=delta;s['newestReceiveNs']+=delta
            series=qa['plotIntake']['series']
            qa['plotDisplay']=dict(observedMonotonicNs=d['exportedMonotonicNs'],lastDrawMonotonicNs=d['capturedMonotonicNs'],
                drawAgeNs=100,chartFullyVisible=True,paused=False,drawPending=False,preparedRevision=1,drawnRevision=1,
                drawnSeries=copy.deepcopy(series),series=copy.deepcopy(series))
        return q,data

    def test_actual_exporter_consumer_total_series_association(self):
        q,data=self.campaign();self.assertEqual('passed',q.analyze_soak(data,data['snapshot'])['status'])
        for mutation in ('wrong-slot','omit-second','prepared-empty','equal-times'):
            bad=copy.deepcopy(data);g=bad['samples'][1]['diagnostic_proof']['qa']['plotDisplay']
            if mutation=='wrong-slot':g['drawnSeries'][0]['slot']=1
            elif mutation=='omit-second':g['drawnSeries'].pop()
            elif mutation=='prepared-empty':g['series']=[]
            else:
                # All copies agree: the timestamps themselves are impossible for two deduplicated points.
                for rows in (g['series'],g['drawnSeries'],bad['samples'][1]['diagnostic_proof']['qa']['plotIntake']['series']):
                    rows[0]['oldestReceiveNs']=rows[0]['newestReceiveNs']
            with self.subTest(mutation=mutation):self.assertEqual('incomplete',q.analyze_soak(bad,bad['snapshot'])['status'])

    def test_native_replacement_never_adopts_an_old_graph(self):
        q,data=self.campaign();self.assertEqual('passed',q.analyze_soak(data,data['snapshot'])['status'])
        for mutation in ('reader','session'):
            bad=copy.deepcopy(data)
            for sample in bad['samples']:
                proof=sample['diagnostic_proof'];qa=proof['qa']
                if mutation=='reader':qa['watches'][0]['readerGeneration']+=100
                else:qa['sessionGeneration']=proof['diagnostic']['sessionGeneration']=8
            with self.subTest(mutation=mutation):self.assertEqual('incomplete',q.analyze_soak(bad,bad['snapshot'])['status'])

    def test_actual_exporter_consumer_native_reader_provenance(self):
        q,data=self.campaign();self.assertEqual('passed',q.analyze_soak(data,data['snapshot'])['status'])
        for mutation in ('replacement-reader','replacement-session','intake-reader','intake-omitted','drawn-field','all-copies-field','all-copies-slot','boolean-prepared','fractional-intake'):
            bad=copy.deepcopy(data)
            for sample in bad['samples']:
                proof=sample['diagnostic_proof'];qa=proof['qa'];g=qa['plotDisplay'];intake=qa['plotIntake']['series']
                if mutation=='replacement-reader':qa['watches'][0]['readerGeneration']+=100
                elif mutation=='replacement-session':qa['sessionGeneration']=proof['diagnostic']['sessionGeneration']=8
                elif mutation=='intake-reader':intake[0]['readerGeneration']+=1
                elif mutation=='intake-omitted':intake.pop()
                elif mutation=='drawn-field':g['drawnSeries'][0]['fieldPath']='other'
                elif mutation=='all-copies-field':
                    for rows in (intake,g['series'],g['drawnSeries']):rows[0]['fieldPath']='other'
                elif mutation=='all-copies-slot':
                    for rows in (intake,g['series'],g['drawnSeries']):rows[0]['slot']=1
                elif mutation=='boolean-prepared':g['series'][0]['seriesIndex']=False
                else:intake[0]['pointCount']=2.0
            with self.subTest(mutation=mutation):self.assertEqual('incomplete',q.analyze_soak(bad,bad['snapshot'])['status'])

    def test_official_cli_consumes_actual_exporter_contract(self):
        from test_qualification import run_cli
        q,data=self.campaign()
        with tempfile.TemporaryDirectory(dir=os.environ.get('TMPDIR')) as out:
            root=Path(out);metadata=root/'metadata.json';payload=root/'input.json'
            metadata.write_text(json.dumps(data['snapshot']));payload.write_text(json.dumps(data))
            accepted=run_cli('analyze-soak','--metadata',metadata,'--input',payload)
            self.assertEqual(0,accepted.returncode,accepted.stderr)
            self.assertEqual('passed',json.loads(accepted.stdout)['status'])
            data['samples'][1]['diagnostic_proof']['qa']['plotDisplay']['drawnSeries'].pop()
            payload.write_text(json.dumps(data));rejected=run_cli('analyze-soak','--metadata',metadata,'--input',payload)
            self.assertEqual(1,rejected.returncode,rejected.stderr)
            self.assertEqual('incomplete',json.loads(rejected.stdout)['status'])

    def test_unmodified_actual_java_json_is_accepted_by_real_graph_validator(self):
        from tools.android_instrumentation import validate_graph
        qa=self.export['qa'];series=qa['plotIntake']['series']
        # The envelope alone is synthetic; no actual draw/visibility is claimed.
        g=dict(observedMonotonicNs=qa['exportedMonotonicNs'],lastDrawMonotonicNs=qa['capturedMonotonicNs'],
            drawAgeNs=100,chartFullyVisible=True,paused=False,drawPending=False,preparedRevision=1,drawnRevision=1,
            drawnSeries=copy.deepcopy(series),series=copy.deepcopy(series))
        validate_graph(g,qa['exportedMonotonicNs'],qa['watches'],qa['plotIntake'],qa['sessionGeneration'])

    def test_actual_exporter_carries_immutable_series_source(self):
        qa=self.export['qa'];self.assertTrue(qa['valid'])
        original=qa['plotIntake']['series'];self.assertEqual(2,len(original))
        for i,s in enumerate(original):
            self.assertEqual(i,s['slot'])
            self.assertEqual(7,s.get('sessionGeneration'),'producer must bind session when sampling')
            self.assertEqual(10+i,s.get('readerGeneration'),'producer must bind reader when sampling')
            self.assertEqual('Watch '+str(i+1)+' · /synthetic/'+str(i)+'\ndata',s.get('seriesId'))
            self.assertEqual('data',s.get('fieldPath'))
        self.assertEqual(original,self.export['afterReaderReplacement'],'copied series cannot be relabeled with new reader')
        self.assertEqual(original,self.export['afterSessionReplacement'],'copied series cannot be relabeled with new session')

if __name__=='__main__':unittest.main()
