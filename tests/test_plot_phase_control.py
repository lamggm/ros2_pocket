"""Synthetic transport regressions for phase publication; no DDS or device."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('phase_control_driver', ROOT / 'tests/plot_phase_host.py')
assert spec is not None and spec.loader is not None
driver = importlib.util.module_from_spec(spec)
spec.loader.exec_module(driver)


class CaptureReached(Exception):
    pass


class PhaseControlTests(unittest.TestCase):
    def test_running_publisher_never_observes_truncated_phase_during_update(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / 'run'
            control = out / 'phase'
            publisher = Mock()
            publisher.poll.return_value = None
            reader = Mock()
            reader.poll.return_value = 0
            ready = {'watches': [{'count': 2} for _ in range(3)]}
            reader.stdout.readline.side_effect = [json.dumps({'ok': True}) + '\n'] * 4 + [json.dumps(ready) + '\n', CaptureReached()]
            selector = Mock()
            selector.__enter__ = Mock(return_value=selector)
            selector.__exit__ = Mock(return_value=False)
            selector.select.return_value = [('ready',)]
            original_write = Path.write_text
            active_updates = []

            def scheduled_write(path, data, *args, **kwargs):
                # Synchronously observe the real filesystem after O_TRUNC,
                # exactly where the independent fixture can be scheduled.
                if path == control and launch.call_count:
                    with path.open('w') as stream:
                        self.assertIn(path.read_text(), driver_run_phases,
                                      'Running publisher observed a truncated control file')
                        return stream.write(data)
                if launch.call_count:
                    active_updates.append(data)
                    self.assertIn(control.read_text(), driver_run_phases)
                return original_write(path, data, *args, **kwargs)

            driver_run_phases = ('finite', 'nan', 'positive_infinity', 'negative_infinity',
                                 'return', 'gap', 'source_reset', 'source_jump', 'window', 'stop')
            with patch.object(driver.subprocess, 'Popen', side_effect=[publisher, reader]) as launch, \
                    patch.object(driver.selectors, 'DefaultSelector', return_value=selector), \
                    patch.object(driver.time, 'sleep'), patch.object(Path, 'write_text', scheduled_write):
                with self.assertRaises(CaptureReached):
                    driver.run(Path('/unused/reader'), Path('/unused/writer'), out)
            self.assertIn('finite', active_updates)
            self.assertIn('stop', active_updates)
            self.assertEqual(control.read_text(), 'stop')
            self.assertEqual(list(out.glob('phase.*')), [])


if __name__ == '__main__':
    unittest.main()
