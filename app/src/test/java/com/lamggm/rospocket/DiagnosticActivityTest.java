package com.lamggm.rospocket;
import org.junit.Test;import org.json.*;import java.io.*;import static org.junit.Assert.*;
/** Actual Activity/worker Views with a clearly labelled JNI double, not DDS execution. */
public class DiagnosticActivityTest extends ConnectionActivityTest {
 static class StateFake extends Fake {
  int states=0;String thread="";
  StateFake() throws Exception{snapshot=new JSONObject(snapshot).put("sessionGeneration",7).toString();}
  public byte[] diagnosticState(){states++;thread=Thread.currentThread().getName();return bytes("{\"capability\":false,\"contract\":null,\"armed\":false,\"runnerActive\":false,\"revision\":4,\"session\":7,\"nativeLeaseEpoch\":0}");}
 }
 @Test public void reconnectAutomaticExtraApplyCannotBeRetargetedByTicketDuringStart() throws Exception {
  java.lang.reflect.Field control=DeviceTestHooks.Controller.class.getDeclaredField("control");control.setAccessible(true);
  DeviceTestHooks.Control original=(DeviceTestHooks.Control)control.get(DeviceTestHooks.CONTROLLER);
  int[] arms={0};DeviceTestHooksTest.Fake n=new DeviceTestHooksTest.Fake(){public long[] arm(int s,String stage,long[] id,long e,long nonce){arms[0]++;return super.arm(s,stage,id,e,nonce);}};
  java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.ScheduledExecutorService worker=MainActivity.diagnosticWorker();DeviceTestHooks.Lease l=null;
  int[] watches={0};
  try{
   control.set(DeviceTestHooks.CONTROLLER,n);l=DeviceTestHooks.CONTROLLER.begin(worker);tick();
   Fake f=new Fake(){public byte[] start(int d,String ip,String peer){entered.countDown();try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("start gate expired");}catch(InterruptedException e){throw new AssertionError(e);}return super.start(d,ip,peer);}public byte[] watch(int s,String w,String t,boolean r,boolean v){watches[0]++;return bytes("{\"ok\":true}");}};
   bridge=f;set("bridge",f);address("192.0.2.3");
   java.util.concurrent.Future<?> reconnect=worker.submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
   assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
   try{call("requestExtra",new Class<?>[]{int.class,String.class,String.class,boolean.class,boolean.class,String.class,Object.class},2,"rt/extra","type",false,false,"reader",l);}catch(java.lang.reflect.InvocationTargetException e){if(!(e.getCause() instanceof IllegalStateException))throw e;}
   finally{release.countDown();}
   reconnect.get(5,java.util.concurrent.TimeUnit.SECONDS);worker.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
   assertEquals("no arm may target replacement session",0,arms[0]);assertEquals("only the automatic reconnect application is permitted",1,watches[0]);assertEquals("rejected ticket never cancels an arm",0,n.cancels);
  }finally{release.countDown();if(l!=null)assertTrue(DeviceTestHooks.CONTROLLER.end(l,worker).get(5,java.util.concurrent.TimeUnit.SECONDS));control.set(DeviceTestHooks.CONTROLLER,original);}
 }
 @Test public void primaryPolicyChangeBetweenExtraOuterCheckAndAcquisitionHasNoNativeSideEffects() throws Exception {
  java.lang.reflect.Field control=DeviceTestHooks.Controller.class.getDeclaredField("control");control.setAccessible(true);
  DeviceTestHooks.Control original=(DeviceTestHooks.Control)control.get(DeviceTestHooks.CONTROLLER);
  int[] arms={0},watches={0};DeviceTestHooksTest.Fake n=new DeviceTestHooksTest.Fake(){public long[] arm(int s,String stage,long[] id,long e,long nonce){arms[0]++;return super.arm(s,stage,id,e,nonce);}};
  java.util.concurrent.ScheduledExecutorService worker=MainActivity.diagnosticWorker();DeviceTestHooks.Lease l=null;
  java.util.concurrent.CountDownLatch checked=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  try{
   control.set(DeviceTestHooks.CONTROLLER,n);l=DeviceTestHooks.CONTROLLER.begin(worker);
   Fake f=new Fake(){public byte[] watch(int s,String w,String t,boolean r,boolean v){watches[0]++;return bytes("{\"ok\":true}");}};bridge=f;set("bridge",f);tick();
   WatchSlots slots=(WatchSlots)get("watchSlots");WatchSlots.Request r=slots.request(2,"rt/extra","type",false,false);slots.complete(r,"");
   Object config=get("requested");ConnectionPolicy policy=(ConnectionPolicy)get("policy");long epoch=policy.generation();
   worker.submit(()->DeviceTestHooks.CONTROLLER.capture(a,config,1,System.nanoTime(),3,slots.frames())).get();
   DeviceTestHooks.Ticket t=DeviceTestHooks.CONTROLLER.ticket(l,a,r,1,config,epoch,"reader");
   java.util.concurrent.Future<?> attempt=worker.submit(()->{assertTrue(policy.accepts(epoch));checked.countDown();try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("admission gate expired");call("applyExtra",new Class<?>[]{WatchSlots.Request.class,DeviceTestHooks.Ticket.class,long.class},r,t,epoch);}catch(Exception e){throw new RuntimeException(e);}});
   assertTrue(checked.await(5,java.util.concurrent.TimeUnit.SECONDS));
   set("selectedWire","rt/new-primary");call("resubscribe",new Class<?>[]{});assertFalse(policy.accepts(epoch));assertTrue(slots.current(r));
   release.countDown();attempt.get(5,java.util.concurrent.TimeUnit.SECONDS);worker.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
   assertEquals("stale policy must not arm",0,arms[0]);assertEquals("stale policy must not apply extra reader",0,watches[0]);assertEquals(0,n.cancels);
  }finally{release.countDown();if(l!=null)assertTrue(DeviceTestHooks.CONTROLLER.end(l,worker).get(5,java.util.concurrent.TimeUnit.SECONDS));control.set(DeviceTestHooks.CONTROLLER,original);}
 }
 JSONObject diagnostic() throws Exception {StringWriter out=new StringWriter();a.dump("",null,new PrintWriter(out),new String[]{"--pocket-qa-v1"});String s=out.toString();return new JSONObject(s.substring(s.indexOf('\n')+1,s.lastIndexOf("ROS_POCKET_QA_V1_END")).trim()).getJSONObject("testHooks");}
 @Test public void mixedQaAndDiagnosticCaptureIsRejectedAtExport() throws Exception {
  StateFake f=new StateFake();bridge=f;set("bridge",f);tick();assertTrue(diagnostic().getBoolean("valid"));
  ((QaSnapshot)get("qa")).invalidate();assertFalse("cache-only export cannot call two mixed captures coherent",diagnostic().getBoolean("valid"));
 }
 @Test public void ordinaryWorkerPublishesNativeCapabilityAndDumpIsCacheOnly() throws Exception {
  StateFake f=new StateFake();bridge=f;set("bridge",f);tick();
  JSONObject d=diagnostic();assertTrue(d.toString(),d.getBoolean("valid"));assertFalse(d.getBoolean("capability"));assertEquals("pocket-dds",f.thread);
  int states=f.states,polls=f.polls;long sequence=d.getLong("captureSequence");diagnostic();assertEquals(states,f.states);assertEquals(polls,f.polls);
  tick();assertTrue(diagnostic().getLong("captureSequence")>sequence);
  call("disconnect",new Class<?>[]{});assertFalse(diagnostic().getBoolean("valid"));
 }
}
