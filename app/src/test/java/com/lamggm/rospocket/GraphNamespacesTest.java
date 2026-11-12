package com.lamggm.rospocket;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GraphNamespacesTest {
 @Test public void circlesEncloseOnlyTheirOwnMembersAndAreBounded(){
  Map<String,double[]> points=new LinkedHashMap<>();points.put("a",new double[]{0,0,20});points.put("b",new double[]{300,0,30});points.put("c",new double[]{1000,0,10});points.put("u",new double[]{50,50,20});
  Map<String,String> owners=new LinkedHashMap<>();owners.put("a","/robo/nav");owners.put("b","/robo/nav");owners.put("c","/robo/perception");
  Map<String,GraphNamespaces.Circle> circles=GraphNamespaces.circles(points,owners);assertEquals(2,circles.size());GraphNamespaces.Circle nav=circles.get("/robo/nav");assertEquals(150,nav.x,0);assertEquals(180,nav.r,0);assertEquals(new LinkedHashSet<>(Arrays.asList("a","b")),nav.members);assertFalse(nav.members.contains("u"));
  for(int i=0;i<60;i++){points.put("extra"+i,new double[]{i,2,1});owners.put("extra"+i,"/ns"+i);}assertEquals(GraphNamespaces.MAX_GROUPS,GraphNamespaces.circles(points,owners).size());assertEquals(64,points.size());
 }
 @Test public void namespaceUsesExactAbsoluteNamesWithoutRepairingUnknownTokens(){
  assertEquals("/robo/navigation",GraphNamespaces.namespaceOf("/robo/navigation/controller"));
  assertEquals("/",GraphNamespaces.namespaceOf("/talker"));
  for(String invalid:new String[]{"Nó não confirmado","relative/name","/robo//node","/robo/safety/","/robo/invalid node","/robo/123invalid"})assertEquals("",GraphNamespaces.namespaceOf(invalid));
 }
}
