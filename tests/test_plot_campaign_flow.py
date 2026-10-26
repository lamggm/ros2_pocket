"""Public orchestration regression with explicitly SYNTHETIC UI/QA/Docker controls.

No subprocess invokes ADB/Docker. Navigation guards and Canvas/DDS have separate
real tests; this exercises the actual campaign function and all phase decisions.
"""
import copy
import hashlib
import importlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import types
import unittest
from unittest.mock import patch


class CampaignFlowTests(unittest.TestCase):
    def test_public_campaign_covers_all_phases_and_cleans_up_without_hardware(self):
        self.campaign_with_path('/data/app/test/base.apk')

    def test_public_campaign_admits_realistic_tilde_apk_path(self):
        self.campaign_with_path('/data/app/~~A_b+c==/com.lamggm.rospocket.dev-Def_1==/base.apk')

    def test_public_campaign_still_rejects_bad_path_split_and_hash(self):
        for path in ('/sdcard/base.apk','/data/app/x;id/base.apk','/data/app/x y/base.apk',
                     '/data/app/x/base.apk\npackage:/data/app/x/split.apk'):
            with self.subTest(path=path),self.assertRaisesRegex(ValueError,'Single exact'):
                self.campaign_with_path(path)
        with self.assertRaisesRegex(ValueError,'bytes differ'):
            self.campaign_with_path('/data/app/~~A_b==/dev-Z==/base.apk',wrong_hash=True)

    def test_measured_setup_and_remaining_navigation_fit_full_campaign(self):
        # Same-host prefix profile: ~489 s to first crop. Charge that entire
        # cost up front AND 8 s per later navigation operation (conservative
        # synthetic scheduling control, never Android performance evidence).
        self.campaign_with_path('/data/app/test/base.apk', setup_seconds=489,
                                navigation_seconds=8)

    def test_old_budget_rejects_measured_cost_and_still_cleans_up(self):
        import plot_device_campaign as c
        with patch.object(c, 'CAMPAIGN_SECONDS', 540, create=True):
            self.campaign_with_path('/data/app/test/base.apk', setup_seconds=489,
                                    navigation_seconds=8, expected_deadline=True)

    def test_exhausted_new_budget_still_rejects_and_cleans_up(self):
        self.campaign_with_path('/data/app/test/base.apk', setup_seconds=1801,
                                expected_deadline=True)

    def campaign_with_path(self, installed_path, *, wrong_hash=False,
                           setup_seconds=0, navigation_seconds=0, expected_deadline=False):
        with patch.dict(os.environ, {'POCKET_TABLET': 'HOST_TEST_DOUBLE'}):
            import tablet_ui as ui
        import tablet_plot_acceptance as h
        import plot_device_campaign as c
        now = [100.0]
        selected, started, frozen = set(), {}, []
        phase = ['finite']
        paused, connected = [False], [False]
        generation = [2, 2, 2, 0]
        requested = []
        serial = 'HOST_TEST_DOUBLE'
        namespace = '/plot_' + 'a'*32
        fixture_stopped = []
        def clock(): return now[0]
        def sleep(seconds): now[0] += seconds
        def series():
            result = []
            for index, slot in enumerate(sorted(selected)):
                newest = int(now[0]*1e9)
                oldest = max(int(started[slot]*1e9), newest-30_000_000_000)
                n = max(1, min(60, (newest-oldest)//500_000_000+1))
                gaps = min(4, n) if slot != 1 and phase[0] in ('nan', 'positive_infinity', 'negative_infinity') else 0
                result.append(dict(slot=slot, seriesIndex=index, seriesId=f'Watch {slot+1} · {namespace}/{("a","b","stamped")[slot]}\ndata',
                                   fieldPath='data', sessionGeneration=7, readerGeneration=generation[slot], pointCount=n,
                                   finitePoints=n-gaps, gapPoints=gaps, breakBeforePoints=2 if phase[0] in ('source_reset','source_jump','window') and slot==2 else 1,
                                   oldestReceiveNs=oldest, newestReceiveNs=newest))
            return result
        class FakeNavigator:
            def __init__(self, client, namespace): pass
            def watch(self, slot, suffix):
                now[0] += navigation_seconds
                generation[slot] += 1
                selected.discard(slot)
            def series(self, slot, suffix, on=True):
                now[0] += navigation_seconds
                if on and slot not in selected:
                    selected.add(slot);started[slot]=now[0]
                elif not on:
                    selected.discard(slot)
                    frozen[:] = [s for s in frozen if s['slot'] != slot]
            def chart(self): now[0] += navigation_seconds
            def tap(self, selector, direction):
                now[0] += navigation_seconds
                if selector == 'plot_pause':
                    paused[0] = not paused[0]
                    if paused[0]: frozen[:] = copy.deepcopy(series())
                elif selector.startswith('REMOVER WATCH'):
                    selected.discard(0);frozen[:] = [s for s in frozen if s['slot'] != 0]
                else: raise AssertionError(selector)
        class FakeFixture:
            def __init__(self, cid, *args): self.cid=cid
            def verify(self): pass
            def phase(self, value):
                requested.append(value);phase[0]=value
                if value == 'gap':
                    selected.discard(0);selected.discard(2)
                if value == 'stop': fixture_stopped.append(True)
            def events(self, value): return []
            def docker(self, action, *args, **kwargs):
                if action == 'wait': return '0'
                if action == 'inspect': return json.dumps([{'State': {'Running': False}}])
                raise AssertionError(action)
        class Client:
            # Explicit synthetic test double. A production-created ui.Client never
            # acquires this class; no actual transport is called here.
            kind = 'adb_observation'
            def __init__(self, out, expected): self.out,self.expected,self.serial=out,expected,serial
            def verify(self): now[0] += setup_seconds
            def focus(self): pass
            def hide_ime(self): pass
            def scroll(self, direction): pass
            def field(self, ident, value): pass
            def tap(self, ident): connected[0]=not connected[0]
            def find(self, ident, **kwargs): return self.dump('synthetic')
            def dump(self, label):
                return [{'package':ui.PKG,'resource-id':ui.PKG+':id/connect','text':'DESCONECTAR' if connected[0] else 'CONECTAR'},
                        {'package':ui.PKG,'resource-id':ui.PKG+':id/plot_chart','bounds':'[0,0][800,300]'}]
            def adb(self, *args, **kwargs):
                if args[:3] == ('shell','pm','path'): return 'package:'+installed_path+'\n'
                if args[:2] == ('shell','sha256sum'): return ('bad' if wrong_hash else self.expected)+'  '+installed_path+'\n'
                if args[:3] == ('shell','am','start'): return ''
                raise AssertionError(args)
            def qa(self):
                now[0] += .8
                export = int(now[0]*1e9)
                rows = [dict(slot=i, valid=True, pending=False, readerActive=i<3, hasError=False,
                             freshAtExport=i<3 and (phase[0]!='gap' or i==1), count=int(now[0]*2) if i<3 else 0,
                             readerGeneration=generation[i], requestVersion=1, appliedVersion=1 if i<3 else -1) for i in range(4)]
                live=series()
                shown=copy.deepcopy(frozen) if paused[0] else copy.deepcopy(live)
                for i,s in enumerate(shown): s['seriesIndex']=i
                return dict(valid=True,foreground=True,clock='java.nanoTime',pid=123,lifecycleGeneration=1,sessionGeneration=7,
                            capturedMonotonicNs=export-1,exportedMonotonicNs=export,captureAgeNs=1,watches=rows,
                            plotIntake=dict(capacityPerSeries=600,windowNs=30_000_000_000,series=live),
                            plotDisplay=dict(drawPending=False,preparedRevision=1,drawnRevision=1,observedMonotonicNs=export,
                                             lastDrawMonotonicNs=export-1,drawAgeNs=1,chartFullyVisible=True,paused=paused[0],
                                             series=shown,drawnSeries=shown))
        def command(args, **kwargs):
            stdout = b'\x89PNG\r\n\x1a\nSYNTHETIC_CONTROL_NOT_AN_IMAGE' if 'screencap' in args else b''
            if args[0] == 'magick' and '-crop' in args:
                Path(args[-1]).write_bytes(b'SYNTHETIC_CHART_CONTROL')
            if kwargs.get('text'):
                if args[:2] == ['magick', 'compare']:
                    command.compares += 1
                    return subprocess.CompletedProcess(args, 0 if command.compares == 1 else 1, '', '0' if command.compares == 1 else '500')
                return subprocess.CompletedProcess(args,0,'','0')
            return subprocess.CompletedProcess(args,0,stdout,b'')
        command.compares = 0
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);apk=out/'not-a-real-apk.bin';apk.write_bytes(b'explicit synthetic installed-artifact binding control')
            client=Client(out,hashlib.sha256(apk.read_bytes()).hexdigest())
            args=types.SimpleNamespace(namespace=namespace,domain=71,fixture_container='b'*64,candidate_apk=apk,peer='')
            with patch.object(h,'Navigator',FakeNavigator), patch.object(c,'Fixture',FakeFixture), \
                 patch.object(c.subprocess,'run',side_effect=command), patch.object(c.time,'monotonic',side_effect=clock), \
                 patch.object(c.time,'sleep',side_effect=sleep):
                if expected_deadline:
                    with self.assertRaisesRegex(ValueError, "Campaign deadline exhausted"):
                        c.campaign(client,args)
                    report=json.loads((out/"plot-result.json").read_text())
                    self.assertFalse(report["passed"])
                    self.assertEqual(fixture_stopped,[True])
                    self.assertFalse(connected[0])
                    return
                report=c.campaign(client,args)
            self.assertTrue(report['passed'],report)
            self.assertFalse(report['physicalAccepted'])
            self.assertEqual(fixture_stopped,[True])
            self.assertFalse(connected[0])
            self.assertEqual(requested, ['nan','positive_infinity','negative_infinity','return','gap','return','source_reset','source_jump','window','stop'])
            self.assertIn('resume-new-pixels', report['checks'])
            self.assertEqual(command.compares, 2)
            self.assertEqual(json.loads((out/'plot-result.json').read_text()),report)
            self.assertGreaterEqual(len(report['checks']),9)

if __name__ == '__main__':
    unittest.main()
