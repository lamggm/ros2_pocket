package com.lamggm.rospocket;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import org.json.JSONObject;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class DeviceTestHooksTest {
 static class Fake implements DeviceTestHooks.Control {
  boolean armed=false,active=false,reject=false;int cancels=0;long epoch=0,session=3,gen=7,request=11;
  public boolean begin(long epoch){this.epoch=epoch;active=true;return true;}
  public boolean end(long epoch){if(armed)return false;active=false;return true;}
  public long[] identity(int slot){return new long[]{session,gen,request};}
  public long[] arm(int slot,String stage,long[] id,long epoch,long nonce){if(reject||armed)return null;armed=true;return new long[]{slot,3,8,11,epoch,nonce};}
  public boolean cancel(long[] handle){cancels++;armed=false;return true;}
  public String receipt(){return "{}";}
  public String state(){return "{\"capability\":true,\"contract\":\"ros-pocket-dds-fault-v1\",\"armed\":"+armed+",\"runnerActive\":"+active+",\"nativeLeaseEpoch\":"+(active?epoch:0)+"}";}
 }
 static DeviceTestHooks.Ticket ticket(ExecutorService worker,DeviceTestHooks.Controller c,DeviceTestHooks.Lease l,Object o,WatchSlots.Request r,int life,Object config,long policy,String stage) throws Exception {
  worker.submit(()->c.capture(o,config,life,System.nanoTime(),3,new WatchSlots().frames())).get(2,TimeUnit.SECONDS);
  return c.ticket(l,o,r,life,config,policy,stage);
 }
 @Test public void missingExpiredPendingOrForeignCaptureCannotMintTicketAndCreationDoesNotReadNative() throws Exception {
  ExecutorService worker=Executors.newSingleThreadExecutor();int[] reads={0};Fake n=new Fake(){public long[] identity(int slot){reads[0]++;return super.identity(slot);}};DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(n);
  try{DeviceTestHooks.Lease l=c.begin(worker);Object o=new Object();WatchSlots slots=new WatchSlots();WatchSlots.Request r=slots.request(1,"rt/a","type",false,false);
   for(int scenario=0;scenario<5;scenario++){
    if(scenario>0){final int which=scenario;worker.submit(()->c.capture(which==3?new Object():o,o,1,System.nanoTime()-(which==2?3_000_000_000L:0),3,which==4?slots.frames():new WatchSlots().frames())).get();}
    int before=reads[0];try{DeviceTestHooks.Ticket t=c.ticket(l,o,r,scenario==1?2:1,o,2,"reader");c.reject(t);fail("unavailable/expired/foreign/pending capture minted ticket");}catch(IllegalStateException expected){}
    assertEquals("ticket creation must never fetch JNI identity",before,reads[0]);
   }
   c.cancelOwner(o);assertTrue(c.end(l,worker).get());
  }finally{worker.shutdownNow();}
 }
 @Test public void changedSlotGenerationOrNextRequestIsNeverRebound() throws Exception {
  for(boolean generation:new boolean[]{true,false}){
   ExecutorService worker=Executors.newSingleThreadExecutor();Fake n=new Fake();DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(n);
   try{DeviceTestHooks.Lease l=c.begin(worker);Object o=new Object();WatchSlots.Request r=new WatchSlots().get(1);DeviceTestHooks.Ticket t=ticket(worker,c,l,o,r,1,o,2,"reader");if(generation)n.gen++;else n.request++;
    try{c.before(t,o,r,1,o,2);fail("native identity changed after ticket");}catch(IllegalStateException expected){}
    assertFalse(n.armed);assertEquals(0,n.cancels);assertTrue(c.end(l,worker).get());
   }finally{worker.shutdownNow();}
  }
 }
 @Test public void nativeReplacementAfterTicketCannotRetargetTheOperation() throws Exception {
  ExecutorService worker=Executors.newSingleThreadExecutor();
  Fake n=new Fake();
  DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(n);
  try{DeviceTestHooks.Lease l=c.begin(worker);Object o=new Object();WatchSlots.Request r=new WatchSlots().get(1);
   DeviceTestHooks.Ticket t=ticket(worker,c,l,o,r,1,o,2,"reader");n.session=4;
   try{DeviceTestHooks.Acquisition a=c.before(t,o,r,1,o,2);c.after(a);fail("changed native identity was retargeted");}catch(IllegalStateException expected){}
   assertEquals("a rejected ticket owns no cancellation",0,n.cancels);assertFalse(n.armed);assertTrue(c.end(l,worker).get());
  }finally{worker.shutdownNow();}
 }
 @Test public void ownerStopCancelsOnlyItsPendingTicketWithoutEndingRunnerLease() throws Exception {
  ExecutorService worker=Executors.newSingleThreadExecutor();Fake n=new Fake();DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(n);
  try{DeviceTestHooks.Lease l=c.begin(worker);Object old=new Object(),next=new Object();WatchSlots.Request r=new WatchSlots().get(1);
   DeviceTestHooks.Ticket stale=ticket(worker,c,l,old,r,1,old,2,"reader");c.cancelOwner(old);assertEquals("ACTIVE",c.phase());
   try{c.before(stale,old,r,1,old,2);fail("stopped owner armed");}catch(IllegalStateException expected){}
   DeviceTestHooks.Ticket replacement=ticket(worker,c,l,next,r,1,next,2,"reader");c.cancelOwner(old);
   DeviceTestHooks.Acquisition acquired=c.before(replacement,next,r,1,next,2);c.after(acquired);assertTrue(c.end(l,worker).get());
  }finally{worker.shutdownNow();}
 }
 @Test public void shutdownCannotBecomeInactiveOnUnverifiedNativeLeaseEnd() throws Exception {
  ExecutorService worker=Executors.newSingleThreadExecutor();Fake n=new Fake(){public boolean end(long e){return true;}};DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(n);
  try{DeviceTestHooks.Lease l=c.begin(worker);assertFalse(c.end(l,worker).get());assertEquals("UNKNOWN",c.phase());}finally{worker.shutdownNow();}
 }
 @Test public void closingBeforeQueuedAdmissionRejectsItAndDrains() throws Exception {
  ExecutorService worker=Executors.newSingleThreadExecutor();Fake nativeApi=new Fake();DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(nativeApi);
  try {
   DeviceTestHooks.Lease lease=c.begin(worker);Object owner=new Object();WatchSlots slots=new WatchSlots();WatchSlots.Request r=slots.request(1,"rt/test","type",false,false);
   DeviceTestHooks.Ticket t=ticket(worker,c,lease,owner,r,4,owner,5,"reader");
   CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
   worker.execute(()->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}});assertTrue(entered.await(2,TimeUnit.SECONDS));
   Future<Boolean> admitted=worker.submit(()->{try{c.before(t,owner,r,4,owner,5);return true;}catch(IllegalStateException e){return false;}});
   CompletableFuture<Boolean> ended=c.end(lease,worker);assertEquals("CLOSING",c.phase());assertFalse(ended.isDone());
   release.countDown();assertFalse(admitted.get(2,TimeUnit.SECONDS));assertTrue(ended.get(2,TimeUnit.SECONDS));assertEquals("INACTIVE",c.phase());assertFalse(nativeApi.armed);
  }finally{worker.shutdownNow();}
 }
 @Test public void closingWhileArmedCannotPublishInactiveBeforeWorkerCleanup() throws Exception {
  ExecutorService worker=Executors.newSingleThreadExecutor();Fake nativeApi=new Fake();DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(nativeApi);
  CountDownLatch release=new CountDownLatch(1);
  try {
   DeviceTestHooks.Lease lease=c.begin(worker);Object owner=new Object();WatchSlots.Request r=new WatchSlots().get(1);
   DeviceTestHooks.Ticket t=ticket(worker,c,lease,owner,r,4,owner,5,"topic");CountDownLatch armed=new CountDownLatch(1);
   Future<?> op=worker.submit(()->{DeviceTestHooks.Acquisition a=c.before(t,owner,r,4,owner,5);armed.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}finally{c.after(a);}});
   assertTrue(armed.await(2,TimeUnit.SECONDS));CompletableFuture<Boolean> ended=c.end(lease,worker);
   assertEquals("CLOSING",c.phase());assertFalse(ended.isDone());
   try{c.begin(worker);fail("replacement during close");}catch(IllegalStateException expected){}
   release.countDown();op.get(2,TimeUnit.SECONDS);assertTrue(ended.get(2,TimeUnit.SECONDS));
   DeviceTestHooks.Lease replacement=c.begin(worker);assertFalse(c.end(lease,worker).get());assertEquals("ACTIVE",c.phase());assertTrue(c.end(replacement,worker).get());
  }finally{release.countDown();worker.shutdownNow();}
 }
 @Test public void receiptMustMatchAcquiredNativeOwnershipAndProjectionMustBeFresh() throws Exception {
  ExecutorService worker=Executors.newSingleThreadExecutor();Fake n=new Fake();DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(n);
  try{DeviceTestHooks.Lease l=c.begin(worker);Object o=new Object();WatchSlots.Request r=new WatchSlots().get(1);DeviceTestHooks.Ticket t=ticket(worker,c,l,o,r,1,o,2,"reader");DeviceTestHooks.Acquisition a=c.before(t,o,r,1,o,2);
   JSONObject receipt=new JSONObject().put("slot",1).put("targetSlot",1).put("session",3).put("attemptedGeneration",8).put("request",11).put("lease",l.epoch).put("nonce",t.nonce).put("stage","reader");
   assertTrue(t.matchesReceipt(receipt));receipt.put("nonce",t.nonce+1);assertFalse(t.matchesReceipt(receipt));c.after(a);
   JSONObject state=new JSONObject(n.state()).put("revision",1).put("session",3);
   c.publish(c.revision(),state,new JSONObject().put("sessionGeneration",3),System.nanoTime()-3_000_000_000L,1,2,new WatchSlots().frames());
   try{c.projection(l);fail("stale ordinary poll must not qualify");}catch(IllegalStateException expected){}
   assertTrue(c.end(l,worker).get());
  }finally{worker.shutdownNow();}
 }
 @Test public void oldCleanupCannotCancelReplacementOperation() throws Exception {
  ExecutorService worker=Executors.newSingleThreadExecutor();Fake n=new Fake();DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(n);
  try{DeviceTestHooks.Lease l=c.begin(worker);Object o=new Object();WatchSlots.Request r=new WatchSlots().get(1);
   DeviceTestHooks.Ticket first=ticket(worker,c,l,o,r,1,o,2,"reader");DeviceTestHooks.Acquisition a=c.before(first,o,r,1,o,2);c.after(a);
   DeviceTestHooks.Ticket next=ticket(worker,c,l,o,r,1,o,2,"reader");DeviceTestHooks.Acquisition b=c.before(next,o,r,1,o,2);int cancelled=n.cancels;
   c.after(a);assertTrue("stale finally must not disarm replacement",n.armed);assertEquals(cancelled,n.cancels);
   c.after(b);assertTrue(c.end(l,worker).get());
  }finally{worker.shutdownNow();}
 }
 @Test public void publicationAndExportAreRevisionFencedWithoutExtraPolling() throws Exception {
  Fake n=new Fake();DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(n);
  JSONObject state=new JSONObject(n.state()).put("revision",1).put("session",3);
  JSONObject poll=new JSONObject().put("sessionGeneration",3);
  long before=c.revision();c.invalidate();
  assertFalse(c.publish(before,state,poll,System.nanoTime(),1,2,new WatchSlots().frames()));
  assertFalse(c.export(System.nanoTime()).getBoolean("valid"));
  assertTrue(c.publish(c.revision(),state,poll,System.nanoTime(),1,2,new WatchSlots().frames()));
  long sequence=c.export(System.nanoTime()).getLong("captureSequence");
  assertTrue(c.publish(c.revision(),state,poll,System.nanoTime(),1,2,new WatchSlots().frames()));
  assertTrue(c.export(System.nanoTime()).getLong("captureSequence")>sequence);
  c.invalidate();assertFalse(c.export(System.nanoTime()).getBoolean("valid"));
 }
 @Test public void nativeSessionEpochAndIntegerStateMustMatchOrdinaryPoll() throws Exception {
  DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(new Fake());
  JSONObject base=new JSONObject("{\"capability\":true,\"contract\":\"ros-pocket-dds-fault-v1\",\"armed\":false,\"runnerActive\":false,\"revision\":1,\"session\":3,\"nativeLeaseEpoch\":0}");
  for(String key:new String[]{"session","revision","nativeLeaseEpoch"}){
   JSONObject bad=new JSONObject(base.toString()).put(key,key.equals("revision")?0.5:4);
   assertFalse(key,c.publish(c.revision(),bad,new JSONObject().put("sessionGeneration",3),System.nanoTime(),1,2,new WatchSlots().frames()));
  }
 }
 @Test public void unknownOrMalformedNativeStateCannotBecomeSafeFalse() throws Exception {
  DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(new Fake());
  for(Object value:new Object[]{JSONObject.NULL,0,"false"}){
   JSONObject state=new JSONObject("{\"capability\":true,\"contract\":\"ros-pocket-dds-fault-v1\",\"revision\":1,\"session\":3,\"runnerActive\":false}").put("armed",value);
   assertFalse(c.publish(c.revision(),state,new JSONObject(),System.nanoTime(),1,2,new WatchSlots().frames()));
   assertFalse(c.export(System.nanoTime()).getBoolean("valid"));
  }
 }
 @Test public void rejectedAcquisitionNeverCancelsIncumbent() throws Exception {
  ExecutorService worker=Executors.newSingleThreadExecutor();Fake n=new Fake();DeviceTestHooks.Controller c=new DeviceTestHooks.Controller(n);
  try{DeviceTestHooks.Lease l=c.begin(worker);Object o=new Object();WatchSlots.Request r=new WatchSlots().get(1);DeviceTestHooks.Ticket t=ticket(worker,c,l,o,r,1,o,2,"reader");n.reject=true;
   try{c.before(t,o,r,1,o,2);fail("rejection");}catch(IllegalStateException expected){}
   assertEquals(0,n.cancels);assertTrue(c.end(l,worker).get());
  }finally{worker.shutdownNow();}
 }
}
