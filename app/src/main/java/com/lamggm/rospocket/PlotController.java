package com.lamggm.rospocket;

import org.json.*;
import java.util.*;

/** Intake contract independent of reader management and Android rendering. */
final class PlotController {
 static final int MAX_FIELDS=192;
 final PlotHistory history=new PlotHistory(600,30_000_000_000L);
 static final class Field {
  final String watch,path,unit;final Double value;
  Field(String w,String p,String u,Double v){watch=w;path=p;unit=u;value=v;}
  String id(){return watch+"\n"+path;}
 }
 // One immutable frame per actual received message or latest-sample snapshot.
 // Typed paths/watch identity come from native structured values, never display text.
 static final class Frame {
  final String watch;final long epoch,sequence,received;final Long source;final List<Field> fields;final PlotHistory.Source provenance;
  Frame(String w,long e,long s,long r,Long source,List<Field> fields){
   this(w,e,s,r,source,fields,PlotHistory.Source.UNKNOWN);
  }
  Frame(String w,long e,long s,long r,Long source,List<Field> fields,PlotHistory.Source identity){
   watch=w;epoch=e;sequence=s;received=r;this.source=source;provenance=identity;
   this.fields=Collections.unmodifiableList(new ArrayList<>(fields.subList(0,Math.min(MAX_FIELDS,fields.size()))));
  }
 }
 private final LinkedHashMap<String,Field> available=new LinkedHashMap<>();
 private final LinkedHashMap<String,Long> sequences=new LinkedHashMap<>();
 private final Map<String,PlotHistory.Source> sources=new HashMap<>();
 private BenchProfiles.Profile profile;
 private final List<BenchProfiles.Series> profileChoices=new ArrayList<>();
 synchronized void configureProfile(BenchProfiles.Profile p){
  for(PlotHistory.Series s:history.snapshot())history.remove(s.id);
  profile=p;profileChoices.clear();profileChoices.addAll(p.plots);resetRevision=++revision;
 }
 private boolean profileWatch(BenchProfiles.Series choice,String watch){
  if(profile==null)return false;
  String topic=profile.watches.get(choice.slot).wire.substring(2);
  return watch.equals("Watch "+(choice.slot+1)+" · "+topic)||(choice.slot==0&&watch.equals(topic));
 }
 synchronized List<BenchProfiles.Series> profileSelection(){
  LinkedHashMap<String,BenchProfiles.Series> selected=new LinkedHashMap<>();
  for(BenchProfiles.Series s:profileChoices)selected.put(s.key(),s);
  for(PlotHistory.Series s:history.snapshot())if(s.provenance.slot>=0){BenchProfiles.Series choice=new BenchProfiles.Series(s.provenance.slot,s.path);selected.put(choice.key(),choice);}
  return new ArrayList<>(selected.values());
 }
 synchronized List<String> pendingProfileFields(){
  List<String> pending=new ArrayList<>();
  for(BenchProfiles.Series choice:profileChoices){boolean ready=false;for(Field f:available.values())if(f.path.equals(choice.path)&&profileWatch(choice,f.watch))ready=true;if(!ready)pending.add("Watch "+(choice.slot+1)+" · "+choice.path);}
  return pending;
 }
 synchronized void removeSelection(String id){
  int split=id.indexOf('\n');if(split>=0){String watch=id.substring(0,split),path=id.substring(split+1);profileChoices.removeIf(s->s.path.equals(path)&&profileWatch(s,watch));}
  history.remove(id);revision++;
 }
 private long nativeSession=-1,nativeReader=-1;
 private final long[] readers={-1,-1,-1,-1},minimumReaders={-1,-1,-1,-1};
 private final String[] slotWatches={"","","",""};
 private long nativeEpoch=-1,revision,resetRevision;private String nativeIdentity="";
 synchronized long revision(){return revision;}
 synchronized long resetRevision(){return resetRevision;}
 synchronized boolean qaCurrent(long session,int slot,long reader){
  return session==nativeSession&&reader>=readers[slot]&&reader>=minimumReaders[slot];
 }
 synchronized JSONObject qaStatistics() throws JSONException {
  return new JSONObject().put("capacityPerSeries",history.capacity).put("windowNs",history.window)
   .put("maximumSeries",PlotHistory.MAX_SERIES).put("invalidationRevision",revision)
   .put("series",qaSeries(history.snapshot()));
 }
 synchronized JSONArray qaSeries(List<PlotHistory.Series> series) throws JSONException {
  JSONArray result=new JSONArray();
  for(PlotHistory.Series s:series){
   // Source is owned by the immutable history copy, never current slotWatches/readers.
   int finite=0,gaps=0,breaks=0;
   for(PlotHistory.Point p:s.points){if(p.value==null)gaps++;else finite++;if(p.breakBefore)breaks++;}
   result.put(new JSONObject().put("seriesIndex",result.length()).put("slot",s.provenance.slot)
    .put("seriesId",s.id).put("fieldPath",s.path).put("sessionGeneration",s.provenance.session).put("readerGeneration",s.provenance.reader)
    .put("pointCount",s.points.size()).put("finitePoints",finite).put("gapPoints",gaps).put("breakBeforePoints",breaks)
    .put("oldestReceiveNs",s.points.isEmpty()?JSONObject.NULL:s.points.get(0).time)
    .put("newestReceiveNs",s.points.isEmpty()?JSONObject.NULL:s.points.get(s.points.size()-1).time));
  }
  return result;
 }
 synchronized void reset(long epoch) {
  if(epoch<nativeEpoch)return;nativeEpoch=epoch;nativeSession=-1;nativeReader=-1;nativeIdentity="";Arrays.fill(readers,-1);Arrays.fill(minimumReaders,-1);Arrays.fill(slotWatches,"");sequences.clear();sources.clear();available.clear();history.clear();resetRevision=++revision;
 }
 synchronized List<Field> fields(){return new ArrayList<>(available.values());}
 synchronized void retire(String watch) {
  sequences.remove(watch);sources.remove(watch);available.entrySet().removeIf(e->e.getValue().watch.equals(watch));
  for(PlotHistory.Series s:history.snapshot())if(s.id.startsWith(watch+"\n"))history.remove(s.id);
  revision++;
 }
 synchronized boolean select(String watch,String path){Field f=available.get(watch+"\n"+path);return f!=null&&history.select(f.id(),f.path,f.unit,sources.getOrDefault(watch,PlotHistory.Source.UNKNOWN));}
 synchronized boolean selected(String id){for(PlotHistory.Series s:history.snapshot())if(s.id.equals(id))return true;return false;}
 synchronized void accept(Frame frame){
  if(frame.watch==null||frame.watch.length()>512||frame.epoch<nativeEpoch||frame.received<0||frame.sequence<1)return;
  if(frame.epoch>nativeEpoch)reset(frame.epoch);
  String key=frame.watch;Long previous=sequences.get(key);
  if(previous!=null&&frame.sequence<=previous)return;
  if(previous==null&&sequences.size()==4)return;
  sequences.put(key,frame.sequence);sources.put(key,frame.provenance);
  available.entrySet().removeIf(e->e.getValue().watch.equals(key));
  Set<String> present=new HashSet<>();
  for(Field field:frame.fields){
   if(!field.watch.equals(key)||field.path.length()>256||key.length()>512||(field.unit!=null&&field.unit.length()>64))continue;
   if(available.size()<MAX_FIELDS*4)available.put(field.id(),field);
   for(BenchProfiles.Series choice:profileChoices)if(choice.slot==frame.provenance.slot&&choice.path.equals(field.path)&&profileWatch(choice,key))history.select(field.id(),field.path,field.unit,frame.provenance);
   present.add(field.id());history.append(field.id(),frame.received,field.value,frame.source,frame.provenance);
  }
  for(PlotHistory.Series s:history.snapshot())if(s.id.startsWith(key+"\n")&&!present.contains(s.id))history.append(s.id,frame.received,null,frame.source,frame.provenance);
 }
 synchronized void acceptNative(JSONObject snapshot,long epoch,boolean pending){
  if(snapshot.optJSONArray("watches")!=null){acceptWatches(snapshot,epoch,pending,null,null);return;}
  if(epoch<nativeEpoch)return;
  long session=snapshot.optLong("sessionGeneration"),reader=snapshot.optLong("readerGeneration");
  if(epoch==nativeEpoch&&(session<nativeSession||(session==nativeSession&&reader<nativeReader)))return;
  String identity=snapshot.optLong("sessionGeneration")+":"+snapshot.optLong("readerGeneration");
  if(epoch>nativeEpoch||!identity.equals(nativeIdentity)){reset(epoch);nativeIdentity=identity;nativeSession=session;nativeReader=reader;}
  if(pending||!snapshot.optBoolean("running")||!snapshot.optBoolean("readerActive")||!snapshot.optBoolean("fresh")
     ||!"receiving".equals(snapshot.optString("subscriptionState"))||!snapshot.optString("error").isEmpty()){
   available.clear();history.clear();resetRevision=++revision;return;
  }
  JSONArray fields=snapshot.optJSONArray("numericFields");
  String watch=snapshot.optString("subscription");slotWatches[0]=watch;
  Object received=snapshot.opt("receivedMonotonicNs"),source=snapshot.opt("sourceTimestampNs");
  if(fields==null||!(received instanceof Number)||watch.isEmpty())return;
  List<Field> typed=new ArrayList<>();
  for(int i=0;i<Math.min(MAX_FIELDS,fields.length());i++){
   JSONObject f=fields.optJSONObject(i);if(f==null)continue;
   Object value=f.opt("value"),unit=f.opt("unit");
   typed.add(new Field(watch,f.optString("path"),unit instanceof String?(String)unit:null,
    value instanceof Number&&"finite".equals(f.optString("state"))?((Number)value).doubleValue():null));
  }
  accept(new Frame(watch,epoch,snapshot.optLong("count"),((Number)received).longValue(),source instanceof Number?((Number)source).longValue():null,typed,new PlotHistory.Source(0,session,reader)));
 }
 static boolean matches(WatchSlots.Request request,JSONObject row){
  String topic=request.wire.startsWith("rt/")?request.wire.substring(2):request.wire;
  return topic.equals(row.optString("subscription"))&&request.type.equals(row.optString("type"));
 }
 static String watchName(int slot,JSONObject row){return "Watch "+(slot+1)+" · "+row.optString("subscription");}
 synchronized void requestSlot(int slot){
  profileChoices.removeIf(s->s.slot==slot);
  minimumReaders[slot]=readers[slot]<0?-1:readers[slot]+1;
  retireSlot(slot);
 }
 synchronized void retireSlot(int slot){
  if(!slotWatches[slot].isEmpty())retire(slotWatches[slot]);
 }
 void acceptNative(JSONObject snapshot,long epoch,WatchSlots slots,WatchSlots.Frame[] frames){
  // Lock order is policy -> slots -> controller. Immutable pre-poll frames are
  // checked under request()'s monitor until ingestion finishes.
  synchronized(slots){synchronized(this){
   if(snapshot.optJSONArray("watches")!=null)acceptWatches(snapshot,epoch,false,slots,frames);
   else if(slots.current(frames[0]))acceptNative(snapshot,epoch,frames[0].pending||!frames[0].error.isEmpty()||frames[0].request.wire.isEmpty());
  }}
 }
 private void acceptWatches(JSONObject snapshot,long epoch,boolean pending,WatchSlots slots,WatchSlots.Frame[] frames){
  long session=snapshot.optLong("sessionGeneration",-1);
  if(epoch<nativeEpoch||session<nativeSession)return;
  if(session!=nativeSession){reset(epoch);nativeSession=session;}
  nativeEpoch=epoch;
  JSONArray rows=snapshot.optJSONArray("watches");
  for(int slot=0;slot<4;slot++){
   if(frames!=null){
    WatchSlots.Frame f=frames[slot];
    if(!slots.current(f))continue; // A newer UI request has already retired it.
    if(f.pending||!f.error.isEmpty()||f.request.wire.isEmpty()){retireSlot(slot);continue;}
   }
   JSONObject row=rows.optJSONObject(slot);
   if(row==null||row.optInt("slot",-1)!=slot){retireSlot(slot);continue;}
   long reader=row.optLong("generation",-1);
   if(reader<readers[slot]||reader<minimumReaders[slot])continue;
   if(frames!=null&&!matches(frames[slot].request,row)){retireSlot(slot);continue;}
   String watch=watchName(slot,row);
   if(reader!=readers[slot]||!slotWatches[slot].equals(watch)){
    retireSlot(slot);readers[slot]=reader;slotWatches[slot]=watch;
   }
   if((pending&&slot==0)||!snapshot.optBoolean("running")||!row.optBoolean("readerActive")||!row.optBoolean("fresh")
      ||!"receiving".equals(row.optString("subscriptionState"))||!row.optString("error").isEmpty()){
    retireSlot(slot);continue;
   }
   JSONArray fields=row.optJSONArray("numericFields");Object received=row.opt("receivedMonotonicNs"),source=row.opt("sourceTimestampNs");
   if(fields==null||!(received instanceof Number)||row.optString("subscription").isEmpty())continue;
   List<Field> typed=new ArrayList<>();
   for(int i=0;i<Math.min(MAX_FIELDS,fields.length());i++){
    JSONObject f=fields.optJSONObject(i);if(f==null)continue;
    Object value=f.opt("value"),unit=f.opt("unit");
    typed.add(new Field(watch,f.optString("path"),unit instanceof String?(String)unit:null,
     value instanceof Number&&"finite".equals(f.optString("state"))?((Number)value).doubleValue():null));
   }
   accept(new Frame(watch,epoch,row.optLong("count"),((Number)received).longValue(),source instanceof Number?((Number)source).longValue():null,typed,new PlotHistory.Source(slot,session,reader)));
  }
 }

}
