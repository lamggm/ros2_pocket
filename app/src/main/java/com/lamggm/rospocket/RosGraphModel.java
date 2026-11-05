package com.lamggm.rospocket;

import org.json.*;
import java.util.*;

/** Bounded projection of advertised associations, never a message-flow claim. */
final class RosGraphModel {
 static final int MAX_VERTICES=160,MAX_EDGES=400,MAX_DETAILS=8,MAX_WORK=8192;
 static String topicId(JSONObject t){return "t:"+new JSONArray().put(t.optString("wireName")).put(t.optString("wireType"));}
 static String nodeId(String participant,String name){return "n:"+new JSONArray().put(participant).put(name);}
 static JSONObject project(JSONObject snapshot) throws JSONException {
  Map<String,JSONObject> vertices=new LinkedHashMap<>(),edges=new LinkedHashMap<>();Set<String> linked=new HashSet<>();
  boolean truncated=false;int work=0;
  if(!snapshot.optBoolean("running"))return result(vertices,edges,false);
  JSONObject graph=snapshot.optJSONObject("graph");boolean confirmed=graph!=null&&"received".equals(graph.optString("state"));
  JSONArray topics=snapshot.optJSONArray("topics");
  outer: if(topics!=null)for(int i=0;i<topics.length();i++){
   if(++work>MAX_WORK){truncated=true;break;}
   JSONObject t=topics.optJSONObject(i);if(t==null)continue;String tid=topicId(t);
   JSONObject topic=new JSONObject().put("id",tid).put("kind","topic").put("label",t.optString("name")).put("type",t.optString("type")).put("wireType",t.optString("wireType"));
   JSONArray eps=t.optJSONArray("endpoints");
   if(eps==null||eps.length()==0){if(vertices.size()<MAX_VERTICES)vertices.put(tid,topic);else truncated=true;continue;}
   for(int j=0;j<eps.length();j++){
    if(++work>MAX_WORK){truncated=true;break outer;}
    JSONObject ep=eps.optJSONObject(j);if(ep==null||!(ep.opt("writer") instanceof Boolean))continue;
    JSONArray names=confirmed?ep.optJSONArray("nodes"):null;boolean unknown=names==null||names.length()==0;
    if(unknown)names=new JSONArray().put("Nó não confirmado");
    for(int k=0;k<names.length();k++){
     if(++work>MAX_WORK){truncated=true;break outer;}
     String name=names.optString(k);if(name.isEmpty())continue;String participant=ep.optString("participant"),nid=unknown?"u:"+ep.optString("id"):nodeId(participant,name);
     boolean pub=ep.optBoolean("writer");String from=pub?nid:tid,to=pub?tid:nid,eid="e:"+new JSONArray().put(from).put(to);
     JSONObject edge=edges.get(eid);
     if(edge==null){
      int extra=(vertices.containsKey(tid)?0:1)+(vertices.containsKey(nid)?0:1);
      if(vertices.size()+extra>MAX_VERTICES||edges.size()>=MAX_EDGES){truncated=true;continue;}
      vertices.put(tid,topic);vertices.put(nid,new JSONObject().put("id",nid).put("kind",unknown?"unknown":"node").put("label",name).put("participant",participant));
      edge=new JSONObject().put("id",eid).put("from",from).put("to",to).put("topic",tid).put("role",pub?"Publica":"Assina").put("endpointCount",0).put("endpoints",new JSONArray());edges.put(eid,edge);
     }
     if(!unknown)linked.add(name);
     edge.put("endpointCount",edge.optInt("endpointCount")+1);
     // Retain only bounded detail summaries; do not duplicate the endpoint's node list.
     JSONArray details=edge.getJSONArray("endpoints");if(details.length()<MAX_DETAILS)details.put(new JSONObject().put("id",ep.optString("id")).put("participant",participant).put("qos",ep.optJSONObject("qos")));
    }
   }
  }
  JSONArray names=confirmed?snapshot.optJSONArray("nodes"):null;
  if(names!=null)for(int i=0;i<names.length();i++){
   if(++work>MAX_WORK){truncated=true;break;}
   String name=names.optString(i);if(name.isEmpty()||linked.contains(name))continue;
   if(vertices.size()>=MAX_VERTICES){truncated=true;break;}
   String id=nodeId("",name);vertices.put(id,new JSONObject().put("id",id).put("kind","node").put("label",name).put("participant",""));
  }
  return result(vertices,edges,truncated);
 }
 private static JSONObject result(Map<String,JSONObject> vs,Map<String,JSONObject> es,boolean truncated)throws JSONException{return new JSONObject().put("vertices",new JSONArray(vs.values())).put("edges",new JSONArray(es.values())).put("truncated",truncated);}
}
