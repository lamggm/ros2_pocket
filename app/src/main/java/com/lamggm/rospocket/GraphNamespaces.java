package com.lamggm.rospocket;
import java.util.*;

/** Presentation grouping only; never creates ROS nodes, endpoints or package metadata. */
final class GraphNamespaces {
 static final int MAX_GROUPS=24;
 static final double NEST_MARGIN=56;
 static final class Circle {final double x,y,r;final Set<String> members;Circle(double x,double y,double r,Set<String> ids){this.x=x;this.y=y;this.r=r;this.members=Collections.unmodifiableSet(ids);}}
 static Map<String,Circle> circles(Map<String,double[]> points,Map<String,String> membership){
  Map<String,List<String>> groups=new LinkedHashMap<>();
  for(Map.Entry<String,double[]> e:points.entrySet()){
   String group=membership.get(e.getKey());double[] p=e.getValue();
   if(group==null||group.isEmpty()||p==null||p.length<2||!Double.isFinite(p[0])||!Double.isFinite(p[1]))continue;
   if(!groups.containsKey(group)&&groups.size()>=MAX_GROUPS)continue;
   groups.computeIfAbsent(group,k->new ArrayList<>()).add(e.getKey());
  }
  List<String> ordered=new ArrayList<>(groups.keySet());
  ordered.sort(Comparator.comparingInt(String::length)); // a parent always precedes its children
  Map<String,String> parents=parents(ordered);
  Map<String,List<String>> children=new HashMap<>();
  for(String ns:ordered)children.computeIfAbsent(parents.get(ns),k->new ArrayList<>()).add(ns);
  // Even when the display cap omits an exact child, retain it inside an admitted parent.
  for(String id:points.keySet()){
   String ns=membership.get(id);double[] p=points.get(id);
   if(ns==null||groups.containsKey(ns)||p==null||p.length<2||!Double.isFinite(p[0])||!Double.isFinite(p[1]))continue;
   String parent=parentOf(ns,ordered);if(!parent.isEmpty())groups.get(parent).add(id);
  }
  Map<String,Circle> built=new HashMap<>();
  for(int i=ordered.size()-1;i>=0;i--){
   String ns=ordered.get(i);List<String> childNames=children.getOrDefault(ns,Collections.emptyList());
   Set<String> members=new LinkedHashSet<>(groups.get(ns));for(String child:childNames)members.addAll(built.get(child).members);
   double x=0,y=0;for(String id:members){double[] p=points.get(id);x+=p[0]/members.size();y+=p[1]/members.size();}
   double r=0;for(String id:groups.get(ns)){double[] p=points.get(id);double pad=p.length>2&&Double.isFinite(p[2])&&p[2]>=0?p[2]:120;r=Math.max(r,Math.hypot(p[0]-x,p[1]-y)+pad);}
   for(String name:childNames){Circle child=built.get(name);r=Math.max(r,Math.hypot(child.x-x,child.y-y)+child.r+NEST_MARGIN);}
   built.put(ns,new Circle(x,y,r,members));
  }
  Map<String,Circle> result=new LinkedHashMap<>();for(String ns:ordered)result.put(ns,built.get(ns));
  return result;
 }
 // The global '/' bubble stays for exact root members, not as a duplicate of the scene outline.
 static boolean contains(String group,String ns){return ns.equals(group)||(group.length()>1&&ns.length()>group.length()&&ns.charAt(group.length())=='/'&&ns.startsWith(group));}
 private static String parentOf(String ns,Collection<String> groups){String parent="";for(String group:groups)if(group.length()<ns.length()&&group.length()>parent.length()&&contains(group,ns))parent=group;return parent;}
 static Map<String,String> parents(Collection<String> groups){Map<String,String> parents=new HashMap<>();for(String group:groups)parents.put(group,parentOf(group,groups));return parents;}
 static String namespaceOf(String name){if(name==null||name.length()>4096||!name.matches("/(?:[A-Za-z_][A-Za-z0-9_]*/)*[A-Za-z_][A-Za-z0-9_]*"))return "";int slash=name.lastIndexOf('/');return slash==0?"/":name.substring(0,slash);}
}
