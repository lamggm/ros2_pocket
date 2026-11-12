package com.lamggm.rospocket;
import java.util.*;

/** Presentation grouping only; never creates ROS nodes, endpoints or package metadata. */
final class GraphNamespaces {
 static final int MAX_GROUPS=24;
 static final class Circle {final double x,y,r;final Set<String> members;Circle(double x,double y,double r,Set<String> ids){this.x=x;this.y=y;this.r=r;this.members=Collections.unmodifiableSet(ids);}}
 static Map<String,Circle> circles(Map<String,double[]> points,Map<String,String> membership){
  Map<String,List<String>> groups=new LinkedHashMap<>();
  for(Map.Entry<String,double[]> e:points.entrySet()){
   String group=membership.get(e.getKey());double[] p=e.getValue();
   if(group==null||group.isEmpty()||p==null||p.length<2||!Double.isFinite(p[0])||!Double.isFinite(p[1]))continue;
   if(!groups.containsKey(group)&&groups.size()>=MAX_GROUPS)continue;
   groups.computeIfAbsent(group,k->new ArrayList<>()).add(e.getKey());
  }
  Map<String,Circle> result=new LinkedHashMap<>();
  for(Map.Entry<String,List<String>> e:groups.entrySet()){
   double x=0,y=0;for(String id:e.getValue()){double[] p=points.get(id);x+=p[0]/e.getValue().size();y+=p[1]/e.getValue().size();}
   double r=0;for(String id:e.getValue()){double[] p=points.get(id);double pad=p.length>2&&Double.isFinite(p[2])&&p[2]>=0?p[2]:120;r=Math.max(r,Math.hypot(p[0]-x,p[1]-y)+pad);}
   result.put(e.getKey(),new Circle(x,y,r,new LinkedHashSet<>(e.getValue())));
  }
  return result;
 }
 static String namespaceOf(String name){if(name==null||name.length()>4096||!name.matches("/(?:[A-Za-z_][A-Za-z0-9_]*/)*[A-Za-z_][A-Za-z0-9_]*"))return "";int slash=name.lastIndexOf('/');return slash==0?"/":name.substring(0,slash);}
}
