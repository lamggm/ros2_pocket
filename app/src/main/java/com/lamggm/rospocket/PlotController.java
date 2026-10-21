package com.lamggm.rospocket;

import org.json.*;
import java.util.*;

/** Intake contract independent of reader management and Android rendering. */
final class PlotController {
 static final int MAX_FIELDS=64;
 final PlotHistory history=new PlotHistory(600,30_000_000_000L);
 static final class Field {
  final String watch,path,unit;final Double value;
  Field(String w,String p,String u,Double v){watch=w;path=p;unit=u;value=v;}
  String id(){return watch+"\n"+path;}
 }
 // One immutable frame per actual received message or latest-sample snapshot.
 // Future 05/06 adapters supply typed paths/watch identity here, never display text.
 static final class Frame {
  final String watch;final long epoch,sequence,received;final Long source;final List<Field> fields;
  Frame(String w,long e,long s,long r,Long source,List<Field> fields){
   watch=w;epoch=e;sequence=s;received=r;this.source=source;
   this.fields=Collections.unmodifiableList(new ArrayList<>(fields.subList(0,Math.min(MAX_FIELDS,fields.size()))));
  }
 }
 private final LinkedHashMap<String,Field> available=new LinkedHashMap<>();
 private final LinkedHashMap<String,Long> sequences=new LinkedHashMap<>();
 private long nativeSession=-1,nativeReader=-1;
 private long nativeEpoch=-1,revision;private String nativeIdentity="";
 synchronized long revision(){return revision;}
 synchronized void reset(long epoch) {
  if(epoch<nativeEpoch)return;nativeEpoch=epoch;nativeSession=-1;nativeReader=-1;nativeIdentity="";sequences.clear();available.clear();history.clear();revision++;
 }
 synchronized List<Field> fields(){return new ArrayList<>(available.values());}
 synchronized void retire(String watch) {
  sequences.remove(watch);available.entrySet().removeIf(e->e.getValue().watch.equals(watch));
  for(PlotHistory.Series s:history.snapshot())if(s.id.startsWith(watch+"\n"))history.remove(s.id);
  revision++;
 }
 synchronized boolean select(String watch,String path){Field f=available.get(watch+"\n"+path);return f!=null&&history.select(f.id(),f.path,f.unit);}
 synchronized boolean selected(String id){for(PlotHistory.Series s:history.snapshot())if(s.id.equals(id))return true;return false;}
 synchronized void accept(Frame frame){
  if(frame.watch==null||frame.watch.length()>512||frame.epoch<nativeEpoch||frame.received<0||frame.sequence<1)return;
  if(frame.epoch>nativeEpoch)reset(frame.epoch);
  String key=frame.watch;Long previous=sequences.get(key);
  if(previous!=null&&frame.sequence<=previous)return;
  if(previous==null&&sequences.size()==4)return;
  sequences.put(key,frame.sequence);
  available.entrySet().removeIf(e->e.getValue().watch.equals(key));
  Set<String> present=new HashSet<>();
  for(Field field:frame.fields){
   if(!field.watch.equals(key)||field.path.length()>256||key.length()>512||(field.unit!=null&&field.unit.length()>64))continue;
   if(available.size()<MAX_FIELDS*4)available.put(field.id(),field);
   present.add(field.id());history.append(field.id(),frame.received,field.value,frame.source);
  }
  for(PlotHistory.Series s:history.snapshot())if(s.id.startsWith(key+"\n")&&!present.contains(s.id))history.append(s.id,frame.received,null,frame.source);
 }
 synchronized void acceptNative(JSONObject snapshot,long epoch,boolean pending){
  if(epoch<nativeEpoch)return;
  long session=snapshot.optLong("sessionGeneration"),reader=snapshot.optLong("readerGeneration");
  if(epoch==nativeEpoch&&(session<nativeSession||(session==nativeSession&&reader<nativeReader)))return;
  String identity=snapshot.optLong("sessionGeneration")+":"+snapshot.optLong("readerGeneration");
  if(epoch>nativeEpoch||!identity.equals(nativeIdentity)){reset(epoch);nativeIdentity=identity;nativeSession=session;nativeReader=reader;}
  if(pending||!snapshot.optBoolean("running")||!snapshot.optBoolean("readerActive")||!snapshot.optBoolean("fresh")
     ||!"receiving".equals(snapshot.optString("subscriptionState"))||!snapshot.optString("error").isEmpty()){
   available.clear();history.clear();revision++;return;
  }
  JSONArray fields=snapshot.optJSONArray("numericFields");
  String watch=snapshot.optString("subscription");
  Object received=snapshot.opt("receivedMonotonicNs"),source=snapshot.opt("sourceTimestampNs");
  if(fields==null||!(received instanceof Number)||watch.isEmpty())return;
  List<Field> typed=new ArrayList<>();
  for(int i=0;i<Math.min(MAX_FIELDS,fields.length());i++){
   JSONObject f=fields.optJSONObject(i);if(f==null)continue;
   Object value=f.opt("value"),unit=f.opt("unit");
   typed.add(new Field(watch,f.optString("path"),unit instanceof String?(String)unit:null,
    value instanceof Number&&"finite".equals(f.optString("state"))?((Number)value).doubleValue():null));
  }
  accept(new Frame(watch,epoch,snapshot.optLong("count"),((Number)received).longValue(),source instanceof Number?((Number)source).longValue():null,typed));
 }
}
