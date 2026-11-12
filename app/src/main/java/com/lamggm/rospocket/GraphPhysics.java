package com.lamggm.rospocket;
import java.util.*;

/** Deterministic, bounded visual layout only. No Android, ROS, timers or networking. */
final class GraphPhysics {
 static final int MAX_BODIES=160,MAX_LINKS=400,MAX_STEPS=360;
 static final class Body {double x,y,vx,vy,fx,fy;boolean pinned,held;Body(double x,double y){this.x=x;this.y=y;}}
 final Map<String,Body> bodies=new LinkedHashMap<>();
 final Map<String,String> namespaces=new LinkedHashMap<>();
 void setNamespaces(Map<String,String> groups){Map<String,String> next=new LinkedHashMap<>();Set<String> accepted=new LinkedHashSet<>();for(String id:bodies.keySet()){String ns=groups.get(id);if(ns==null||ns.isEmpty())continue;if(!accepted.contains(ns)&&accepted.size()>=GraphNamespaces.MAX_GROUPS)continue;accepted.add(ns);next.put(id,ns);}if(!namespaces.equals(next)){namespaces.clear();namespaces.putAll(next);wake();}}
 Map<String,GraphNamespaces.Circle> namespaceCircles(){Map<String,double[]> points=new LinkedHashMap<>();for(Map.Entry<String,Body> e:bodies.entrySet())points.put(e.getKey(),new double[]{e.getValue().x,e.getValue().y,120});return GraphNamespaces.circles(points,namespaces);}
 private List<String[]> links=new ArrayList<>();
 boolean enabled=true,awake;
 int steps;
 double alpha=1,repulsion=1,linkStrength=.025,linkDistance=340,centering=.001;
 private double centerX,centerY;
 private String topology="";
 void sync(Map<String,double[]> seeds,List<String[]> connections){
  StringBuilder key=new StringBuilder();for(String id:seeds.keySet())key.append(id.length()).append(':').append(id);List<String[]> accepted=new ArrayList<>();
  for(String[] pair:connections)if(accepted.size()<MAX_LINKS&&pair.length==2&&seeds.containsKey(pair[0])&&seeds.containsKey(pair[1])&&!pair[0].equals(pair[1])){accepted.add(pair.clone());key.append('|').append(pair[0].length()).append(':').append(pair[0]).append(pair[1].length()).append(':').append(pair[1]);}
  boolean first=bodies.isEmpty();bodies.keySet().retainAll(seeds.keySet());int n=0;for(Map.Entry<String,double[]> e:seeds.entrySet()){if(n++>=MAX_BODIES)break;double[] p=e.getValue();if(p.length<2||!Double.isFinite(p[0])||!Double.isFinite(p[1]))continue;bodies.putIfAbsent(e.getKey(),new Body(p[0],p[1]));}
  links=accepted;if(first&&!bodies.isEmpty()){centerX=0;centerY=0;for(Body b:bodies.values()){centerX+=b.x;centerY+=b.y;}centerX/=bodies.size();centerY/=bodies.size();}
  String next=key.toString();if(!next.equals(topology)){topology=next;wake();}if(bodies.isEmpty())awake=false;
 }
 void wake(){awake=!bodies.isEmpty();alpha=1;steps=0;}
 void clear(){bodies.clear();namespaces.clear();links.clear();topology="";awake=false;steps=0;}
 void hold(String id,boolean held){Body b=bodies.get(id);if(b!=null){b.held=held;b.vx=b.vy=0;}}
 void move(String id,double x,double y){Body b=bodies.get(id);if(b!=null&&Double.isFinite(x)&&Double.isFinite(y)){b.x=Math.max(-100000,Math.min(100000,x));b.y=Math.max(-100000,Math.min(100000,y));b.vx=b.vy=0;wake();}}
 void pin(String id,boolean fixed){Body b=bodies.get(id);if(b!=null){b.pinned=fixed;b.vx=b.vy=0;wake();}}
 boolean step(){
  if(!enabled||!awake||bodies.isEmpty())return false;
  List<Body> a=new ArrayList<>(bodies.values());for(Body b:a){b.fx=(centerX-b.x)*centering*alpha;b.fy=(centerY-b.y)*centering*alpha;}
  for(int i=0;i<a.size();i++)for(int j=i+1;j<a.size();j++){
   Body p=a.get(i),q=a.get(j);double dx=q.x-p.x,dy=q.y-p.y;if(Math.abs(dx)+Math.abs(dy)<.001){dx=1;dy=((i+j)&1)==0?1:-1;}
   double d2=dx*dx+dy*dy+1600,d=Math.sqrt(d2),force=16000*repulsion*alpha/d2,fx=force*dx/d,fy=force*dy/d;
   p.fx-=fx;p.fy-=fy;q.fx+=fx;q.fy+=fy;
   double ox=286-Math.abs(dx),oy=90-Math.abs(dy);if(ox>0&&oy>0){double c=Math.max(.12,alpha)*.22;if(ox<oy){double push=Math.copySign(ox*c,dx);p.fx-=push;q.fx+=push;}else{double push=Math.copySign(oy*c,dy);p.fy-=push;q.fy+=push;}}
  }
  for(String[] edge:links){Body p=bodies.get(edge[0]),q=bodies.get(edge[1]);if(p==null||q==null)continue;double dx=q.x-p.x,dy=q.y-p.y,d=Math.max(1,Math.hypot(dx,dy)),f=(d-linkDistance)*linkStrength*alpha,fx=f*dx/d,fy=f*dy/d;p.fx+=fx;p.fy+=fy;q.fx-=fx;q.fy-=fy;}
  for(GraphNamespaces.Circle group:namespaceCircles().values())for(String id:group.members){Body b=bodies.get(id);double dx=group.x-b.x,dy=group.y-b.y,d=Math.max(1,Math.hypot(dx,dy)),f=Math.max(0,d-80)*.035*alpha;b.fx+=f*dx/d;b.fy+=f*dy/d;}
  for(Body b:a){if(b.pinned||b.held){b.vx=b.vy=0;continue;}b.vx=Math.max(-16,Math.min(16,(b.vx+b.fx)*.78));b.vy=Math.max(-16,Math.min(16,(b.vy+b.fy)*.78));b.x=Math.max(-100000,Math.min(100000,b.x+b.vx));b.y=Math.max(-100000,Math.min(100000,b.y+b.vy));}
  if(repulsion>0&&!namespaces.isEmpty())separateNamespaces();
  alpha*=.97;if(++steps>=MAX_STEPS||alpha<.003){awake=false;for(Body b:a)b.vx=b.vy=0;}return awake;
 }
 void separateNamespaces(){
  Map<String,GraphNamespaces.Circle> shapes=namespaceCircles();List<String> ids=new ArrayList<>(shapes.keySet());
  for(int pass=0;pass<6;pass++)for(int i=0;i<ids.size();i++)for(int j=i+1;j<ids.size();j++){
   String ia=ids.get(i),ib=ids.get(j);GraphNamespaces.Circle a=shapes.get(ia),b=shapes.get(ib);double dx=b.x-a.x,dy=b.y-a.y,d=Math.hypot(dx,dy),overlap=a.r+b.r+40-d;if(overlap<=0)continue;
   boolean af=groupFree(a),bf=groupFree(b);int n=(af?1:0)+(bf?1:0);if(n==0)continue;if(d<.001){double angle=(i*31+j+1)*2.399963229728653;dx=Math.cos(angle);dy=Math.sin(angle);d=1;}
   double sx=dx/d*overlap/n,sy=dy/d*overlap/n;
   if(af&&translateGroup(a,-sx,-sy))shapes.put(ia,new GraphNamespaces.Circle(a.x-sx,a.y-sy,a.r,a.members));
   if(bf&&translateGroup(b,sx,sy))shapes.put(ib,new GraphNamespaces.Circle(b.x+sx,b.y+sy,b.r,b.members));
  }
 }
 boolean groupFree(GraphNamespaces.Circle group){for(String id:group.members){Body b=bodies.get(id);if(b.pinned||b.held)return false;}return true;}
 boolean translateGroup(GraphNamespaces.Circle group,double dx,double dy){for(String id:group.members){Body b=bodies.get(id);if(Math.abs(b.x+dx)>100000||Math.abs(b.y+dy)>100000)return false;}for(String id:group.members){Body b=bodies.get(id);b.x+=dx;b.y+=dy;b.vx*=.5;b.vy*=.5;}return true;}
}
