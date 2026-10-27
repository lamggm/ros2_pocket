"""Host scheduling/argument controls; no ROS, Android or physical acceptance."""
import argparse
import subprocess
import sys
from pathlib import Path
import unittest

import device_plot_fixture as fixture


class PlotCampaignBudgetTests(unittest.TestCase):
    def test_bounded_budget_includes_cleanup_and_fixture_startup_margin(self):
        self.assertEqual(fixture.CAMPAIGN_SECONDS, 1800)
        self.assertEqual(fixture.RUNNER_TIMEOUT_SECONDS, 1980)
        self.assertEqual(fixture.FIXTURE_MAX_SECONDS, 2040)
        self.assertGreaterEqual(fixture.RUNNER_TIMEOUT_SECONDS - fixture.CAMPAIGN_SECONDS, 180)
        self.assertGreaterEqual(fixture.FIXTURE_MAX_SECONDS - fixture.RUNNER_TIMEOUT_SECONDS, 60)

    def test_duration_boundary_and_existing_short_host_fixture(self):
        for duration in (30, 180, 420, 600, 2040):
            self.assertEqual(fixture.bounded_duration(str(duration)), duration)
        for duration in ('29', '2041', '0', '-1', '1.5', 'infinity'):
            with self.subTest(duration=duration), self.assertRaises(argparse.ArgumentTypeError):
                fixture.bounded_duration(duration)

    def test_cli_rejects_excess_duration_before_importing_ros(self):
        command = [sys.executable, str(Path(fixture.__file__)), '--domain', '67',
                   '--namespace', '/plot_' + 'a' * 32, '--control', 'unused',
                   '--duration', '2041']
        result = subprocess.run(command, capture_output=True, text=True, timeout=5)
        self.assertEqual(result.returncode, 2)
        self.assertIn('duration must be 30..2040 seconds', result.stderr)
        self.assertNotIn('Traceback', result.stderr)
        self.assertEqual(result.stdout, '')
