"""Real collector schedule with explicitly synthetic slow ADB/clock inputs."""
import copy
from pathlib import Path
import sys
import tempfile
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parent))
from test_collect_soak import Clock,SyntheticTransport
from test_qualification import metadata


class InitialCPUWindowTests(unittest.TestCase):
    def run_campaign(self,late_sleep=False):
        import collect_soak as c
        root=Path(__file__).resolve().parents[1]
        class SlowTransport(SyntheticTransport):
            def read(self,args,deadline,limit=65536):
                # About80ms per actual read is representative of the retained
                # physical journal; no parser/collector/analysis is mocked.
                self.clock.ns+=80_000_000
                return super().read(args,deadline,limit)
        class TestClock(Clock):
            def __init__(self):super().__init__();self.injected=False
            def sleep(self,seconds):
                super().sleep(seconds)
                if late_sleep and seconds>10 and not self.injected:
                    self.injected=True;self.ns+=3_000_000_000
        clock=TestClock();meta=metadata();transport=SlowTransport(clock,meta)
        collector=c.Collector(transport,meta,meta['apk'],clock.now,clock.sleep)
        with tempfile.TemporaryDirectory(dir=root/'build') as directory:
            out=Path(directory)/'synthetic-slow-adb';journal=c.Journal(out,root);transport.journal=journal
            result=c.run_campaign(collector,journal)
            saved=c.q.load_json(out/'soak-input.json')
            return result,saved,meta

    def test_first_real_cpu_window_does_not_add_observer_cost_to_nominal_interval(self):
        import collect_soak as c
        result,saved,meta=self.run_campaign()
        self.assertEqual(result['collection_status'],'complete',saved.get('collection_error'))
        self.assertEqual(result['analysis']['status'],'passed',result)
        self.assertIn(len(saved['samples']),(121,122))
        self.assertGreaterEqual(saved['samples'][-1]['monotonic_s']-saved['samples'][0]['monotonic_s'],1800)
        previous=saved['cpu_baseline']
        for sample in saved['samples']:
            current=sample['process_metrics'];dt=(current['host_ns']-previous['host_ns'])/1e9
            self.assertGreaterEqual(dt,13);self.assertLessEqual(dt,17)
            self.assertGreater(sample['cpu_percent'],0,'No zero-filled first CPU value')
            self.assertAlmostEqual(sample['cpu_percent'],c.cpu_percent(previous,current,100))
            previous=current
        invalid=copy.deepcopy(saved);invalid['samples'][0]['cpu_percent']=None
        self.assertEqual(c.q.analyze_soak(invalid,meta)['status'],'incomplete','Unmeasured priming must never become a measured sample')

    def test_late_first_window_is_still_incomplete_without_catchup_or_threshold_change(self):
        result,saved,_=self.run_campaign(late_sleep=True)
        self.assertEqual(result['collection_status'],'incomplete')
        self.assertIn('CPU clock/counter gap',saved['collection_error'])
        self.assertEqual(saved['samples'],[])
        self.assertNotEqual(result['analysis']['status'],'passed')


if __name__=='__main__':unittest.main()
