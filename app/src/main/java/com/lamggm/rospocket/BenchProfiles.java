package com.lamggm.rospocket;

import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import org.json.*;

/** Schema v1: immutable local configuration, never telemetry or native identities. */
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
  final int slot;final String path;
  Series(int s,String p){if(s<0||s>=4||p==null||p.length()>256)throw invalid("Campo de gráfico inválido");slot=s;path=p;}
  String key(){return slot+"\n"+path;}
 }
 static final class Profile {
  final String id,name,peer;final int domain;final List<Watch> watches;final List<Series> plots;
  Profile(String id,String name,int domain,String peer,List<Watch> watches,List<Series> plots){
   if(id==null||!id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw invalid("Identidade de perfil inválida");
   validateName(name);
   if(domain<0||domain>101||peer==null||peer.length()>15||!NetworkRules.validPeer(peer))throw invalid("Domínio/peer inválido");
   if(watches==null||watches.size()!=4||plots==null||plots.size()>4)throw invalid("Limite: quatro watches e quatro séries");
   for(Watch w:watches)Objects.requireNonNull(w,"Watch inválido");
   Set<String> fields=new HashSet<>();
   for(Series s:plots)if(s==null||!numeric(watches.get(s.slot).type,s.path)||!fields.add(s.key()))throw invalid("Campo não numérico, duplicado ou de watch vazio");
   this.id=id;this.name=name;this.domain=domain;this.peer=peer;this.watches=Collections.unmodifiableList(new ArrayList<>(watches));this.plots=Collections.unmodifiableList(new ArrayList<>(plots));
  }
  Profile rename(String name){return new Profile(id,name,domain,peer,watches,plots);}
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
 private static Profile profile(JSONObject j)throws JSONException{
  keys(j,"id","name","domain","peer","watches","plots");
  JSONArray ws=j.getJSONArray("watches"),ps=j.getJSONArray("plots");if(ws.length()!=4||ps.length()>4)throw invalid("Limite: quatro watches e quatro séries");
  List<Watch> watches=new ArrayList<>();List<Series> plots=new ArrayList<>();
  for(int i=0;i<ws.length();i++){JSONObject w=ws.getJSONObject(i);keys(w,"wire","type","reliable","transientLocal");watches.add(new Watch(string(w,"wire"),string(w,"type"),bool(w,"reliable"),bool(w,"transientLocal")));}
  for(int i=0;i<ps.length();i++){JSONObject s=ps.getJSONObject(i);keys(s,"slot","path");plots.add(new Series(integer(s,"slot"),string(s,"path")));}
  return new Profile(string(j,"id"),string(j,"name"),integer(j,"domain"),string(j,"peer"),watches,plots);
 }
 static BenchProfiles decode(byte[] bytes)throws JSONException{
  if(bytes==null||bytes.length>MAX_BYTES)throw invalid("Arquivo de perfis excede 128 KiB");
  String text;
  try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}catch(CharacterCodingException ex){throw invalid("UTF-8 inválido");}
  int depth=0;boolean quoted=false,escape=false;
  for(char c:text.toCharArray()){if(quoted){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='\"')quoted=false;}else if(c=='\"')quoted=true;else if(c=='{'||c=='['){if(++depth>12)throw invalid("Perfil excessivamente aninhado");}else if(c=='}'||c==']'){if(--depth<0)throw invalid("JSON inválido");}}
  if(depth!=0||quoted)throw invalid("JSON incompleto");
  JSONTokener tokener=new JSONTokener(text);Object value=tokener.nextValue();if(!(value instanceof JSONObject)||tokener.nextClean()!=0)throw invalid("JSON inválido");JSONObject j=(JSONObject)value;
  keys(j,"schema","profiles","applied");if(integer(j,"schema")!=1)throw invalid("Schema de perfis desconhecido");
  JSONArray rows=j.getJSONArray("profiles");if(rows.length()>MAX_PROFILES)throw invalid("Limite: 16 perfis locais");List<Profile> profiles=new ArrayList<>();
  for(int i=0;i<rows.length();i++)profiles.add(profile(rows.getJSONObject(i)));
  return new BenchProfiles(profiles,j.isNull("applied")?null:profile(j.getJSONObject("applied")));
 }
 private static JSONObject json(Profile p)throws JSONException{
  JSONArray watches=new JSONArray(),plots=new JSONArray();
  for(Watch w:p.watches)watches.put(new JSONObject().put("wire",w.wire).put("type",w.type).put("reliable",w.reliable).put("transientLocal",w.transientLocal));
  for(Series s:p.plots)plots.put(new JSONObject().put("slot",s.slot).put("path",s.path));
  return new JSONObject().put("id",p.id).put("name",p.name).put("domain",p.domain).put("peer",p.peer).put("watches",watches).put("plots",plots);
 }
 byte[] encode()throws JSONException{
  JSONArray rows=new JSONArray();for(Profile p:profiles)rows.put(json(p));
  byte[] out=utf8(new JSONObject().put("schema",1).put("profiles",rows).put("applied",applied==null?JSONObject.NULL:json(applied)).toString());
  if(out.length>MAX_BYTES)throw invalid("Arquivo de perfis excede 128 KiB");return out;
 }
}
