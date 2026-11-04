import pathlib,subprocess,unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
class FdroidNativeTests(unittest.TestCase):
 def test_missing_arguments_are_controlled_before_any_build(self):
  p=subprocess.run(['bash',str(ROOT/'tools/fdroid-native.sh')],capture_output=True,text=True)
  self.assertEqual(2,p.returncode)
  self.assertIn('Usage:',p.stderr)
 def test_wrong_source_or_ndk_fail_before_cmake(self):
  p=subprocess.run(['bash',str(ROOT/'tools/fdroid-native.sh'),str(ROOT),str(ROOT)],capture_output=True,text=True)
  self.assertNotEqual(0,p.returncode)
  self.assertIn('pinned Cyclone DDS',p.stderr)
 def test_fdroid_helper_is_source_only_and_hooks_off(self):
  s=(ROOT/'tools/fdroid-native.sh').read_text()
  for forbidden in ('curl ','wget ','git clone','gradlew','signingConfig','--skip-scan'):
   self.assertNotIn(forbidden,s)
  self.assertIn('POCKET_ENABLE_TEST_HOOKS=OFF',s)
  self.assertIn('2cdd114cbd18340c606573b4cc8dc20cc161ec5a',s)
  self.assertIn('28.2.13676358',s)
