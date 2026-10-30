package com.lamggm.rospocket;

import java.util.*;

/** Bounded raw-value overlay. All times are receive-monotonic nanoseconds, never wall time. */
final class PlotHistory {
 static final int MAX_SERIES=4;
 static final class Point {
  final long time; final Double value; final boolean breakBefore; final Long source;
  Point(long t,Double v,boolean b){this(t,v,b,null);}
  Point(long t,Double v,boolean b,Long s){time=t;value=v;breakBefore=b;source=s;}
 }
 static final class Source {
  static final Source UNKNOWN=new Source(-1,-1,-1);
  final int slot;final long session,reader;
  final String topic,type;
  final Long knownLocalDiscarded;
  Source(int slot,long session,long reader){this(slot,session,reader,"","");}
  Source(int slot,long session,long reader,String topic,String type){this(slot,session,reader,topic,type,null);}
  Source(int slot,long session,long reader,String topic,String type,Long dropped){this.slot=slot;this.session=session;this.reader=reader;this.topic=topic;this.type=type;knownLocalDiscarded=dropped;}
  boolean same(Source other){return slot==other.slot&&session==other.session&&reader==other.reader;}
 }
 static final class Series {
  final String id,path,unit; final List<Point> points;final Source provenance;
  Series(String i,String p,String u,List<Point> v,Source source){id=i;path=p;unit=u;points=Collections.unmodifiableList(v);provenance=source;}
 }
 private static final class Ring {
  final String path,unit; final ArrayDeque<Point> points=new ArrayDeque<>();Long source,lastDiscard;Source provenance;
  Ring(String p,String u,Source identity){path=p;unit=u;provenance=identity;}
 }
 private final LinkedHashMap<String,Ring> rings=new LinkedHashMap<>();
 final int capacity; final long window;
 PlotHistory(int capacity,long window){
  if(capacity<1||capacity>2048||window<1||window>300_000_000_000L)throw new IllegalArgumentException("plot budget");
  this.capacity=capacity;this.window=window;
 }
 synchronized boolean select(String id,String path,String unit){return select(id,path,unit,Source.UNKNOWN);}
 synchronized boolean select(String id,String path,String unit,Source identity){
  if(id==null||path==null||id.length()>1024||path.length()>256||(unit!=null&&unit.length()>64))return false;
  if(rings.containsKey(id))return true;
  if(rings.size()==MAX_SERIES)return false;
  rings.put(id,new Ring(path,unit,identity));return true;
 }
 synchronized boolean remove(String id){return rings.remove(id)!=null;}
 synchronized void clear(){for(Ring r:rings.values()){r.points.clear();r.source=null;r.lastDiscard=null;}}
 synchronized void observeSource(Source identity){for(Ring r:rings.values())if(r.provenance.same(identity))r.provenance=identity;}
 synchronized void append(String id,long time,Double value,Long source){
  Ring r=rings.get(id);if(r!=null)append(id,time,value,source,r.provenance);
 }
 synchronized void append(String id,long time,Double value,Long source,Source identity){
  Ring r=rings.get(id);if(r==null||time<0)return;
  if(!r.provenance.same(identity)){r.points.clear();r.source=null;r.lastDiscard=null;}r.provenance=identity;
  Point last=r.points.peekLast();
  if(last!=null&&time<last.time){r.points.clear();r.source=null;r.lastDiscard=null;last=null;}
  // Native batches are already deduplicated by reader sequence. A monotonic
  // clock can repeat; retaining that sample is essential for nonfinite gaps.
  if(last!=null&&time==last.time&&identity.knownLocalDiscarded==null)return;
  boolean gap=last==null||time-last.time>3_000_000_000L||(source!=null&&r.source!=null&&source<r.source)
   ||(identity.knownLocalDiscarded!=null&&r.lastDiscard!=null&&identity.knownLocalDiscarded>r.lastDiscard);
  if(value!=null&&!Double.isFinite(value))value=null;
  r.points.addLast(new Point(time,value,gap,source));r.source=source;r.lastDiscard=identity.knownLocalDiscarded;
  while(r.points.size()>capacity)r.points.removeFirst();
  for(Ring ring:rings.values())while(!ring.points.isEmpty()&&time-ring.points.peekFirst().time>window)ring.points.removeFirst();
 }
 synchronized List<Point> points(String id){Ring r=rings.get(id);return r==null?Collections.emptyList():new ArrayList<>(r.points);}
 synchronized List<Series> snapshot(){
  List<Series> result=new ArrayList<>();for(Map.Entry<String,Ring> e:rings.entrySet())result.add(new Series(e.getKey(),e.getValue().path,e.getValue().unit,new ArrayList<>(e.getValue().points),e.getValue().provenance));return Collections.unmodifiableList(result);
 }
}
