package com.lamggm.rospocket;

import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.json.*;

/** Schema 7 writes, schema 1–6 reads. Configuration and explicit robot hints; no telemetry or ephemeral DDS IDs. */
final class BenchProfiles {
 static final int MAX_PROFILES=16,MAX_BYTES=131072,MAX_NAME_CHARS=48,MAX_NAME_BYTES=128;
 static final class Watch {
  final String wire,type;final boolean reliable,transientLocal;
  Watch(String w,String t,boolean r,boolean l){
   if(w==null||t==null||w.length()>256||(!w.isEmpty()&&!w.matches("rt/[A-Za-z_][A-Za-z_0-9]*(/[A-Za-z_][A-Za-z_0-9]*)*"))||w.isEmpty()!=t.isEmpty()||(!t.isEmpty()&&!supported(t)))throw invalid("Tópico/tipo não suportado no perfil");
   wire=w;type=t;reliable=r;transientLocal=l;
  }
 }
 static final class Series {
  final int slot;final String path,unit;
  Series(int s,String p){this(s,p,"");}
  Series(int s,String p,String u){if(s<0||s>=4||p==null||p.length()>256||u==null||u.length()>24||u.codePoints().anyMatch(Character::isISOControl))throw invalid("Campo/unidade de gráfico inválido");slot=s;path=p;unit=u;}
  String key(){return slot+"\n"+path;}
 }
 static final class Sensor {
  final String wire,type;
  Sensor(String wire,String type,boolean scan){
   if(wire==null||wire.isEmpty()||wire.length()>256||!wire.matches("rt/[A-Za-z_][A-Za-z_0-9]*(/[A-Za-z_][A-Za-z_0-9]*)*")||type==null||
      !(scan?type.equals("sensor_msgs::msg::dds_::LaserScan_"):type.equals("sensor_msgs::msg::dds_::Image_")||type.equals("sensor_msgs::msg::dds_::CompressedImage_")))throw invalid("Seleção visual inválida");
   this.wire=wire;this.type=type;
  }
 }
 static final class Visual {
  final String mode;final Sensor scan,camera;
  Visual(){this("scan",null,null);}
  Visual(String mode,Sensor scan,Sensor camera){
   if(!"scan".equals(mode)&&!"camera".equals(mode))throw invalid("Modo visual inválido");
   if(scan!=null&&!scan.type.equals("sensor_msgs::msg::dds_::LaserScan_"))throw invalid("Tipo de scan inválido");
   if(camera!=null&&camera.type.equals("sensor_msgs::msg::dds_::LaserScan_"))throw invalid("Tipo de câmera inválido");
   this.mode=mode;this.scan=scan;this.camera=camera;
  }
 }
 static final class Robot {
  final String wire,fingerprint;
  Robot(String wire,String fingerprint){if(wire==null||wire.length()>256||!wire.matches("rt/[A-Za-z_][A-Za-z_0-9]*(/[A-Za-z_][A-Za-z_0-9]*)*")||fingerprint==null||!fingerprint.matches("[0-9a-f]{64}"))throw invalid("Associação de robô inválida");this.wire=wire;this.fingerprint=fingerprint;}
  boolean same(Robot other){return other!=null&&wire.equals(other.wire)&&fingerprint.equals(other.fingerprint);}
 }
 static final class Profile {
  final String id,name,peer,graphFormat,networkMode;final int domain;final List<Watch> watches;final List<Series> plots;final Visual visual;final DashboardLayout dashboard;final LocationConfig localization;final Robot robot;
  Profile(String id,String name,int domain,String peer,List<Watch> watches,List<Series> plots){
   this(id,name,domain,peer,watches,plots,new Visual());
  }
  Profile(String id,String name,int domain,String peer,List<Watch> watches,List<Series> plots,Visual visual){
   this(id,name,domain,peer,watches,plots,visual,"jazzy");
  }
  Profile(String id,String name,int domain,String peer,List<Watch> watches,List<Series> plots,Visual visual,String graphFormat){
   this(id,name,domain,peer,watches,plots,visual,graphFormat,"lan");
  }
  Profile(String id,String name,int domain,String peer,List<Watch> watches,List<Series> plots,Visual visual,String graphFormat,String networkMode){
   this(id,name,domain,peer,watches,plots,visual,graphFormat,networkMode,null);
  }
  Profile(String id,String name,int domain,String peer,List<Watch> watches,List<Series> plots,Visual visual,String graphFormat,String networkMode,DashboardLayout dashboard){
   this(id,name,domain,peer,watches,plots,visual,graphFormat,networkMode,dashboard,new LocationConfig());
  }
  Profile(String id,String name,int domain,String peer,List<Watch> watches,List<Series> plots,Visual visual,String graphFormat,String networkMode,DashboardLayout dashboard,LocationConfig localization){this(id,name,domain,peer,watches,plots,visual,graphFormat,networkMode,dashboard,localization,null);}
  Profile(String id,String name,int domain,String peer,List<Watch> watches,List<Series> plots,Visual visual,String graphFormat,String networkMode,DashboardLayout dashboard,LocationConfig localization,Robot robot){
   this.robot=robot;
   if(id==null||!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw invalid("Identidade de perfil inválida");
   validateName(name);
   if(!"jazzy".equals(graphFormat)&&!"humble".equals(graphFormat))throw invalid("Formato de grafo desconhecido");
   if(domain<0||domain>101||peer==null||peer.length()>15||!NetworkRules.validPeer(peer))throw invalid("Domínio/peer inválido");
   if(!"lan".equals(networkMode)&&!"vpn".equals(networkMode))throw invalid("Modo de rede desconhecido");
   if("vpn".equals(networkMode)&&peer.isEmpty())throw invalid("Modo VPN exige peer IPv4 explícito");
   if(watches==null||watches.size()!=4||plots==null||plots.size()>4)throw invalid("Limite: quatro watches e quatro séries");
   for(Watch w:watches)Objects.requireNonNull(w,"Watch inválido");
   Set<String> fields=new HashSet<>();
   for(Series s:plots)if(s==null||!numeric(watches.get(s.slot).type,s.path)||!fields.add(s.key()))throw invalid("Campo não numérico, duplicado ou de watch vazio");
   this.id=id;this.name=name;this.domain=domain;this.peer=peer;this.graphFormat=graphFormat;this.networkMode=networkMode;this.watches=Collections.unmodifiableList(new ArrayList<>(watches));this.plots=Collections.unmodifiableList(new ArrayList<>(plots));this.visual=Objects.requireNonNull(visual);this.dashboard=dashboard;this.localization=Objects.requireNonNull(localization);
  }
  Profile rename(String name){return new Profile(id,name,domain,peer,watches,plots,visual,graphFormat,networkMode,dashboard,localization,robot);}
  Profile withRobot(Robot value){return new Profile(id,name,domain,peer,watches,plots,visual,graphFormat,networkMode,dashboard,localization,value);}
  Profile withDashboard(DashboardLayout value){return new Profile(id,name,domain,peer,watches,plots,visual,graphFormat,networkMode,Objects.requireNonNull(value),localization,robot);}
 }
 final List<Profile> profiles;final Profile applied;
 BenchProfiles(){this(Collections.emptyList(),null);}
 BenchProfiles(List<Profile> profiles,Profile applied){
  if(profiles.size()>MAX_PROFILES)throw invalid("Limite: 16 perfis locais");
  Set<String> ids=new HashSet<>(),names=new HashSet<>();
  for(Profile p:profiles)if(p==null||!ids.add(p.id)||!names.add(p.name.toLowerCase(Locale.ROOT)))throw invalid("Nome/identidade de perfil duplicado");
  this.profiles=Collections.unmodifiableList(new ArrayList<>(profiles));this.applied=applied;
 }
 Profile find(String id){for(Profile p:profiles)if(p.id.equals(id))return p;return null;}
 BenchProfiles save(Profile p){List<Profile> next=new ArrayList<>(profiles);Profile old=find(p.id);if(old!=null)next.set(next.indexOf(old),p);else next.add(p);return new BenchProfiles(next,applied);}
 BenchProfiles delete(String id){List<Profile> next=new ArrayList<>(profiles);next.removeIf(p->p.id.equals(id));return new BenchProfiles(next,applied!=null&&applied.id.equals(id)?null:applied);}
 BenchProfiles apply(Profile p){return new BenchProfiles(profiles,p);}
 Profile recognize(int domain,String peer,String format,String mode,String wire,String fingerprint){
  if(wire==null||fingerprint==null)return null;Profile found=null;
  for(Profile p:profiles)if(p.domain==domain&&p.peer.equals(peer)&&p.graphFormat.equals(format)&&p.networkMode.equals(mode)&&p.robot!=null&&p.robot.wire.equals(wire)&&p.robot.fingerprint.equals(fingerprint)){if(found!=null)return null;found=p;}return found;
 }
 static void validateName(String name){
  if(name==null||name.isEmpty()||!name.equals(name.trim())||name.codePointCount(0,name.length())>MAX_NAME_CHARS||utf8(name).length>MAX_NAME_BYTES||name.codePoints().anyMatch(c->Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))throw invalid("Nome: 1–48 caracteres, até 128 bytes UTF-8, sem controles/espaços nas pontas");
 }
 static boolean supported(String t){return t.matches("std_msgs::msg::dds_::(String|Bool|Int32|Int64|Float32|Float64)_")||t.matches("geometry_msgs::msg::dds_::(Vector3|Quaternion|Twist|Pose)_")||t.equals("nav_msgs::msg::dds_::Odometry_");}
 static boolean numeric(String t,String p){
  if(t.matches("std_msgs::msg::dds_::(Int32|Int64|Float32|Float64)_"))return p.equals("data");
  if(t.equals("geometry_msgs::msg::dds_::Vector3_"))return p.matches("[xyz]");
  if(t.equals("geometry_msgs::msg::dds_::Quaternion_"))return p.matches("[xyzw]");
  if(t.equals("geometry_msgs::msg::dds_::Twist_"))return p.matches("(linear|angular)\\.[xyz]");
  if(t.equals("geometry_msgs::msg::dds_::Pose_"))return p.matches("position\\.[xyz]|orientation\\.[xyzw]");
  if(t.equals("nav_msgs::msg::dds_::Odometry_"))return p.matches("header\\.stamp\\.(sec|nanosec)|pose\\.pose\\.(position\\.[xyz]|orientation\\.[xyzw])|twist\\.twist\\.(linear|angular)\\.[xyz]|(pose|twist)\\.covariance\\[([0-9]|[12][0-9]|3[0-5])\\]");
  return false;
 }
 private static IllegalArgumentException invalid(String message){return new IllegalArgumentException(message);}
 private static byte[] utf8(String text){
  try{ByteBuffer b=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));byte[] out=new byte[b.remaining()];b.get(out);return out;}catch(CharacterCodingException ex){throw invalid("UTF-8 inválido");}
 }
 private static void keys(JSONObject j,String...expected){Set<String> want=new HashSet<>(Arrays.asList(expected)),actual=new HashSet<>();Iterator<String> keys=j.keys();while(keys.hasNext())actual.add(keys.next());if(!actual.equals(want))throw invalid("Campos de perfil desconhecidos/ausentes");}
 private static String string(JSONObject j,String key)throws JSONException{Object v=j.get(key);if(!(v instanceof String))throw invalid("Texto inválido: "+key);return (String)v;}
 private static int integer(JSONObject j,String key)throws JSONException{Object v=j.get(key);if(!(v instanceof Integer))throw invalid("Inteiro inválido: "+key);return (Integer)v;}
 private static boolean bool(JSONObject j,String key)throws JSONException{Object v=j.get(key);if(!(v instanceof Boolean))throw invalid("Booleano inválido: "+key);return (Boolean)v;}
 private static Sensor sensor(JSONObject j,boolean scan)throws JSONException{
  keys(j,"wire","type");return new Sensor(string(j,"wire"),string(j,"type"),scan);
 }
 private static Visual visual(JSONObject j)throws JSONException{
  keys(j,"mode","scan","camera");return new Visual(string(j,"mode"),j.isNull("scan")?null:sensor(j.getJSONObject("scan"),true),j.isNull("camera")?null:sensor(j.getJSONObject("camera"),false));
 }
 private static Profile profile(JSONObject j,int schema)throws JSONException{
  if(schema==1)keys(j,"id","name","domain","peer","watches","plots");
  else if(schema==2)keys(j,"id","name","domain","peer","watches","plots","visual");
  else if(schema==3)keys(j,"id","name","domain","peer","watches","plots","visual","graphFormat");
  else if(schema==4)keys(j,"id","name","domain","peer","watches","plots","visual","graphFormat","networkMode");
  else if(schema==5)keys(j,"id","name","domain","peer","watches","plots","visual","graphFormat","networkMode","dashboard");
  else if(schema==6)keys(j,"id","name","domain","peer","watches","plots","visual","graphFormat","networkMode","dashboard","localization");
  else keys(j,"id","name","domain","peer","watches","plots","visual","graphFormat","networkMode","dashboard","localization","robot");
  JSONArray ws=j.getJSONArray("watches"),ps=j.getJSONArray("plots");if(ws.length()!=4||ps.length()>4)throw invalid("Limite: quatro watches e quatro séries");
  List<Watch> watches=new ArrayList<>();List<Series> plots=new ArrayList<>();
  for(int i=0;i<ws.length();i++){JSONObject w=ws.getJSONObject(i);keys(w,"wire","type","reliable","transientLocal");watches.add(new Watch(string(w,"wire"),string(w,"type"),bool(w,"reliable"),bool(w,"transientLocal")));}
  for(int i=0;i<ps.length();i++){JSONObject s=ps.getJSONObject(i);if(schema>=7)keys(s,"slot","path","unit");else keys(s,"slot","path");plots.add(new Series(integer(s,"slot"),string(s,"path"),schema>=7?string(s,"unit"):""));}
  Robot robot=null;if(schema>=7&&!j.isNull("robot")){JSONObject r=j.getJSONObject("robot");keys(r,"wire","fingerprint");robot=new Robot(string(r,"wire"),string(r,"fingerprint"));}
  return new Profile(string(j,"id"),string(j,"name"),integer(j,"domain"),string(j,"peer"),watches,plots,schema==1?new Visual():visual(j.getJSONObject("visual")),schema<3?"jazzy":string(j,"graphFormat"),schema<4?"lan":string(j,"networkMode"),schema<5||j.isNull("dashboard")?null:DashboardLayout.decode(j.getJSONObject("dashboard").toString()),schema<6?new LocationConfig():LocationConfig.decode(j.getJSONObject("localization")),robot);
 }
 static BenchProfiles decode(byte[] bytes)throws JSONException{
  JSONObject j=ProfileJson.parse(bytes);
  keys(j,"schema","profiles","applied");int schema=integer(j,"schema");if(schema<1||schema>7)throw invalid("Schema de perfis desconhecido");
  JSONArray rows=j.getJSONArray("profiles");if(rows.length()>MAX_PROFILES)throw invalid("Limite: 16 perfis locais");List<Profile> profiles=new ArrayList<>();
  for(int i=0;i<rows.length();i++)profiles.add(profile(rows.getJSONObject(i),schema));
  return new BenchProfiles(profiles,j.isNull("applied")?null:profile(j.getJSONObject("applied"),schema));
 }
 private static Object sensorJson(Sensor s)throws JSONException{return s==null?JSONObject.NULL:new JSONObject().put("wire",s.wire).put("type",s.type);}
 private static JSONObject json(Profile p)throws JSONException{
  JSONArray watches=new JSONArray(),plots=new JSONArray();
  for(Watch w:p.watches)watches.put(new JSONObject().put("wire",w.wire).put("type",w.type).put("reliable",w.reliable).put("transientLocal",w.transientLocal));
  for(Series s:p.plots)plots.put(new JSONObject().put("slot",s.slot).put("path",s.path).put("unit",s.unit));
  JSONObject visual=new JSONObject().put("mode",p.visual.mode).put("scan",sensorJson(p.visual.scan)).put("camera",sensorJson(p.visual.camera));
  return new JSONObject().put("id",p.id).put("name",p.name).put("domain",p.domain).put("peer",p.peer).put("watches",watches).put("plots",plots).put("visual",visual).put("graphFormat",p.graphFormat).put("networkMode",p.networkMode).put("dashboard",p.dashboard==null?JSONObject.NULL:new JSONObject(p.dashboard.encode())).put("localization",p.localization.json()).put("robot",p.robot==null?JSONObject.NULL:new JSONObject().put("wire",p.robot.wire).put("fingerprint",p.robot.fingerprint));
 }
 static Profile importProfile(byte[] bytes)throws JSONException{
  JSONObject envelope=ProfileJson.parse(bytes);keys(envelope,"format","schema","profile");
  int schema=integer(envelope,"schema");
  if(!"ros-pocket-profile".equals(string(envelope,"format"))||(schema<2||schema>7))throw invalid("Formato/schema portátil desconhecido");
  Profile p=profile(envelope.getJSONObject("profile"),schema);
  return new Profile(UUID.randomUUID().toString(),p.name,p.domain,p.peer,p.watches,p.plots,p.visual,p.graphFormat,p.networkMode,p.dashboard,p.localization,p.robot);
 }
 static byte[] exportProfile(Profile profile)throws JSONException{
  byte[] bytes=utf8(new JSONObject().put("format","ros-pocket-profile").put("schema",7).put("profile",json(profile)).toString());
  if(bytes.length>MAX_BYTES)throw invalid("Perfil excede 128 KiB");return bytes;
 }
 byte[] encode()throws JSONException{
  JSONArray rows=new JSONArray();for(Profile p:profiles)rows.put(json(p));
  byte[] out=utf8(new JSONObject().put("schema",7).put("profiles",rows).put("applied",applied==null?JSONObject.NULL:json(applied)).toString());
  if(out.length>MAX_BYTES)throw invalid("Arquivo de perfis excede 128 KiB");return out;
 }
}
