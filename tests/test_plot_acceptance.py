"""Synthetic host controls for the fixture/harness, not Android acceptance."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent

def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

class PlotFixtureTests(unittest.TestCase):
    def test_phases_have_sustained_poll_opportunity_and_nonzero_finite_controls(self):
        f = load('device_plot_fixture')
        self.assertGreaterEqual(f.MIN_PHASE_SECONDS, 6)
        for phase in ('finite', 'nan', 'positive_infinity', 'negative_infinity', 'gap', 'return', 'source_reset', 'source_jump', 'window'):
            a, b, source = f.values(phase, 10)
            self.assertNotEqual(b, 0)
            if phase in ('finite', 'return', 'window'):
                self.assertNotEqual(a, 0)
            if phase == 'gap':
                self.assertIsNone(a)
        with self.assertRaises(ValueError):
            f.values('unknown', 1)

class PlotNavigationTests(unittest.TestCase):
    def test_real_guarded_client_reaches_source_selectors_from_top_and_deep_viewport(self):
        import os
        from unittest.mock import patch
        import tempfile
        import xml.etree.ElementTree as ET
        with patch.dict(os.environ, {'POCKET_TABLET': 'HOST_TEST_DOUBLE'}):
            from test_tablet_harness import TraceAdb, ui
        h = load('tablet_plot_acceptance')
        namespace = '/plot_' + 'a' * 32

        class Pages(TraceAdb):
            def __init__(self, page):
                super().__init__()
                self.focused = ui.PKG + '/com.lamggm.rospocket.MainActivity'
                self.page, self.search, self.reads = page, '', 0
                self.checked = {'qos_reliable': 'true', 'qos_transient': 'true', 'field': 'false'}

            def nodes(self):
                specs = [[('diagnostics', 'connected', 'TextView'), ('explorer_search', self.search, 'EditText'),
                          ('topic', '', 'TextView')], [('watch_select_0', 'Watch 1', 'Button')],
                         [('qos_reliable', 'Reliable', 'CheckBox'), ('qos_transient', 'Transient local', 'CheckBox')],
                         [('field', f'Watch 1 · {namespace}/a · data [unidade desconhecida]', 'CheckBox')],
                         [('plot_chart', '', 'View')]][self.page]
                root = ET.Element('hierarchy')
                ET.SubElement(root, 'node', {'package': ui.PKG, 'class': 'android.widget.ScrollView', 'bounds': '[0,0][800,900]'})
                for i, (ident, text, cls) in enumerate(specs):
                    attrs = {'package': ui.PKG, 'resource-id': ui.PKG + ':id/' + ident, 'text': text,
                             'class': 'android.widget.' + cls, 'bounds': f'[10,{100+i*70}][600,{150+i*70}]', 'enabled': 'true'}
                    if ident in self.checked: attrs['checked'] = self.checked[ident]
                    if ident == 'topic': attrs['content-desc'] = 'Tópico ' + namespace + '/a'
                    ET.SubElement(root, 'node', attrs)
                return ET.tostring(root, encoding='unicode')

            def __call__(self, args, timeout):
                c = args[3:]
                if c[:2] == ['shell', 'cat']: return self.nodes()
                if c[:3] == ['shell', 'input', 'swipe']:
                    self.page = max(0, min(4, self.page + (1 if int(c[4]) > int(c[6]) else -1)))
                    return ''
                if c[:3] == ['shell', 'input', 'tap']:
                    index = (int(c[4])-125)//70
                    if self.page == 0 and index == 2: self.reads += 1
                    if self.page == 2:
                        key = ('qos_reliable', 'qos_transient')[index]
                        self.checked[key] = 'false' if self.checked[key] == 'true' else 'true'
                    if self.page == 3: self.checked['field'] = 'true'
                    return ''
                if c[:3] == ['shell', 'input', 'text']:
                    self.search = c[-1].replace('%s', ' ')
                    return ''
                return super().__call__(args, timeout)

        for initial in (0, 4):
            with tempfile.TemporaryDirectory() as tmp:
                transport = Pages(initial)
                client = ui.Client('HOST_TEST_DOUBLE', out=tmp, runner=transport)
                nav = h.Navigator(client, namespace)
                nav.watch(0, 'a')
                self.assertEqual(transport.reads, 1, 'The actual topic row already invokes listener.read')
                nav.series(0, 'a')
                self.assertEqual(transport.page, 4)
                self.assertEqual(transport.checked['field'], 'true')

class PlotCampaignTests(unittest.TestCase):
    def test_public_cli_help_and_unguarded_run_never_touch_adb(self):
        import subprocess
        import tempfile
        script = ROOT / 'tablet_plot_acceptance.py'
        help_run = subprocess.run(['python3', str(script), '--help'], capture_output=True, text=True)
        self.assertEqual(help_run.returncode, 0)
        with tempfile.TemporaryDirectory() as tmp:
            bad = subprocess.run(['python3', str(script), 'run', '--serial', 'HOST_TEST_DOUBLE',
                                  '--namespace', '/plot_' + 'a'*32, '--domain', '71',
                                  '--candidate-apk', tmp, '--fixture-container', 'b'*64,
                                  '--out', tmp], capture_output=True, text=True)
        self.assertEqual(bad.returncode, 2)
        self.assertIn('--authorize-device', bad.stderr)

    def test_repeated_return_phase_uses_only_current_contiguous_events(self):
        import json
        import tempfile
        c = load('plot_device_campaign')
        ns = '/plot_' + 'a'*32
        rows = [dict(event='publication', namespace=ns, phase='return', sequence=i,
                     monotonicNs=i*500_000_000) for i in range(1, 17)]
        rows += [dict(event='publication', namespace=ns, phase='gap', sequence=i,
                      monotonicNs=i*500_000_000) for i in range(17, 33)]
        rows += [dict(event='publication', namespace=ns, phase='return', sequence=i,
                      monotonicNs=i*500_000_000) for i in range(33, 49)]
        with tempfile.TemporaryDirectory() as tmp:
            fixture = c.Fixture('b'*64, ns, 71, Path(tmp))
            fixture.verify = lambda: None
            fixture.docker = lambda *a, **k: '\n'.join(json.dumps(r) for r in rows)
            result = fixture.events('return')
            self.assertEqual(result[0]['sequence'], 33)
            self.assertEqual(result[-1]['sequence'], 48)

    def test_selective_paused_removal_ignores_renumbering_not_points_or_identity(self):
        c = load('plot_device_campaign')
        original = [dict(slot=i, seriesIndex=i, readerGeneration=2, pointCount=3, newestReceiveNs=300) for i in range(3)]
        after = [dict(s, seriesIndex=i) for i, s in enumerate(original[1:])]
        self.assertTrue(c.paused_survivors(original, after, 0))
        self.assertFalse(c.paused_survivors(original, [dict(s, newestReceiveNs=400) for s in after], 0))
        self.assertFalse(c.paused_survivors(original, [], 0))

    def test_phase_graph_requires_every_expected_series_not_vacuous_empty_success(self):
        c = load('plot_device_campaign')
        good = [dict(slot=i, finitePoints=4, gapPoints=2) for i in range(3)]
        c.require_phase_graph({'intake': good, 'display': good}, (0, 1, 2), gap_slots=(0, 2))
        for bad in ([], good[1:], good + [good[0]]):
            with self.assertRaises(ValueError):
                c.require_phase_graph({'intake': bad, 'display': bad}, (0, 1, 2), gap_slots=(0, 2))

    def test_fixture_rejects_undeclared_stamp_writer_path(self):
        c = load('plot_device_campaign')
        ns = '/plot_' + 'a'*32
        state = dict(Id='b'*64, State=dict(Running=True), Config=dict(
            Labels={'ros-pocket.plot-run': ns}, Cmd=['python3', '/fixture/device_plot_fixture.py',
                '--namespace', ns, '--domain', '71', '--control', '/tmp/plot-phase',
                '--stamped-writer', '/foreign/writer']))
        with self.assertRaises(ValueError):
            c.validate_fixture(state, 'b'*64, ns, 71)
        state['Config']['Cmd'][-1] = '/fixture/plot-stamped-writer'
        c.validate_fixture(state, 'b'*64, ns, 71)

    def test_cli_cleanup_failure_returns_nonzero_even_when_campaign_returned_a_report(self):
        import contextlib
        import io
        import os
        import sys
        import tempfile
        from unittest.mock import patch
        with patch.dict(os.environ, {'POCKET_TABLET': 'HOST_TEST_DOUBLE'}):
            import tablet_ui as ui
        import tablet_plot_acceptance as h
        import plot_device_campaign as c
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'synthetic-candidate.bin'
            apk.write_bytes(b'synthetic argument guard, not Android APK')
            argv = ['tablet_plot_acceptance.py', 'run', '--serial', 'HOST_TEST_DOUBLE',
                    '--namespace', '/plot_'+'a'*32, '--domain', '71', '--candidate-apk', str(apk),
                    '--fixture-container', 'b'*64, '--out', tmp, '--authorize-device']
            with patch.object(sys, 'argv', argv), patch.object(ui, 'Client', return_value=object()), \
                 patch.object(c, 'campaign', return_value={'passed': False, 'errors': ['synthetic cleanup failure']}), \
                 contextlib.redirect_stdout(io.StringIO()), self.assertRaises(SystemExit) as raised:
                h.main()
            self.assertEqual(raised.exception.code, 1)

    def test_missing_fixture_ownership_stops_before_phase_write(self):
        c = load('plot_device_campaign')
        with self.assertRaises(ValueError):
            c.validate_fixture({'Id': 'b'*64, 'State': {'Running': True},
                                'Config': {'Labels': {}, 'Cmd': []}}, 'b'*64,
                               '/plot_' + 'a'*32, 71)

class PlotOracleTests(unittest.TestCase):
    @staticmethod
    def synthetic_qa():
        series = dict(seriesId='Watch 1 · /q\ndata', fieldPath='data', slot=0, seriesIndex=0,
                      sessionGeneration=7, readerGeneration=2, pointCount=2,
                      finitePoints=2, gapPoints=0, breakBeforePoints=1,
                      oldestReceiveNs=100, newestReceiveNs=200)
        watches = [dict(slot=i, valid=True, pending=False, readerActive=i == 0, hasError=False,
                        freshAtExport=i == 0, count=10 if i == 0 else 0, readerGeneration=2,
                        requestVersion=1, appliedVersion=1 if i == 0 else -1) for i in range(4)]
        return dict(valid=True, clock='java.nanoTime', foreground=True, pid=123, lifecycleGeneration=1,
                    sessionGeneration=7, capturedMonotonicNs=300, exportedMonotonicNs=500, captureAgeNs=200,
                    watches=watches, plotIntake=dict(capacityPerSeries=600, windowNs=30_000_000_000, series=[series]),
                    plotDisplay=dict(preparedRevision=1, drawnRevision=1, drawPending=False,
                                     observedMonotonicNs=500, lastDrawMonotonicNs=400, drawAgeNs=100,
                                     chartFullyVisible=True, paused=False, series=[series], drawnSeries=[series]))

    def test_observation_rejects_replayed_draws_bool_revisions_and_detached_display(self):
        import copy
        h = load('tablet_plot_acceptance')
        original = self.synthetic_qa()
        h.observation(original, slots=(0,))  # unused inactive slots legitimately have applied=-1
        for path, value in [(('plotDisplay', 'drawnRevision'), True),
                            (('plotDisplay', 'observedMonotonicNs'), 600),
                            (('watches', 0, 'appliedVersion'), True),
                            (('plotIntake', 'series'), []), (('foreground',), False)]:
            bad = copy.deepcopy(original)
            target = bad
            for key in path[:-1]: target = target[key]
            target[path[-1]] = value
            with self.subTest(path=path), self.assertRaises(ValueError):
                h.observation(bad, slots=(0,))
        with self.assertRaises(ValueError):
            h.observation(original, original, slots=(0,))

    def test_completed_draw_oracle_accepts_unused_inactive_slots_but_rejects_mutants(self):
        import copy
        h = load('tablet_plot_acceptance')
        s = dict(seriesId='w\\ndata', fieldPath='data', slot=0, sessionGeneration=7,
                 readerGeneration=2, pointCount=1, finitePoints=1, gapPoints=0,
                 breakBeforePoints=1, oldestReceiveNs=100, newestReceiveNs=100)
        qa = dict(valid=True, foreground=True, pid=123, lifecycleGeneration=1, sessionGeneration=7,
                  clock='java.nanoTime', capturedMonotonicNs=100, exportedMonotonicNs=110, captureAgeNs=10,
                  watches=[dict(slot=i, valid=True, pending=False, readerActive=i<2, hasError=False,
                                freshAtExport=i<2, count=20 if i<2 else 0, readerGeneration=2,
                                requestVersion=0, appliedVersion=0 if i<2 else -1) for i in range(4)],
                  plotIntake=dict(capacityPerSeries=600, windowNs=30_000_000_000, series=[s]),
                  plotDisplay=dict(drawPending=False, preparedRevision=1, drawnRevision=1, paused=False,
                                   observedMonotonicNs=110, lastDrawMonotonicNs=105, drawAgeNs=5,
                                   chartFullyVisible=True, series=[s], drawnSeries=[s]))
        self.assertEqual(h.observation(qa)['counts'], [20, 20])
        mutants = []
        for section, key, value in [(None, 'foreground', False), (None, 'captureAgeNs', True),
                                    ('plotDisplay', 'drawPending', True), ('plotDisplay', 'drawnRevision', True),
                                    ('plotDisplay', 'drawAgeNs', True), ('plotIntake', 'windowNs', 30_000_000_000.0),
                                    ('plotIntake', 'series', []), ('plotDisplay', 'drawnSeries', [])]:
            bad = copy.deepcopy(qa)
            (bad if section is None else bad[section])[key] = value
            mutants.append(bad)
        bad = copy.deepcopy(qa); bad['watches'][0]['appliedVersion'] = False; mutants.append(bad)
        bad = copy.deepcopy(qa); bad['watches'][0]['readerGeneration'] = 3; mutants.append(bad)
        for index, bad in enumerate(mutants):
            with self.subTest(mutant=index), self.assertRaises(ValueError):
                h.observation(bad)
        with self.assertRaises(ValueError):
            h.observation(qa, qa)

    def test_statistics_reject_boolean_budget_and_unbalanced_finite_gaps(self):
        h = load('tablet_plot_acceptance')
        series = dict(seriesId='Watch 1 · /q\\ndata', fieldPath='data', slot=0,
                      sessionGeneration=7, readerGeneration=2, pointCount=2,
                      finitePoints=1, gapPoints=1, breakBeforePoints=1,
                      oldestReceiveNs=100, newestReceiveNs=200)
        h.validate_series(series)
        for key, value in [('pointCount', True), ('gapPoints', 2), ('newestReceiveNs', 100)]:
            bad = dict(series, **{key: value})
            with self.assertRaises(ValueError):
                h.validate_series(bad)

    def test_frozen_display_requires_advancing_intake_and_same_identity(self):
        h = load('tablet_plot_acceptance')
        s = dict(seriesId='w\\ndata', fieldPath='data', slot=0, sessionGeneration=7,
                 readerGeneration=2, pointCount=1, finitePoints=1, gapPoints=0,
                 breakBeforePoints=1, oldestReceiveNs=100, newestReceiveNs=100)
        first = {'display': [s], 'intake': [s], 'counts': [10, 20]}
        next_s = dict(s, pointCount=2, finitePoints=2, newestReceiveNs=200)
        after = {'display': [s], 'intake': [next_s], 'counts': [11, 21]}
        self.assertTrue(h.paused_progress(first, after))
        for bad in [dict(after, counts=[10, 21]), dict(after, display=[next_s]),
                    dict(after, intake=[dict(next_s, readerGeneration=3)])]:
            self.assertFalse(h.paused_progress(first, bad))

if __name__ == '__main__':
    unittest.main()
