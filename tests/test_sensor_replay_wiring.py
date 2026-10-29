"""CI transport wiring only; actual DDS/Canvas behavior is exercised by Verify."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]

class SensorReplayWiringTests(unittest.TestCase):
 def test_fixture_records_both_rmws_for_the_unskipped_canvas_gate(self):
  fixture=(ROOT/'tools/test.sh').read_text()
  campaigns=(ROOT/'tools/host_campaigns.py').read_text()
  self.assertIn('POCKET_SENSOR_RECORDS=/tmp/sensor-records',fixture)
  self.assertIn('mkdir -p /tmp/native /tmp/sensor-records',fixture)
  self.assertIn('build/sensor-dds-records/$rmw',fixture)
  self.assertIn('for packet in scan image compressed',fixture)
  self.assertIn('/tmp/sensor-records/$packet.packet',fixture)
  self.assertIn("POCKET_SENSOR_PACKETS=str(ROOT/'build/sensor-dds-records')",campaigns)
  self.assertNotIn('SensorCanvasTest',campaigns.split('def _read_java_report')[1].split('def verify_java_reports')[0])
