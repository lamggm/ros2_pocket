package com.lamggm.rospocket;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RosGraphModelTest {
 static JSONObject fixture() throws Exception {
  JSONObject s=new JSONObject("{\"running\":true,\"graph\":{\"state\":\"received\",\"format\":\"jazzy\"},\"nodes\":[\"/talker\",\"/listener\",\"/other\"],\"topics\":[]}");
  JSONArray topics=s.getJSONArray("topics");
  topics.put(topic("/value","custom/msg/Value",new JSONArray().put(endpoint("w","p1",true,"/talker")).put(endpoint("r","p2",false,"/listener"))));
  topics.put(topic("/unrelated","std_msgs/msg/String",new JSONArray().put(endpoint("u","p3",true,"/other"))));return s;
 }
 static JSONObject endpoint(String id,String participant,boolean writer,String...nodes)throws Exception{return new JSONObject().put("id",id).put("participant",participant).put("writer",writer).put("nodes",new JSONArray(nodes)).put("qos",new JSONObject().put("reliability","reliable"));}
 static JSONObject topic(String name,String type,JSONArray eps)throws Exception{return new JSONObject().put("name",name).put("type",type).put("wireName","rt"+name).put("wireType",type).put("endpoints",eps);}
 static JSONObject project(JSONObject s)throws Exception{
  Class<?> c;
  try{c=Class.forName("com.lamggm.rospocket.RosGraphModel");}catch(ClassNotFoundException e){fail("Graph projection must use the actual advertised endpoint associations");return null;}
  var m=c.getDeclaredMethod("project",JSONObject.class);m.setAccessible(true);return (JSONObject)m.invoke(null,s);
 }
 @Test public void directedEdgesComeFromEndpointAssociationsEvenForCustomTypes()throws Exception{
  JSONObject g=project(fixture());JSONArray vs=g.getJSONArray("vertices"),es=g.getJSONArray("edges");
  assertEquals(5,vs.length());assertEquals(3,es.length());
  for(int i=0;i<es.length();i++){
   JSONObject e=es.getJSONObject(i);assertEquals(e.getString("role").equals("Publica"),e.getString("to").startsWith("t:"));
   assertEquals(1,e.getJSONArray("endpoints").length());
  }
 }
 @Test public void endpointsWithoutConfirmedNodesRemainExplicitlyUnknown()throws Exception{
  JSONObject s=fixture();s.getJSONArray("topics").getJSONObject(0).getJSONArray("endpoints").put(endpoint("unknown","px",false));
  JSONObject g=project(s);boolean unknown=false;
  for(int i=0;i<g.getJSONArray("vertices").length();i++)if(g.getJSONArray("vertices").getJSONObject(i).getString("kind").equals("unknown"))unknown=true;
  assertTrue("Unknown endpoints must not silently vanish or acquire guessed node names",unknown);
  s.put("graph",new JSONObject().put("state","unsupported"));g=project(s);
  for(int i=0;i<g.getJSONArray("vertices").length();i++)assertNotEquals("A rejected graph cannot retain confirmed ROS nodes","node",g.getJSONArray("vertices").getJSONObject(i).getString("kind"));
 }
 @Test public void projectionBudgetPreservesConnectedTopicsBeforeIsolatedNames()throws Exception{
  JSONObject s=fixture();for(int i=0;i<600;i++)s.getJSONArray("nodes").put("/aaa_"+i);
  JSONObject g=project(s);assertTrue("Bound work before serializing the projected graph",g.getJSONArray("vertices").length()<=160);assertEquals(3,g.getJSONArray("edges").length());assertTrue(g.getBoolean("truncated"));
 }
 @Test public void endpointDetailsAreBoundedWithoutLosingTheirCount()throws Exception{
  JSONObject s=fixture();JSONArray eps=s.getJSONArray("topics").getJSONObject(0).getJSONArray("endpoints");for(int i=0;i<40;i++)eps.put(endpoint("extra"+i,"p1",true,"/talker"));
  JSONObject g=project(s);for(int i=0;i<g.getJSONArray("edges").length();i++){
   JSONObject e=g.getJSONArray("edges").getJSONObject(i);if(e.optInt("endpointCount")>8){assertEquals(41,e.getInt("endpointCount"));assertEquals(8,e.getJSONArray("endpoints").length());return;}
  }fail("Aggregate endpoint count must survive a bounded detail list");
 }
}
