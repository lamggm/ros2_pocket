package com.lamggm.rospocket;

import org.json.*;

/** Immutable, bounded metadata projection of the existing worker poll. No JNI calls. */
final class QaSnapshot {
 static final long MAX_CAPTURE_AGE_NS=2_000_000_000L;
 static final int MAX_JSON_BYTES=24_576;
 private volatile String cached;

 void invalidate(){cached=null;}

 void publish(JSONObject nativeSnapshot,WatchSlots slots,WatchSlots.Frame[] frames,PlotController plots,
              int lifecycle,long policyEpoch,long captured) throws JSONException {
  synchronized(slots){
   JSONObject out=new JSONObject().put("schemaVersion",1).put("capturedMonotonicNs",captured)
    .put("clock","java.nanoTime").put("lifecycleGeneration",lifecycle).put("policyGeneration",policyEpoch)
    .put("sessionGeneration",integer(nativeSnapshot,"sessionGeneration"));
   JSONArray rows=nativeSnapshot.optJSONArray("watches"),watches=new JSONArray();
   boolean valid=nativeSnapshot.optBoolean("running")&&out.opt("sessionGeneration")!=JSONObject.NULL
    &&rows!=null&&rows.length()==4;
   for(int slot=0;slot<4;slot++){
    WatchSlots.Frame frame=frames[slot];JSONObject row=rows==null?null:rows.optJSONObject(slot);
    boolean current=slots.current(frame)&&!frame.pending&&row!=null&&row.optInt("slot",-1)==slot
     &&(frame.request.wire.isEmpty()?!row.optBoolean("readerActive"):PlotController.matches(frame.request,row));
    Object count=current?integer(row,"count"):JSONObject.NULL;
    Object reader=current?integer(row,"generation"):JSONObject.NULL;
    Object matched=current?integer(row,"matched"):JSONObject.NULL;
    Object hz=current?finite(row,"hz",0):JSONObject.NULL;
    Object age=current?finite(row,"age",-1):JSONObject.NULL;
    if(age instanceof Number&&((Number)age).doubleValue()<0&&((Number)age).doubleValue()!=-1)age=JSONObject.NULL;
    Object active=current?bool(row,"readerActive"):JSONObject.NULL;
    Object fresh=current?bool(row,"fresh"):JSONObject.NULL;
    String state=row==null?"unknown":state(row.optString("subscriptionState"));
    boolean hasError=!frame.error.isEmpty()||(row!=null&&!row.optString("error").isEmpty());
    boolean coherent=current&&count!=JSONObject.NULL&&reader!=JSONObject.NULL&&matched!=JSONObject.NULL
     &&hz!=JSONObject.NULL&&age!=JSONObject.NULL&&active!=JSONObject.NULL&&fresh!=JSONObject.NULL
     &&!"unknown".equals(state)&&row.opt("error") instanceof String;
    coherent&=reader!=JSONObject.NULL&&plots.qaCurrent(nativeSnapshot.optLong("sessionGeneration",-1),slot,reader==JSONObject.NULL?-1:((Number)reader).longValue());
    valid&=coherent;
    watches.put(new JSONObject().put("slot",slot).put("requestVersion",frame.request.version)
     .put("appliedVersion",frame.appliedVersion).put("pending",frame.pending).put("valid",coherent)
     .put("readerGeneration",reader).put("count",count).put("matched",matched).put("receivedHz",hz)
     .put("sampleAgeAtCaptureSeconds",age).put("readerActive",active).put("nativeFresh",fresh)
     .put("state",frame.pending?"pending":hasError?"error":current?state:"unavailable").put("hasError",hasError)
     .put("requestedReliable",frame.request.reliable).put("requestedTransientLocal",frame.request.transientLocal));
   }
   String value=out.put("valid",valid).put("watches",watches).put("plotIntake",plots.qaStatistics()).toString();
   cached=value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=MAX_JSON_BYTES?value:null;
  }
 }

 private static Object integer(JSONObject object,String key){
  Object n=object==null?null:object.opt(key);
  return (n instanceof Long||n instanceof Integer)&&((Number)n).longValue()>=0?n:JSONObject.NULL;
 }
 private static Object finite(JSONObject object,String key,double minimum){
  Object n=object==null?null:object.opt(key);
  return n instanceof Number&&Double.isFinite(((Number)n).doubleValue())&&((Number)n).doubleValue()>=minimum?n:JSONObject.NULL;
 }
 private static Object bool(JSONObject object,String key){
  Object n=object==null?null:object.opt(key);return n instanceof Boolean?n:JSONObject.NULL;
 }
 private static String state(String state){
  switch(state){case "inactive":case "waiting":case "receiving":case "stale":case "error":return state;default:return "unknown";}
 }

 JSONObject export(long now) throws JSONException {
  String value=cached;
  JSONObject out=value==null?new JSONObject().put("schemaVersion",1).put("valid",false).put("watches",new JSONArray()):new JSONObject(value);
  long captured=out.optLong("capturedMonotonicNs",-1);
  boolean timely=captured>=0&&now>=captured&&now-captured<=MAX_CAPTURE_AGE_NS;
  out.put("exportedMonotonicNs",now).put("captureAgeNs",captured<0||now<captured?JSONObject.NULL:now-captured)
   .put("maxCaptureAgeNs",MAX_CAPTURE_AGE_NS);
  if(!timely)out.put("valid",false);
  JSONArray watches=out.getJSONArray("watches");
  for(int i=0;i<watches.length();i++){
   JSONObject w=watches.getJSONObject(i);
   boolean fresh=timely&&w.optBoolean("valid")&&w.optBoolean("readerActive")&&w.optBoolean("nativeFresh")
    &&!w.optBoolean("hasError")&&w.optLong("count")>0&&w.optLong("matched")>0&&"receiving".equals(w.optString("state"))
    &&ConnectionPolicy.fresh(true,w.optDouble("sampleAgeAtCaptureSeconds",-1),captured,now);
   w.put("freshAtExport",fresh);
  }
  return out;
 }
}
