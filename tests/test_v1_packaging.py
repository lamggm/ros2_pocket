import pathlib,re,unittest,xml.etree.ElementTree as ET
ROOT=pathlib.Path(__file__).resolve().parents[1]
ANDROID='{http://schemas.android.com/apk/res/android}'
class V1PackagingTests(unittest.TestCase):
 def test_production_and_dev_are_separate_update_identities(self):
  s=(ROOT/'app/build.gradle').read_text()
  self.assertRegex(s,r"applicationId 'com\.lamggm\.rospocket'")
  self.assertRegex(s,r"versionName '1\.3\.0'")
  self.assertIn("applicationIdSuffix '.dev'",s)
  self.assertIn("versionNameSuffix '-dev'",s)
  self.assertIn('debuggable false',s)
 def test_launcher_has_adaptive_and_themed_icon(self):
  m=ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot().find('application')
  assert m is not None
  self.assertEqual('@mipmap/ic_launcher',m.get(ANDROID+'icon'))
  self.assertEqual('@string/app_name',m.get(ANDROID+'label'))
  themed=ET.parse(ROOT/'app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml').getroot()
  self.assertIsNotNone(themed.find('monochrome'))
 def test_license_and_foss_source_icon_are_present(self):
  self.assertIn('Apache License',(ROOT/'LICENSE').read_text())
  self.assertTrue((ROOT/'artwork/ros-pocket-icon.svg').is_file())
if __name__=='__main__':unittest.main()
