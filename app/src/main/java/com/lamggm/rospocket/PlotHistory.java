package com.lamggm.rospocket;

import java.util.*;

/** Bounded raw-value overlay. All times are receive-monotonic nanoseconds, never wall time. */
final class PlotHistory {
 static final int MAX_SERIES=4;
 static final class Point {
  final long time; final Double value; final boolean breakBefore;
  Point(long t,Double v,boolean b){time=t;value=v;breakBefore=b;}
 }
 static final class Series {
  final String id,path,unit; final List<Point> points;
  Series(String i,String p,String u,List<Point> v){id=i;path=p;unit=u;points=Collections.unmodifiableList(v);}
 }
 private static final class Ring {
  final String path,unit; final ArrayDeque<Point> points=new ArrayDeque<>();Long source;
  Ring(String p,String u){path=p;unit=u;}
 }
 private final LinkedHashMap<String,Ring> rings=new LinkedHashMap<>();
 private final int capacity; private final long window;
 PlotHistory(int capacity,long window){
  if(capacity<1||capacity>2048||window<1||window>300_000_000_000L)throw new IllegalArgumentException("plot budget");
  this.capacity=capacity;this.window=window;
 }
 synchronized boolean select(String id,String path,String unit){
  if(id==null||path==null||id.length()>1024||path.length()>256||(unit!=null&&unit.length()>64))return false;
  if(rings.containsKey(id))return true;
  if(rings.size()==MAX_SERIES)return false;
  rings.put(id,new Ring(path,unit));return true;
 }
 synchronized boolean remove(String id){return rings.remove(id)!=null;}
 synchronized void clear(){for(Ring r:rings.values()){r.points.clear();r.source=null;}}
 synchronized void append(String id,long time,Double value,Long source){
  Ring r=rings.get(id);if(r==null||time<0)return;
  Point last=r.points.peekLast();
  if(last!=null&&time<last.time){r.points.clear();r.source=null;last=null;}
  if(last!=null&&time==last.time)return;
  boolean gap=last==null||time-last.time>3_000_000_000L||(source!=null&&r.source!=null&&source<r.source);
  if(value!=null&&!Double.isFinite(value))value=null;
  r.points.addLast(new Point(time,value,gap));r.source=source;
  while(r.points.size()>capacity)r.points.removeFirst();
  for(Ring ring:rings.values())while(!ring.points.isEmpty()&&time-ring.points.peekFirst().time>window)ring.points.removeFirst();
 }
 synchronized List<Point> points(String id){Ring r=rings.get(id);return r==null?Collections.emptyList():new ArrayList<>(r.points);}
 synchronized List<Series> snapshot(){
  List<Series> result=new ArrayList<>();for(Map.Entry<String,Ring> e:rings.entrySet())result.add(new Series(e.getKey(),e.getValue().path,e.getValue().unit,new ArrayList<>(e.getValue().points)));return Collections.unmodifiableList(result);
 }
}
