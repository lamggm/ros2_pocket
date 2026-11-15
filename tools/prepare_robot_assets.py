#!/usr/bin/env python3
"""Build a bounded offline URDF/GLB package. Requires trimesh, pycollada, meshoptimizer.
No robot access, arbitrary URI fetching or changes to source meshes.
"""
import argparse,collections,hashlib,io,json,os,pathlib,zipfile,xml.etree.ElementTree as ET

def collect_sources(urdf,mappings):
 data=pathlib.Path(urdf).read_bytes()
 if len(data)>512*1024 or b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():raise ValueError('Unsafe or excessive URDF')
 root=ET.fromstring(data);name=root.get('name')
 if root.tag!='robot' or not name or len(name)>256:raise ValueError('Robot name required')
 counts=collections.Counter(m.get('filename') for m in root.findall('.//visual/geometry/mesh'))
 if not counts or len(counts)>64 or sum(counts.values())>256:raise ValueError('Mesh count outside bounds')
 items=[]
 for uri,count in sorted(counts.items()):
  if not isinstance(uri,str) or len(uri)>4096:raise ValueError('Invalid mesh URI')
  prefixes=sorted((p for p in mappings if p.endswith('/') and uri.startswith(p)),key=len,reverse=True)
  if not prefixes:raise ValueError('Unmapped URI; no network access: '+uri)
  prefix=prefixes[0];relative=uri[len(prefix):]
  if not relative or pathlib.PurePosixPath(relative).is_absolute() or '..' in pathlib.PurePosixPath(relative).parts or '\\' in relative:raise ValueError('Unsafe relative mesh path')
  parent=pathlib.Path(mappings[prefix]).resolve();path=(parent/relative).resolve()
  if not path.is_relative_to(parent) or not path.is_file() or path.suffix.lower()!='.dae':raise ValueError('Expected local DAE inside mapped root')
  items.append({'uri':uri,'path':path,'instances':count})
 return name,hashlib.sha256(data).hexdigest(),items

def budgets(counts,maximum,demands=None):
 if not counts or any(type(n) is not int or n<1 for n in counts.values()) or type(maximum) is not int or not 12*sum(counts.values())<=maximum<=120000:raise ValueError('Invalid instance-weighted face budget')
 if demands is None:
  each=maximum//sum(counts.values());return {key:each for key in counts}
 import math
 if set(demands)!=set(counts) or any(type(n) is not int or n<1 for n in demands.values()):raise ValueError('Complexity demands must cover exactly the mesh population')
 result={key:min(12,demands[key]) for key in counts};remaining=maximum-sum(result[k]*counts[k] for k in counts)
 while True:
  active=[k for k in sorted(counts) if result[k]<demands[k] and counts[k]<=remaining]
  if not active:break
  weight=sum(counts[k]*math.sqrt(demands[k]) for k in active);grants={k:min(demands[k]-result[k],int(remaining*math.sqrt(demands[k])/weight)) for k in active}
  if not any(grants.values()):
   for k in active:
    if counts[k]<=remaining:result[k]+=1;remaining-=counts[k]
  else:
   for k,n in grants.items():result[k]+=n;remaining-=n*counts[k]
 return result

def visual_color(visual):
 source=visual.material if getattr(visual,'kind',None)=='texture' else visual
 return source.main_color

def prepare(path,full=False):
 import numpy as np
 import trimesh, meshoptimizer
 data=path.read_bytes()
 if len(data)>256*1024*1024 or b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():raise ValueError('Unsafe or excessive DAE')
 meter=1.;up='Y_UP'
 for event,e in ET.iterparse(io.BytesIO(data),events=['end']):
  if e.tag.split('}')[-1]=='asset':
   for child in e:
    if child.tag.split('}')[-1]=='unit':meter=float(child.get('meter','1'))
    if child.tag.split('}')[-1]=='up_axis':up=(child.text or '').strip()
   break
 if not np.isfinite(meter) or not 0<meter<=1000 or up!='Z_UP':raise ValueError('Converter currently requires explicit Z_UP COLLADA and valid units')
 scene=trimesh.load_scene(io.BytesIO(data),file_type='dae',resolver={},ignore_broken=False,process=False)
 if not scene.geometry or any(not isinstance(m,trimesh.Trimesh) for m in scene.geometry.values()):raise ValueError('No triangle geometry')
 groups={}
 for node in scene.graph.nodes_geometry:
  transform,key=scene.graph[node];geometry=scene.geometry[key];color=tuple(int(c) for c in visual_color(geometry.visual))
  if len(color)!=4 or any(c<0 or c>255 for c in color):raise ValueError('Invalid material color')
  mesh=geometry.copy();mesh.apply_transform(transform);mesh.apply_scale(meter);mesh.visual=trimesh.visual.ColorVisuals(mesh=mesh,vertex_colors=np.tile(color,(len(mesh.vertices),1)));groups.setdefault(color,[]).append(mesh)
 if not groups or len(groups)>128:raise ValueError('Material palette outside bounds')
 parts=[]
 for color,meshes in sorted(groups.items()):
  mesh=trimesh.util.concatenate(meshes);mesh.merge_vertices(merge_tex=True,merge_norm=True)
  if not full:mesh.update_faces(mesh.unique_faces())
  mesh.remove_unreferenced_vertices()
  if not np.isfinite(mesh.vertices).all() or len(mesh.faces)==0:raise ValueError('Invalid material geometry')
  parts.append((color,mesh))
 original_bounds=np.array([np.min([m.bounds[0] for _,m in parts],axis=0),np.max([m.bounds[1] for _,m in parts],axis=0)])
 return parts,original_bounds,{'source_sha256':hashlib.sha256(data).hexdigest(),'source_bytes':len(data),'unit_meter':meter,'source_up_axis':up}

def convert(path,target,prepared=None):
 import numpy as np
 import trimesh,meshoptimizer
 parts,original_bounds,info=prepare(path) if prepared is None else prepared
 counts=np.array([len(m.faces) for _,m in parts],dtype=int);allowances=np.minimum(counts,32)
 if int(allowances.sum())>target:raise ValueError('Face budget cannot preserve the material palette')
 remaining=target-int(allowances.sum());weights=np.sqrt(counts);shares=np.floor(remaining*weights/weights.sum()).astype(int);allowances=np.minimum(counts,allowances+shares)
 while int(allowances.sum())<min(target,int(counts.sum())):
  for i in range(len(parts)):
   if allowances[i]<counts[i]:allowances[i]+=1
   if int(allowances.sum())>=min(target,int(counts.sum())):break
 output=[];palette=[]
 for (color,mesh),limit in zip(parts,allowances):
  before=len(mesh.faces)
  if before>limit:
   extrema=np.unique(np.concatenate((np.argmin(mesh.vertices,axis=0),np.argmax(mesh.vertices,axis=0))));keep=np.zeros(before,dtype=bool)
   for vertex in extrema:keep[np.flatnonzero(np.any(mesh.faces==vertex,axis=1))[0]]=True
   protected=mesh.faces[keep];allowance=int(limit)-len(protected)
   if allowance<4:raise ValueError('Insufficient material LOD allowance')
   indices=np.ascontiguousarray(mesh.faces[~keep].reshape(-1),dtype=np.uint32);destination=np.empty_like(indices);positions=np.ascontiguousarray(mesh.vertices,dtype=np.float32)
   count=meshoptimizer.simplify(destination,indices,positions,target_index_count=allowance*3,target_error=1.0,options=meshoptimizer.SIMPLIFY_PRUNE)
   method='quadric/prune'
   if count>allowance*3 or count<3:
    count=meshoptimizer.simplify_sloppy(destination,indices,positions,target_index_count=allowance*3,target_error=1.0);method='spatial fallback'
   if count<3 or count%3 or count>allowance*3:raise ValueError('Material LOD failed budget')
   mesh=trimesh.Trimesh(vertices=mesh.vertices.copy(),faces=np.vstack((protected,destination[:count].reshape(-1,3))),process=False);mesh.remove_unreferenced_vertices()
  else:method='original'
  mesh=trimesh.graph.smooth_shade(mesh,angle=np.radians(35),facet_minarea=None)
  mesh.visual=trimesh.visual.ColorVisuals(mesh=mesh,vertex_colors=np.tile(color,(len(mesh.vertices),1)));output.append(mesh);palette.append({'rgba':list(color),'faces_before':before,'faces_after':len(mesh.faces),'reduction':method})
 optimized=trimesh.util.concatenate(output)
 if len(optimized.faces)>target:raise ValueError('Simplification failed budget')
 bounds=optimized.bounds.copy();scale=max(float(np.ptp(original_bounds,axis=0).max()),1e-6);error=float(np.max(np.abs(bounds-original_bounds)))
 if error>scale*.05:raise ValueError('Simplification changed bounds by more than5 percent')
 normals=np.asarray(optimized.vertex_normals).copy();bad_normals=np.linalg.norm(normals,axis=1)<1e-12;normals[bad_normals]=[0,0,1];optimized.vertex_normals=normals
 optimized.metadata={} # Omit internal adjacency metadata, not geometry.
 optimized.apply_transform(np.array([[1,0,0,0],[0,0,1,0],[0,-1,0,0],[0,0,0,1]],dtype=float));glb=optimized.export(file_type='glb',include_normals=True)
 return glb,{**info,'faces_before':int(counts.sum()),'faces_after':len(optimized.faces),'material_palette':palette,'normal_crease_degrees':35,'normal_fallback_vertices':int(bad_normals.sum()),'bounds_urdf_metres':bounds.tolist(),'bounds_error_metres':error,'glb_bytes':len(glb)}

def build(urdf,mappings,output,maximum=120000,full=False):

 name,sha,items=collect_sources(urdf,mappings);prepared={i['uri']:prepare(i['path'],full=full) for i in items};demands={uri:sum(len(m.faces) for _,m in data[0]) for uri,data in prepared.items()};allocation=demands if full else budgets({i['uri']:i['instances'] for i in items},maximum,demands);manifest={'schema':2 if full else 1,'robot_name':name,'description_sha256':sha,'meshes':[]};reports=[];payloads={}
 if full:
  manifest['geometry']='full'
  if sum(demands.values())>2000000 or sum(demands[i['uri']]*i['instances'] for i in items)>4000000:raise ValueError('Full geometry exceeds bounded profile')
 for index,item in enumerate(items):
  uri=item['uri'];blob,report=convert(item['path'],allocation[uri],prepared[uri]);report['allocated_faces']=allocation[uri];file=f'meshes/m{index:03}.glb';payloads[file]=blob;manifest['meshes'].append({'uri':uri,'file':file,'sha256':hashlib.sha256(blob).hexdigest()});report.update(uri=uri,instances=item['instances'],source=str(item['path']));reports.append(report);print(json.dumps(report),flush=True)
 if sum(r['faces_after']*r['instances'] for r in reports)>(4000000 if full else maximum):raise ValueError('Assembled renderer budget exceeded')
 out=pathlib.Path(output);out.parent.mkdir(parents=True,exist_ok=True)
 if out.exists():raise ValueError('Refusing to overwrite an existing bundle')
 with zipfile.ZipFile(out,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
  data={'manifest.json':json.dumps(manifest,separators=(',',':')).encode(),**payloads}
  for filename,blob in data.items():
   info=zipfile.ZipInfo(filename,date_time=(2000,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,blob)
 if out.stat().st_size>(64 if full else 16)*1024*1024:raise ValueError('Package exceeds Android limit')
 result={'robot_name':name,'description_sha256':sha,'bundle':str(out),'bundle_sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'bundle_bytes':out.stat().st_size,'render_faces':sum(r['faces_after']*r['instances'] for r in reports),'assets':reports,'textures':'not transferred; original diffuse palette retained per material','coordinate_frame':'glTF Y-up, decoded into URDF Z-up'}
 out.with_suffix(out.suffix+'.report.json').write_text(json.dumps(result,indent=2));return result

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--urdf',required=True);p.add_argument('--mesh-root',action='append',default=[],metavar='URI_PREFIX=LOCAL_ROOT');p.add_argument('--output',required=True);p.add_argument('--full',action='store_true',help='Preserve all source faces; schema2 indexed profile');p.add_argument('--max-triangles',type=int,default=120000);a=p.parse_args();roots={}
 for item in a.mesh_root:
  prefix,sep,path=item.partition('=')
  if not sep or not prefix.endswith('/') or prefix in roots:raise ValueError('Unique URI prefix ending in / required')
  roots[prefix]=path
 print(json.dumps(build(a.urdf,roots,a.output,a.max_triangles,full=a.full),indent=2))
if __name__=='__main__':main()
