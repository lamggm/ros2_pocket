package com.lamggm.rospocket;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Offline package only: URI strings are keys, never file/network access. */
final class RobotAssetBundle {
 static final int MAX_BYTES=64*1024*1024;
 final String name,descriptionHash;private final Map<String,List<RobotGlb.Triangle>> meshes;private final Map<String,RobotCad.Mesh> fullMeshes;
 private RobotAssetBundle(String n,String sha,Map<String,List<RobotGlb.Triangle>> m,Map<String,RobotCad.Mesh> f){name=n;descriptionHash=sha;meshes=Collections.unmodifiableMap(new HashMap<>(m));fullMeshes=Collections.unmodifiableMap(new HashMap<>(f));}
 private static IllegalArgumentException bad(){return new IllegalArgumentException("Pacote de malhas inválido ou fora dos limites");}
 private static void keys(JSONObject j,String... names){Set<String> expected=new HashSet<>(Arrays.asList(names));Iterator<String> k=j.keys();while(k.hasNext())if(!expected.remove(k.next()))throw bad();if(!expected.isEmpty())throw bad();}
 private static String digest(JSONObject j,String key)throws JSONException{String h=j.getString(key);if(!h.matches("[0-9a-f]{64}"))throw bad();return h;}
 private static boolean path(String p){return p.length()<=160&&p.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*\\.glb");}
 static RobotAssetBundle decode(byte[] bytes){try{
  if(bytes==null||bytes.length<22||bytes.length>MAX_BYTES)throw bad();ByteBuffer end=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);int tail=bytes.length-22;if(end.getInt(tail)!=0x06054b50||end.getShort(tail+4)!=0||end.getShort(tail+6)!=0||end.getShort(tail+20)!=0)throw bad();int entries=Short.toUnsignedInt(end.getShort(tail+10));if(entries<2||entries>65||Short.toUnsignedInt(end.getShort(tail+8))!=entries||Integer.toUnsignedLong(end.getInt(tail+12))+Integer.toUnsignedLong(end.getInt(tail+16))!=tail)throw bad();
  Map<String,byte[]> files=new HashMap<>();long total=0;boolean full=false;
  try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){ZipEntry entry;while((entry=zip.getNextEntry())!=null){String p=entry.getName();if(entry.isDirectory()||!(p.equals("manifest.json")||path(p))||files.containsKey(p)||files.size()>=65)throw bad();int limit=p.equals("manifest.json")?128*1024:full?RobotCad.MAX_BYTES:RobotGlb.MAX_BYTES;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=zip.read(buffer))!=-1){if(n==0||out.size()+(long)n>limit||(total+=n)>(full?128L:32L)*1024*1024)throw bad();out.write(buffer,0,n);}zip.closeEntry();files.put(p,out.toByteArray());if(p.equals("manifest.json")){JSONObject header=ProfileJson.parse(out.toByteArray());full=header.optInt("schema")==2;if(!full&&bytes.length>16*1024*1024)throw bad();}}}
  if(files.size()!=entries||!files.containsKey("manifest.json"))throw bad();JSONObject j=ProfileJson.parse(files.remove("manifest.json"));if(full){keys(j,"schema","robot_name","description_sha256","meshes","geometry");if(!"full".equals(j.getString("geometry")))throw bad();}else keys(j,"schema","robot_name","description_sha256","meshes");if(!(j.get("schema") instanceof Integer)||j.getInt("schema")!=(full?2:1))throw bad();String name=j.getString("robot_name"),hash=digest(j,"description_sha256");if(name.trim().isEmpty()||name.length()>256)throw bad();JSONArray rows=j.getJSONArray("meshes");if(rows.length()<1||rows.length()>64)throw bad();Map<String,List<RobotGlb.Triangle>> meshes=new HashMap<>();Map<String,List<RobotGlb.Triangle>> decoded=new HashMap<>();Map<String,RobotCad.Mesh> fullMeshes=new HashMap<>(),fullDecoded=new HashMap<>();long payloadBytes=0;Set<String> used=new HashSet<>();int budget=0;
  for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);keys(row,"uri","file","sha256");String uri=row.getString("uri"),file=row.getString("file"),sha=digest(row,"sha256");if(uri.isEmpty()||uri.length()>4096||(meshes.containsKey(uri)||fullMeshes.containsKey(uri))||!path(file)||!files.containsKey(file)||!RobotGlb.sha(files.get(file)).equals(sha))throw bad();if(full){RobotCad.Mesh mesh=fullDecoded.get(file);if(mesh==null){mesh=RobotCad.decode(files.get(file));fullDecoded.put(file,mesh);budget+=mesh.triangles;payloadBytes+=mesh.byteCount;if(budget>RobotCad.MAX_TRIANGLES||payloadBytes>96L*1024*1024)throw bad();}fullMeshes.put(uri,mesh);used.add(file);continue;}List<RobotGlb.Triangle> triangles=decoded.get(file);if(triangles==null){triangles=RobotGlb.decode(files.get(file));decoded.put(file,triangles);budget+=triangles.size();if(budget>RobotGlb.MAX_TRIANGLES)throw bad();}used.add(file);meshes.put(uri,triangles);}
  if(!used.equals(files.keySet()))throw bad();return new RobotAssetBundle(name,hash,meshes,fullMeshes);
 }catch(Exception e){throw bad();}}
 boolean matches(RobotDescription model){return model!=null&&name.equals(model.name)&&descriptionHash.equals(model.fingerprint);}
 List<RobotGlb.Triangle> triangles(String uri){return meshes.get(uri);}
 boolean fullGeometry(){return !fullMeshes.isEmpty();}
 RobotCad.Mesh fullMesh(String uri){return fullMeshes.get(uri);}
 boolean hasMesh(String uri){return meshes.containsKey(uri)||fullMeshes.containsKey(uri);}
 int meshCount(){return meshes.size()+fullMeshes.size();}
 int missing(RobotDescription model){int n=0;for(String uri:model.missingMeshes)if(!matches(model)||!hasMesh(uri))n++;return n;}
}
