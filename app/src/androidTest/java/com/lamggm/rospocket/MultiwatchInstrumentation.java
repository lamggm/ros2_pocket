package com.lamggm.rospocket;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.util.concurrent.TimeUnit;

/** Framework runner: no external injector, bridge replacement, auxiliary Session or DDS return double. */
public final class MultiwatchInstrumentation extends Instrumentation {
 private Bundle arguments;private MainActivity activity;private DeviceTestHooks.Lease lease;
 private android.content.SharedPreferences preferences;private java.util.Map<String,?> savedPreferences;
 private static final String[] PREFERENCE_KEYS={"domain","peer","wanted","reliable","transient"};
 private final String[] wires=new String[4];private String owner;private final JSONArray cases=new JSONArray();
 @Override public void onCreate(Bundle args){arguments=args==null?new Bundle():args;start();}
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 @Override public void onStart(){Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
  try{
   owner=arguments.getString("owner","");check(owner.matches("[0-9a-f]{32}"),"explicit run owner required");
   String domain=arguments.getString("domain","");check(domain.equals("67")||domain.equals("68"),"reserved bench domain required");
   check(NetworkRules.validPeer(arguments.getString("peer","")),"invalid peer before lease");
   for(int i=0;i<4;i++)wires[i]="rt/pocket_multiwatch/run_"+owner+"/watch"+i;
   if("os-positive-control".equals(arguments.getString("mode"))){
    // Deliberately no recognized app lease. Parent must prove OS-active rejection.
    JSONObject nativeState=NativeBridge.json(NativeBridge.diagnosticState());
    check(nativeState.get("runnerActive")==Boolean.FALSE&&DeviceTestHooks.CONTROLLER.phase().equals("INACTIVE"),"positive control requires actual inactive app lease");
    result.putBoolean("app_runner_active",false);result.putString("app_lease_phase",DeviceTestHooks.CONTROLLER.phase());result.putInt("pid",android.os.Process.myPid());
    result.putString("native_state",nativeState.toString());result.putString("scope","OS runner positive control only; no app lease");sendStatus(1,result);Thread.sleep(30_000);code=Activity.RESULT_OK;
   }else{
    lease=DeviceTestHooks.CONTROLLER.begin(MainActivity.diagnosticWorker());
    Intent intent=new Intent(Intent.ACTION_MAIN).setClassName(getTargetContext(),MainActivity.class.getName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
    preferences=getTargetContext().getSharedPreferences("connection",android.content.Context.MODE_PRIVATE);savedPreferences=preferences.getAll();
    activity=(MainActivity)startActivitySync(intent);waitForIdleSync();
    runOnMainSync(()->{
     Button connect=activity.findViewById(R.id.connect);if(!((EditText)activity.findViewById(R.id.domain)).isEnabled())connect.performClick();
     activity.getWindow().getDecorView().findViewWithTag("watch-select:0").performClick();
     for(int id:new int[]{R.id.qos_reliable,R.id.qos_transient}){CheckBox qos=activity.findViewById(id);if(qos.isChecked())qos.performClick();check(!qos.isChecked(),"baseline QoS readback failed");}
     ((EditText)activity.findViewById(R.id.domain)).setText(domain);
     String peer=arguments.getString("peer","");check(NetworkRules.validPeer(peer),"invalid isolated fixture peer");((EditText)activity.findViewById(R.id.peer)).setText(peer);connect.performClick();
    });
    await(()->fixtureReady(projection().getJSONObject("poll")));
    for(int slot=0;slot<4;slot++)select(slot);
    await(()->healthy(projection().getJSONObject("poll")));
    mixedRates();fifthRejected();negativeQos();
    for(int slot:new int[]{0,2})for(String stage:new String[]{"topic","reader"}){exercise(slot,stage);cases.put(stage+"_failure_"+slot);}
    sameTopicRemoval();cases.put("rosout_new");
    result.putString("multiwatch13_result",new JSONObject().put("schemaVersion",1).put("owner",owner).put("cases",cases).put("passed",true).toString());
    result.putString("scope","real candidate JNI/Session, primary+extra topic+reader, three distinct failed polls and advancing recovery");code=Activity.RESULT_OK;
   }
  }catch(Throwable error){result.putString("failure",error.getClass().getName()+": "+error.getMessage());}
  finally{
   if(lease!=null)try{check(DeviceTestHooks.CONTROLLER.end(lease,MainActivity.diagnosticWorker()).get(5,TimeUnit.SECONDS),"lease drain failed");}catch(Throwable ex){code=Activity.RESULT_CANCELED;result.putString("cleanupFailure",ex.toString());}
   try{if(activity!=null)runOnMainSync(()->{Button button=activity.findViewById(R.id.connect);if(!((EditText)activity.findViewById(R.id.domain)).isEnabled())button.performClick();activity.finish();});}
   catch(Throwable ex){code=Activity.RESULT_CANCELED;result.putString("activityCleanupFailure",ex.toString());}
   finally{if(savedPreferences!=null){android.content.SharedPreferences.Editor editor=preferences.edit();for(String key:PREFERENCE_KEYS){Object value=savedPreferences.get(key);if(value==null)editor.remove(key);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof String)editor.putString(key,(String)value);else throw new IllegalStateException("unexpected preference type: "+key);}if(!editor.commit()){code=Activity.RESULT_CANCELED;result.putString("preferenceCleanupFailure","restore commit failed");}}}
   finish(code,result);
  }
 }
 private interface Condition {boolean get() throws Exception;}
 private long waitDeadline;
 private void await(Condition c) throws Exception {
  long previous=waitDeadline;waitDeadline=System.nanoTime()+20_000_000_000L;
  try{while(System.nanoTime()<waitDeadline){if(c.get())return;Thread.sleep(100);}throw new AssertionError("bounded genuine intake/poll condition timed out");}
  finally{waitDeadline=previous;}
 }
 private JSONObject projection() throws Exception {
  long deadline=waitDeadline==0?System.nanoTime()+20_000_000_000L:waitDeadline;JSONObject value;
  while(true){
   try{value=DeviceTestHooks.CONTROLLER.projection(lease);break;}
   catch(DeviceTestHooks.NotCaptured notReady){if(System.nanoTime()>=deadline)throw notReady;Thread.sleep(100);}
  }
  validateProjection(value);return value;
 }
 private String processInstance;private long sessionIdentity=-1,lastCaptureSequence=-1;
 private static long integer(JSONObject o,String key,long minimum) throws JSONException {
  Object value=o.get(key);if(!(value instanceof Integer||value instanceof Long)||((Number)value).longValue()<minimum)throw new JSONException("invalid integer: "+key);return ((Number)value).longValue();
 }
 private static void bool(JSONObject o,String key) throws JSONException {if(!(o.get(key) instanceof Boolean))throw new JSONException("invalid boolean: "+key);}
 private static String string(JSONObject o,String key) throws JSONException {Object value=o.get(key);if(!(value instanceof String))throw new JSONException("invalid string: "+key);return (String)value;}
 private void validateProjection(JSONObject value) throws JSONException {
  JSONObject d=value.getJSONObject("diagnostic"),poll=value.getJSONObject("poll");
  if(integer(d,"schemaVersion",0)!=2||d.get("valid")!=Boolean.TRUE||d.get("capability")!=Boolean.TRUE||d.get("runnerActive")!=Boolean.TRUE||!"ACTIVE".equals(string(d,"leasePhase"))||!"ros-pocket-dds-fault-v1".equals(string(d,"contract"))||lease==null||integer(d,"leaseEpoch",1)!=lease.epoch||integer(d,"pid",1)!=android.os.Process.myPid())throw new IllegalStateException("foreign diagnostic runner identity");
  integer(d,"startTimeTicks",1);integer(d,"lifecycle",0);integer(d,"policy",0);integer(d,"revision",0);integer(d,"nativeRevision",0);
  for(String key:new String[]{"armed","ticketPending","operationInFlight"})bool(d,key);
  long captured=integer(d,"capturedMonotonicNs",0),exported=integer(d,"exportedMonotonicNs",0),sequence=integer(d,"captureSequence",1),session=integer(d,"sessionGeneration",1);
  String instance=string(d,"processInstance");
  if(instance.isEmpty()||exported<captured||sequence<lastCaptureSequence||integer(poll,"sessionGeneration",1)!=session||(sessionIdentity!=-1&&sessionIdentity!=session)||(processInstance!=null&&!processInstance.equals(instance)))throw new IllegalStateException("foreign or regressed ordinary poll identity");
  JSONArray requests=d.getJSONArray("requests");if(requests.length()!=4)throw new JSONException("four request identities required");
  for(int i=0;i<4;i++){JSONObject r=requests.getJSONObject(i);if(integer(r,"slot",0)!=i)throw new IllegalStateException("foreign request slot");integer(r,"version",0);integer(r,"appliedVersion",-1);bool(r,"pending");}
  bool(poll,"running");if(poll.get("running")!=Boolean.TRUE)throw new IllegalStateException("ordinary session not running");
  JSONArray topics=poll.getJSONArray("topics");for(int i=0;i<topics.length();i++){JSONObject t=topics.getJSONObject(i);string(t,"wireName");string(t,"wireType");bool(t,"supported");}
  JSONArray rows=poll.getJSONArray("watches");if(rows.length()!=4)throw new JSONException("four native watches required");
  for(int i=0;i<4;i++){
   JSONObject w=rows.getJSONObject(i);if(integer(w,"slot",0)!=i)throw new IllegalStateException("foreign native slot");integer(w,"generation",0);integer(w,"count",0);integer(w,"matched",0);bool(w,"readerActive");bool(w,"fresh");
   Object age=w.get("age");if(!(age instanceof Number)||!Double.isFinite(((Number)age).doubleValue()))throw new JSONException("invalid age");
   for(String key:new String[]{"subscription","type","last","error","subscriptionState"})string(w,key);
   Object message=w.get("message");if(message!=JSONObject.NULL&&!(message instanceof JSONObject))throw new JSONException("invalid message projection");
   if(w.get("receivedMonotonicNs")!=JSONObject.NULL)integer(w,"receivedMonotonicNs",0);
  }
  JSONArray logs=poll.getJSONObject("rosout").getJSONArray("entries");for(int i=0;i<logs.length();i++){JSONObject log=logs.getJSONObject(i);string(log,"logger");integer(log,"line",0);}
  processInstance=instance;sessionIdentity=session;lastCaptureSequence=sequence;
 }
 private boolean healthy(JSONObject poll) throws Exception {JSONArray rows=poll.getJSONArray("watches");for(int i=0;i<4;i++){JSONObject w=rows.getJSONObject(i);if(!w.getBoolean("readerActive")||w.getLong("count")==0||!w.getBoolean("fresh"))return false;}return logSequence(poll)>0;}
 private long logSequence(JSONObject poll) throws Exception {JSONArray logs=poll.getJSONObject("rosout").getJSONArray("entries");long sequence=0;for(int i=0;i<logs.length();i++){JSONObject log=logs.getJSONObject(i);if(("mw13."+owner).equals(log.getString("logger")))sequence=Math.max(sequence,log.getLong("line"));}return sequence;}
 private boolean fixtureReady(JSONObject poll) throws Exception {
  JSONArray topics=poll.getJSONArray("topics");boolean[] found=new boolean[4];
  for(int i=0;i<topics.length();i++){
   JSONObject topic=topics.getJSONObject(i);String wire=topic.getString("wireName"),type=topic.getString("wireType");
   for(int slot=0;slot<4;slot++)if(wires[slot].equals(wire)){
    check("std_msgs::msg::dds_::String_".equals(type)&&topic.get("supported")==Boolean.TRUE&&!found[slot],"wrong type, unsupported or duplicate fixture topic: "+wire);found[slot]=true;
   }
  }
  for(boolean present:found)if(!present)return false;
  final boolean[] rendered={true};runOnMainSync(()->{for(String wire:wires){View read=activity.getWindow().getDecorView().findViewWithTag("read:"+wire+"\nstd_msgs::msg::dds_::String_");if(read==null||!read.isEnabled()||!read.isShown())rendered[0]=false;}});
  return rendered[0];
 }
 private void select(int slot) throws Exception {selectProfile(slot,slot);}
 private void selectProfile(int slot,int source) throws Exception {
  runOnMainSync(()->{
   activity.getWindow().getDecorView().findViewWithTag("watch-select:"+slot).performClick();
   boolean[] desired={slot==1||slot==3,slot==3};int[] ids={R.id.qos_reliable,R.id.qos_transient};
   for(int i=0;i<2;i++){CheckBox box=activity.findViewById(ids[i]);if(box.isChecked()!=desired[i])box.performClick();check(box.isChecked()==desired[i],"selected-slot QoS readback");}
  });
  JSONObject poll=projection().getJSONObject("poll");JSONArray topics=poll.getJSONArray("topics");JSONObject found=null;
  for(int i=0;i<topics.length();i++)if(wires[source].equals(topics.getJSONObject(i).getString("wireName")))found=topics.getJSONObject(i);
  check(found!=null,"required genuine fixture topic missing: "+wires[slot]);final String tag="read:"+found.getString("wireName")+"\n"+found.getString("wireType");
  runOnMainSync(()->{activity.getWindow().getDecorView().findViewWithTag("watch-select:"+slot).performClick();View read=activity.getWindow().getDecorView().findViewWithTag(tag);check(read!=null&&read.isEnabled(),"actual Explorer read View missing");read.performClick();});
 }
 private void exercise(int slot,String stage) throws Exception {
  JSONObject baseline=projection().getJSONObject("poll");long[] counts=new long[4],generations=new long[4];
  for(int i=0;i<4;i++){JSONObject w=baseline.getJSONArray("watches").getJSONObject(i);counts[i]=w.getLong("count");generations[i]=w.getLong("generation");}
  long logs=logSequence(baseline);DeviceTestHooks.Ticket[] ticket=new DeviceTestHooks.Ticket[1];
  runOnMainSync(()->ticket[0]=activity.diagnosticRetry(slot,stage,lease));check(ticket[0]!=null,"no bound diagnostic ticket");
  await(()->!ticket[0].receipt.isEmpty());JSONObject receipt=new JSONObject(ticket[0].receipt);
  check(ticket[0].matchesReceipt(receipt),"receipt/native ownership mismatch");
  check("dds_create_topic".equals(receipt.getString("topicApi")),"real topic call missing");
  if(stage.equals("topic")){check(receipt.getLong("topicReturn")==-3&&receipt.isNull("readerReturn")&&receipt.isNull("readerApi")&&receipt.getBoolean("topicArgumentOverridden")&&"rt/pocket_test/invalid topic".equals(receipt.getString("topicName")),"actual topic failure missing");}
  else{check(receipt.getLong("topicReturn")>0&&receipt.getLong("readerReturn")==-3&&"dds_create_reader".equals(receipt.getString("readerApi"))&&!receipt.getBoolean("topicArgumentOverridden")&&receipt.getLong("readerParent")==0&&receipt.getBoolean("readerArgumentOverridden"),"actual reader parent0 failure missing");}
  long sequence=-1;long[] previousCounts=counts.clone();long[] previousLogs={logs};
  for(int distinct=0;distinct<3;distinct++){
   final long previous=sequence;await(()->{
    JSONObject p=projection();if(p.getJSONObject("diagnostic").getLong("captureSequence")<=previous)return false;
    JSONObject poll=p.getJSONObject("poll");for(int i=0;i<4;i++)if(i!=slot){JSONObject w=poll.getJSONArray("watches").getJSONObject(i);if(w.getLong("count")<=previousCounts[i]||w.getLong("generation")!=generations[i])return false;}
    return logSequence(poll)>previousLogs[0];
   });
   JSONObject p=projection();sequence=p.getJSONObject("diagnostic").getLong("captureSequence");JSONObject poll=p.getJSONObject("poll"),w=poll.getJSONArray("watches").getJSONObject(slot);
   check(!w.getBoolean("readerActive")&&!w.getBoolean("fresh")&&w.getLong("count")==0&&w.getInt("matched")==0&&w.getDouble("age")<0&&w.getString("last").isEmpty()&&w.isNull("message")&&w.getString("subscription").isEmpty()&&!w.getString("error").isEmpty(),"failed telemetry not persistent inactive");
   check(w.getLong("generation")==receipt.getLong("attemptedGeneration")&&poll.getLong("sessionGeneration")==receipt.getLong("session"),"receipt/poll generation/session mismatch");
   for(int i=0;i<4;i++)if(i!=slot)previousCounts[i]=poll.getJSONArray("watches").getJSONObject(i).getLong("count");previousLogs[0]=logSequence(poll);
   final String[] rendered=new String[1];runOnMainSync(()->rendered[0]=((TextView)activity.getWindow().getDecorView().findViewWithTag("watch-state:"+slot)).getText().toString());
   // Ordinary UI throttle can lag a worker poll; await actual View's failure state separately.
   await(()->{runOnMainSync(()->rendered[0]=((TextView)activity.getWindow().getDecorView().findViewWithTag("watch-state:"+slot)).getText().toString());return rendered[0].contains("ERRO")&&!rendered[0].contains("RECEBENDO");});
  }
  await(()->{JSONObject poll=projection().getJSONObject("poll");for(int i=0;i<4;i++)if(i!=slot){JSONObject w=poll.getJSONArray("watches").getJSONObject(i);if(w.getLong("count")<=counts[i]||w.getLong("generation")!=generations[i])return false;}return logSequence(poll)>logs;});
  select(slot);await(()->healthy(projection().getJSONObject("poll")));JSONObject first=projection().getJSONObject("poll").getJSONArray("watches").getJSONObject(slot);long count=first.getLong("count"),received=first.getLong("receivedMonotonicNs");String value=first.getString("last");
  await(()->{JSONObject w=projection().getJSONObject("poll").getJSONArray("watches").getJSONObject(slot);return w.getLong("count")>count&&w.getLong("receivedMonotonicNs")>received&&!w.getString("last").equals(value)&&w.getLong("generation")>receipt.getLong("attemptedGeneration");});
 }

 private JSONObject stablePoll() throws Exception {
  JSONObject value=projection();JSONArray requests=value.getJSONObject("diagnostic").getJSONArray("requests");
  for(int i=0;i<4;i++){JSONObject r=requests.getJSONObject(i);check(r.get("pending")==Boolean.FALSE&&r.getLong("version")==r.getLong("appliedVersion"),"pending worker request");}
  return value.getJSONObject("poll");
 }
 private void settled() throws Exception {await(()->{JSONObject p=projection();JSONArray r=p.getJSONObject("diagnostic").getJSONArray("requests");for(int i=0;i<4;i++)if(r.getJSONObject(i).get("pending")!=Boolean.FALSE||r.getJSONObject(i).getLong("version")!=r.getJSONObject(i).getLong("appliedVersion"))return false;return healthy(p.getJSONObject("poll"));});}
 private interface PollCheck {void test(JSONObject p) throws Exception;}
 private JSONObject observe(JSONObject before,int omitted,PollCheck checkPoll) throws Exception {
  long start=System.nanoTime();JSONObject after;
  do{Thread.sleep(500);after=stablePoll();checkPoll.test(after);}while(System.nanoTime()-start<2_500_000_000L);
  check(after.getLong("sessionGeneration")==before.getLong("sessionGeneration"),"session changed");
  for(int i=0;i<4;i++)if(i!=omitted){JSONObject a=before.getJSONArray("watches").getJSONObject(i),b=after.getJSONArray("watches").getJSONObject(i);check(b.getLong("generation")==a.getLong("generation")&&b.getLong("count")>a.getLong("count")&&b.getBoolean("readerActive")&&b.getString("error").isEmpty(),"survivor stalled/replaced: "+i);}
  check(logSequence(after)>logSequence(before),"new dedicated rosout missing");return after;
 }
 private void mixedRates() throws Exception {
  settled();JSONObject before=stablePoll();long begin=System.nanoTime();
  Thread.sleep(12_000);JSONObject after=stablePoll();double seconds=(System.nanoTime()-begin)/1e9;int[] rates={1,2,5,10};JSONArray hz=new JSONArray();
  for(int i=0;i<4;i++){JSONObject a=before.getJSONArray("watches").getJSONObject(i),b=after.getJSONArray("watches").getJSONObject(i);double rate=(b.getLong("count")-a.getLong("count"))/seconds;check(a.getLong("generation")==b.getLong("generation")&&rate>=.8*rates[i]&&rate<=1.2*rates[i],"mixed intake rate: "+i+" "+rate);hz.put(rate);}
  Bundle observation=new Bundle();observation.putString("multiwatch13_rates",hz.toString());sendStatus(1,observation);cases.put("mixed_rates");
 }
 private void fifthRejected() throws Exception {
  JSONObject before=stablePoll();java.util.concurrent.CompletableFuture<JSONObject> response=new java.util.concurrent.CompletableFuture<>();
  MainActivity.diagnosticWorker().execute(()->{try{response.complete(NativeBridge.json(NativeBridge.watch(4,wires[0],"std_msgs::msg::dds_::String_",false,false)));}catch(Throwable ex){response.completeExceptionally(ex);}});
  check(response.get(3,TimeUnit.SECONDS).get("ok")==Boolean.FALSE,"fifth JNI watch not rejected");
  observe(before,-1,p->{for(int i=0;i<4;i++){JSONObject a=before.getJSONArray("watches").getJSONObject(i),b=p.getJSONArray("watches").getJSONObject(i);for(String k:new String[]{"generation","subscription","type","qos","readerActive","error"})check(a.get(k).equals(b.get(k)),"fifth mutated: "+k);}});cases.put("fifth_rejected");
 }
 private void negativeQos() throws Exception {
  settled();JSONObject before=stablePoll();
  runOnMainSync(()->{activity.getWindow().getDecorView().findViewWithTag("watch-select:0").performClick();CheckBox box=activity.findViewById(R.id.qos_reliable);if(!box.isChecked())box.performClick();check(box.isChecked(),"Reliable readback missing");});
  await(()->{JSONObject w=projection().getJSONObject("poll").getJSONArray("watches").getJSONObject(0);return "reliable / volatile".equals(w.getString("qos"))&&w.getLong("count")==0&&w.getBoolean("readerActive");});
  JSONObject start=stablePoll();String gid=null;JSONArray topics=start.getJSONArray("topics");
  for(int i=0;i<topics.length();i++){JSONObject t=topics.getJSONObject(i);if(!wires[0].equals(t.getString("wireName")))continue;JSONArray es=t.getJSONArray("endpoints");for(int j=0;j<es.length();j++){JSONObject e=es.getJSONObject(j);if(e.get("writer")==Boolean.TRUE){check(gid==null,"multiple writers");gid=e.getString("id");check(e.getJSONObject("qos").getString("reliability").equals("best_effort")&&e.getJSONArray("nodes").toString().contains("/multiwatch13_"+owner),"unowned writer");}}}
  check(gid!=null,"actual incompatible writer missing");Bundle phase=new Bundle();phase.putString("multiwatch13_phase","negative");phase.putString("writerGid",gid);sendStatus(2,phase);
  long generation=start.getJSONArray("watches").getJSONObject(0).getLong("generation"),end=System.nanoTime()+25_000_000_000L;
  while(System.nanoTime()<end){Thread.sleep(500);JSONObject w=stablePoll().getJSONArray("watches").getJSONObject(0);check(w.getLong("generation")==generation&&w.getBoolean("readerActive")&&!w.getBoolean("fresh")&&w.getLong("count")==0&&w.getInt("matched")==0&&w.getString("last").isEmpty()&&w.getString("error").isEmpty()&&w.getString("qos").equals("reliable / volatile"),"negative QoS changed");}
  observe(before,0,p->{});cases.put("negative_qos");select(0);settled();JSONObject first=stablePoll().getJSONArray("watches").getJSONObject(0);await(()->{JSONObject w=stablePoll().getJSONArray("watches").getJSONObject(0);return w.getLong("count")>first.getLong("count")&&!w.getString("last").equals(first.getString("last"));});cases.put("qos_recovery");
 }
 private void sameTopicRemoval() throws Exception {
  selectProfile(0,1);settled();JSONObject before=stablePoll();
  before=observe(before,-1,p->{JSONObject a=p.getJSONArray("watches").getJSONObject(0),b=p.getJSONArray("watches").getJSONObject(1);check(a.getString("subscription").equals(b.getString("subscription"))&&a.getString("qos").equals("best_effort / volatile")&&b.getString("qos").equals("reliable / volatile"),"shared QoS not independent");});cases.put("same_topic");
  runOnMainSync(()->{activity.getWindow().getDecorView().findViewWithTag("watch-select:0").performClick();activity.findViewById(R.id.watch_remove).performClick();activity.findViewById(R.id.watch_remove).performClick();});
  Thread.sleep(600);observe(before,0,p->{JSONObject w=p.getJSONArray("watches").getJSONObject(0);check(!w.getBoolean("readerActive")&&w.getLong("count")==0&&w.getString("subscription").isEmpty()&&w.getString("error").isEmpty(),"removed slot active");});cases.put("remove_same_topic");
  select(0);settled();JSONObject first=stablePoll().getJSONArray("watches").getJSONObject(0);await(()->{JSONObject w=stablePoll().getJSONArray("watches").getJSONObject(0);return w.getLong("count")>first.getLong("count")&&w.getLong("receivedMonotonicNs")>first.getLong("receivedMonotonicNs")&&!w.getString("last").equals(first.getString("last"));});cases.put("recreate");
 }
}
