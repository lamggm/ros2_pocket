"""Synthetic viewport-cost regression; real guarded Client, never physical acceptance."""
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

with patch.dict(os.environ, {'POCKET_TABLET': 'HOST_TEST_DOUBLE'}):
    from test_tablet_harness import TraceAdb, ui
import tablet_plot_acceptance as harness

NAMESPACE = '/plot_' + 'a' * 32


class PlotPages(TraceAdb):
    """Only the transport is synthetic; offscreen controls are absent."""
    def __init__(self):
        super().__init__()
        self.focused = ui.PKG + '/com.lamggm.rospocket.MainActivity'
        self.page = 4
        self.moves = []
        self.checked = {0: False, 1: False}

    def nodes(self):
        pages = [
            [('diagnostics', 'connected', 'TextView')],
            [('explorer_search', '', 'EditText')],
            [('rosout_search', '', 'EditText')],
            [('field_' + str(slot), f'Watch {slot+1} · {NAMESPACE}/{name} · data [unidade desconhecida]', 'CheckBox')
             for slot, name in enumerate(('a', 'b'))],
            [('plot_chart', '', 'View')],
        ]
        root = ET.Element('hierarchy')
        ET.SubElement(root, 'node', {'package': ui.PKG, 'class': 'android.widget.ScrollView',
                                    'bounds': '[0,0][800,900]'})
        for i, (ident, text, cls) in enumerate(pages[self.page]):
            attrs = {'package': ui.PKG, 'resource-id': ui.PKG + ':id/' + ident,
                     'text': text, 'class': 'android.widget.' + cls,
                     'bounds': f'[10,{100+i*70}][600,{150+i*70}]', 'enabled': 'true'}
            if self.page == 3:
                attrs['checked'] = str(self.checked[i]).lower()
            ET.SubElement(root, 'node', attrs)
        return ET.tostring(root, encoding='unicode')

    def __call__(self, args, timeout):
        command = args[3:]
        if command[:2] == ['shell', 'cat']:
            return self.nodes()
        if command[:3] == ['shell', 'input', 'swipe']:
            before = self.page
            step = 1 if int(command[4]) > int(command[6]) else -1
            self.page = max(0, min(4, self.page + step))
            self.moves.append((before, self.page))
            return ''
        if command[:3] == ['shell', 'input', 'tap']:
            if self.page == 3:
                slot = (int(command[4]) - 125) // 70
                self.checked[slot] = not self.checked[slot]
            return ''
        return super().__call__(args, timeout)


class PlotNavigationCostTests(unittest.TestCase):
    def test_repeated_series_changes_stay_between_plot_choices_and_chart(self):
        with tempfile.TemporaryDirectory() as tmp:
            transport = PlotPages()
            client = ui.Client('HOST_TEST_DOUBLE', out=Path(tmp), runner=transport)
            nav = harness.Navigator(client, NAMESPACE)
            for slot, name in enumerate(('a', 'b')):
                nav.series(slot, name)
                self.assertEqual(transport.page, 4)
                self.assertTrue(transport.checked[slot])
            self.assertEqual(transport.checked, {0: True, 1: True})
            self.assertTrue(all(before >= 3 and after >= 3 for before, after in transport.moves),
                            'Plot-to-plot changes must not revisit diagnostics or Explorer')
            self.assertEqual(len(transport.moves), 4, 'Two adjacent viewport moves per series change')

    def test_series_remains_reachable_from_every_setup_viewport(self):
        for initial in range(5):
            with self.subTest(initial=initial), tempfile.TemporaryDirectory() as tmp:
                transport = PlotPages()
                transport.page = initial
                client = ui.Client('HOST_TEST_DOUBLE', out=Path(tmp), runner=transport)
                nav = harness.Navigator(client, NAMESPACE)
                for selected in (True, True, False, False):
                    nav.series(1, 'b', selected)
                    self.assertEqual(transport.page, 4)
                    self.assertEqual(transport.checked, {0: False, 1: selected})

    def test_foreign_focus_stops_local_navigation_before_any_input(self):
        with tempfile.TemporaryDirectory() as tmp:
            transport = PlotPages()
            transport.focused = 'com.example.foreign/.Main'
            client = ui.Client('HOST_TEST_DOUBLE', out=Path(tmp), runner=transport)
            with self.assertRaises(RuntimeError):
                harness.Navigator(client, NAMESPACE).series(0, 'a')
            self.assertEqual(transport.moves, [])
            self.assertEqual(transport.checked, {0: False, 1: False})
