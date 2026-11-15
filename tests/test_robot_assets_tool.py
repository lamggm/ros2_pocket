import pathlib,importlib.util,tempfile,unittest,os
ROOT=pathlib.Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('prepare_robot_assets',ROOT/'tools/prepare_robot_assets.py');tool=importlib.util.module_from_spec(spec);spec.loader.exec_module(tool)
class RobotAssetToolTests(unittest.TestCase):
 def test_complexity_budget_gives_detail_to_large_parts_not_an_equal_file_slice(self):
  counts={'wheel':4,'body':1,'camera':1,'lidar':1,'deck':1};demand={'wheel':51140,'body':324878,'camera':238686,'lidar':15956,'deck':593424};a=tool.budgets(counts,120000,demand);self.assertGreater(a['deck'],25000);self.assertLess(a['lidar'],8000);self.assertGreaterEqual(sum(a[k]*counts[k] for k in a),119996);self.assertLessEqual(sum(a[k]*counts[k] for k in a),120000)
 def test_complexity_allocation_caps_at_real_geometry_and_validates_the_same_population(self):
  self.assertEqual({'one':12,'repeated':24},tool.budgets({'one':1,'repeated':4},6000,{'one':12,'repeated':24}))
  for demand in [{'one':True},{'one':0},{'wrong':10}]:
   with self.assertRaises(ValueError):tool.budgets({'one':1},120,demand)
 def test_gpu_budget_preserves_instance_counting_and_a_bounded_120k_ceiling(self):
  counts={'base':1,'deck':1,'camera':1,'lidar':1,'wheel':4};result=tool.budgets(counts,120000);self.assertLessEqual(sum(result[k]*counts[k] for k in counts),120000);self.assertGreater(result['wheel'],750)
  with self.assertRaises(ValueError):tool.budgets(counts,120001)
 def test_texture_visual_uses_its_material_color_without_texture_loading(self):
  from types import SimpleNamespace
  visual=SimpleNamespace(kind='texture',material=SimpleNamespace(main_color=[12,34,56,255]));self.assertEqual([12,34,56,255],tool.visual_color(visual))
 def test_resolves_literal_mesh_uris_only_under_explicit_readonly_roots(self):
  with tempfile.TemporaryDirectory(dir=os.environ.get('TMPDIR')) as d:
   root=pathlib.Path(d);(root/'base.dae').write_text('source');p=root/'robot.urdf';p.write_text("<robot name='actual'><link name='base'><visual><geometry><mesh filename='package://r/base.dae'/></geometry></visual></link></robot>")
   name,sha,items=tool.collect_sources(p,{'package://r/':root});self.assertEqual('actual',name);self.assertEqual(64,len(sha));self.assertEqual(1,len(items));self.assertEqual(root/'base.dae',items[0]['path']);self.assertEqual('package://r/base.dae',items[0]['uri'])
 def test_rejects_unmapped_network_uris_traversal_and_dtd(self):
  with tempfile.TemporaryDirectory(dir=os.environ.get('TMPDIR')) as d:
   root=pathlib.Path(d);p=root/'robot.urdf'
   for uri in ['https://never-fetch.invalid/a.dae','package://r/../escape.dae']:
    p.write_text(f"<robot name='x'><link name='b'><visual><geometry><mesh filename='{uri}'/></geometry></visual></link></robot>")
    with self.assertRaises(ValueError):tool.collect_sources(p,{'package://r/':root})
   p.write_text("<!DOCTYPE robot [<!ENTITY e 'unsafe'>]><robot name='x'/>")
   with self.assertRaises(ValueError):tool.collect_sources(p,{})
 def test_budget_counts_every_urdf_instance_not_only_unique_files(self):
  counts={'base':1,'deck':1,'camera':1,'lidar':1,'wheel':4};result=tool.budgets(counts,6000);self.assertEqual(set(counts),set(result));self.assertLessEqual(sum(result[k]*counts[k] for k in counts),6000);self.assertTrue(all(v>=12 for v in result.values()))
