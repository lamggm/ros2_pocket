package com.lamggm.rospocket;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** One metadata reader, confined to MainActivity's existing DDS worker. */
final class RobotContext {
 private static final class Selection {final String wire;Selection(String w){wire=w;}}
 private volatile Selection selection=new Selection("");
 static final class Source {
  final String wire,name,key;final boolean reliable,retained,supported;final int writers;
  Source(JSONObject row){wire=row.optString("wireName");name=row.optString("name");writers=row.optInt("publishers");supported="std_msgs::msg::dds_::String_".equals(row.optString("wireType"));JSONArray eps=row.optJSONArray("endpoints");JSONObject qos=null;String id="";if(eps!=null)for(int i=0;i<eps.length();i++){JSONObject ep=eps.optJSONObject(i);if(ep!=null&&ep.opt("writer")==Boolean.TRUE){id=ep.optString("id");qos=ep.optJSONObject("qos");break;}}String r=qos==null?"unknown":qos.optString("reliability"),d=qos==null?"unknown":qos.optString("durability");reliable="reliable".equals(r);retained=Arrays.asList("transient_local","transient","persistent").contains(d);key=wire+"\n"+id+"\n"+r+"\n"+d;}
 }
 static final class Snapshot {
  final List<Source> sources;final String status,source;final RobotDescription model;private final Selection owner;
  Snapshot(List<Source> list,String message,String provenance,RobotDescription value,Selection s){sources=Collections.unmodifiableList(new ArrayList<>(list));status=message;source=provenance;model=value;owner=s;}
 }
 static final class PoseSnapshot {final RobotTf.Pose pose;final String fingerprint,source;private final Selection owner;PoseSnapshot(RobotTf.Pose p,String f,Selection s,String origin){pose=p;fingerprint=f;owner=s;source=origin;}}
 boolean current(PoseSnapshot s){return s!=null&&s.owner==selection;}
 private Selection tfBindingOwner;private String tfBoundKey="";private boolean tfRevoked;private Source tfDynamic,tfStatic;
 private RobotTf tree=new RobotTf();private String tfKey="",tfError="",tfSources="";
 PoseSnapshot pollTf(NativeBridge.Api api,long now){Selection owner=selection;if(appliedSelection!=owner||model==null)return null;RobotDescription robot=model;RobotTf.Pose pose;
  try{if(!tfKey.isEmpty()){JSONObject raw=NativeBridge.Api.json(api.pollTf());if(!raw.optString("error").isEmpty()||!raw.optBoolean("running")){tree.accept(null,now);tfError=raw.optString("error","TF indisponível");}else tree.accept(raw.optJSONArray("rows"),now);}Set<String> links=new LinkedHashSet<>();for(RobotDescription.Visual v:robot.visuals)links.add(v.link);pose=tree.solve(robot.rootLink,links,now);if(!tfError.isEmpty())pose=new RobotTf.Pose(false,tfError,0,pose.revision,Collections.emptyMap());}catch(Exception e){pose=new RobotTf.Pose(false,"TF indisponível",0,0,Collections.emptyMap());}if(owner!=selection)return null;return new PoseSnapshot(pose,robot.fingerprint,owner,tfSources);
 }
 private void configureTf(NativeBridge.Api api,JSONArray topics,Selection owner,BooleanSupplier admitted){
  if(selection!=owner||!admitted.getAsBoolean())return;
  if(tfBindingOwner!=owner){tfBindingOwner=owner;tfBoundKey="";tfRevoked=false;tfDynamic=tfStatic=null;tree=new RobotTf();}
  if(tfRevoked){if(!tfKey.isEmpty())api.stopTf();tfKey="";tfError="Autoridade/configuração TF mudou · reconecte";return;}
  if(model==null)return;List<Source> dynamic=new ArrayList<>(),fixed=new ArrayList<>();if(topics!=null)for(int i=0;i<topics.length();i++){JSONObject row=topics.optJSONObject(i);if(row==null||!"tf2_msgs::msg::dds_::TFMessage_".equals(row.optString("wireType"))||row.optInt("publishers")==0)continue;String name=row.optString("name");if(!row.optString("wireName").equals("rt"+name)||name.length()>509)continue;if(name.endsWith("/tf"))dynamic.add(new Source(row));if(name.endsWith("/tf_static"))fixed.add(new Source(row));}
  if(dynamic.size()>1||fixed.size()>1){if(!tfKey.isEmpty())api.stopTf();tfKey="";tfRevoked=!tfBoundKey.isEmpty();tfError="TF ambíguo: vários tópicos descobertos";return;}Source d=dynamic.isEmpty()?tfDynamic:dynamic.get(0),s=fixed.isEmpty()?tfStatic:fixed.get(0);String key=d==null&&s==null?"":model.fingerprint+"|"+(d==null?"":d.key)+"|"+(s==null?"":s.key);if(!tfBoundKey.isEmpty()&&!key.equals(tfBoundKey)){api.stopTf();tfKey="";tfRevoked=true;tfError="Autoridade/configuração TF mudou · reconecte";return;}if(key.equals(tfKey)){if(key.isEmpty())tfError="";return;}api.stopTf();tfKey="";tfError="";if(key.isEmpty())return;
  try{if(!admitted.getAsBoolean()||selection!=owner)return;JSONObject answer=NativeBridge.Api.json(api.startTf(d==null?"":d.wire,s==null?"":s.wire,s!=null&&s.reliable,s!=null&&s.retained));if(!admitted.getAsBoolean()||selection!=owner){api.stopTf();return;}if(!answer.optBoolean("ok")){tfError=answer.optString("error","TF indisponível");return;}tfKey=key;tfBoundKey=key;tfDynamic=d;tfStatic=s;tfSources=(d==null?"":d.name)+(s==null?"":" + "+s.name);}catch(Exception e){api.stopTf();tfError="TF indisponível";}
 }
 void select(String wire){selection=new Selection(wire);}
 boolean current(Snapshot s){return s!=null&&s.owner==selection;}
 private String applied="",parseError="";private long sequence=-1;private Selection appliedSelection;private RobotDescription model;
 void resetWorker(){applied="";sequence=-1;model=null;parseError="";appliedSelection=null;tfKey="";tfError="";tfSources="";}
 private static List<Source> sources(JSONArray rows){List<Source> out=new ArrayList<>();if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;String n=r.optString("name"),w=r.optString("wireName");if(n.endsWith("/robot_description")&&w.equals("rt"+n)&&w.length()<=512&&r.optInt("publishers")>0)out.add(new Source(r));}out.sort(Comparator.comparing(s->s.wire));return out;}
 Snapshot observe(NativeBridge.Api api,int domain,String address,String peer,String mode,String format,JSONArray topics,BooleanSupplier admitted){
  Selection owner=selection;List<Source> all=sources(topics);Source chosen=null;if(!owner.wire.isEmpty()){for(Source s:all)if(s.wire.equals(owner.wire))chosen=s;}else if(all.size()==1)chosen=all.get(0);
  if(chosen==null||!chosen.supported||chosen.writers!=1){if(!applied.isEmpty())api.stopDescription();resetWorker();String why=all.isEmpty()?"Aguardando tópico robot_description existente":chosen==null?(owner.wire.isEmpty()?"Selecione a descrição: vários robôs descobertos":"Descrição selecionada indisponível"):!chosen.supported?"robot_description com tipo não suportado (esperado std_msgs/String)":"Descrição ambígua: vários publicadores no mesmo tópico";return new Snapshot(all,why,"",null,owner);}
  String provenance=chosen.name+" · "+(chosen.reliable?"reliable":"best effort")+" / "+(chosen.retained?"transient local":"volatile");
  try{
   if(!admitted.getAsBoolean()||selection!=owner)return new Snapshot(all,"Aguardando conexão atual","",null,owner);
   if(!chosen.key.equals(applied)||appliedSelection!=owner){api.stopDescription();resetWorker();JSONObject answer=NativeBridge.Api.json(api.startDescription(domain,address,peer,chosen.wire,chosen.reliable,chosen.retained,mode,format));if(!admitted.getAsBoolean()||selection!=owner){api.stopDescription();return new Snapshot(all,"Seleção cancelada","",null,owner);}if(!answer.optBoolean("ok"))throw new IllegalArgumentException(answer.optString("error","Leitor de descrição indisponível"));applied=chosen.key;appliedSelection=owner;}
   JSONObject raw=NativeBridge.Api.json(api.pollDescription());if(!admitted.getAsBoolean()||selection!=owner)return new Snapshot(all,"Seleção cancelada","",null,owner);
   String error=raw.optString("error");if(!error.isEmpty()||!raw.optBoolean("running")){model=null;sequence=-1;return new Snapshot(all,error.isEmpty()?"Leitor de descrição indisponível":error,provenance,null,owner);}
   long next=raw.optLong("sequence");if(next!=sequence){sequence=next;model=null;parseError="";String xml=raw.optString("xml");if(!xml.isEmpty())try{model=RobotDescription.parse(xml);}catch(IllegalArgumentException e){parseError="URDF inválido: "+e.getMessage();}}
   configureTf(api,topics,owner,admitted);
   String state=!parseError.isEmpty()?parseError:model==null?"Aguardando URDF"+(chosen.retained?" retido":" · fonte volatile, aguardando nova publicação"):model.visuals.isEmpty()?"Descrição recebida · sem elementos visuais":model.missingMeshes.isEmpty()?"Modelo completo · primitivas URDF":"Modelo parcial · "+model.missingMeshes.size()+" malha(s) não recebida(s)";
   if(model!=null&&raw.optInt("matched")==0)state+=" · descrição retida, sem publicador conectado";
   return new Snapshot(all,state,provenance,model,owner);
  }catch(Exception e){model=null;return new Snapshot(all,"Descrição indisponível: "+e.getMessage(),provenance,null,owner);}
 }
}
