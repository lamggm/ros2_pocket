package com.lamggm.rospocket;
import android.widget.Button;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

/** Inherits the real Android lifecycle/ordered-worker harness and its regressions. */
public class MultiWatchActivityTest extends ConnectionActivityTest {
 static class MultiFake extends Fake {
  java.util.List<String> calls=new java.util.ArrayList<>();boolean failExtra;
  public byte[] watch(int s,String w,String t,boolean r,boolean l){calls.add("watch:"+s+":"+w+":"+r+":"+l);return bytes(failExtra?"{\"ok\":false,\"error\":\"extra reader failed\"}":"{\"ok\":true}");}
  public byte[] removeWatch(int s){calls.add("remove:"+s);return bytes("{\"ok\":true}");}
 }
 MultiFake extras() throws Exception {
  MultiFake fake=new MultiFake();bridge=fake;set("bridge",fake);
  org.json.JSONArray rows=new org.json.JSONArray();
  for(int i=0;i<4;i++)rows.put(new JSONObject().put("slot",i).put("readerActive",true).put("subscriptionState","receiving").put("fresh",true).put("count",20).put("age",0.1).put("matched",1).put("last","extra-live-"+i).put("qos","best_effort / volatile"));
  fake.snapshot=new JSONObject(fake.snapshot).put("watches",rows).toString();tick();paint();return fake;
 }
 void selectExtra(int slot) throws Exception {
  ((Button)a.getWindow().getDecorView().findViewWithTag("watch-select:"+slot)).performClick();
  call("select",new Class<?>[]{JSONObject.class},new JSONObject().put("wireName","rt/extra"+slot).put("wireType","std_msgs::msg::dds_::String_").put("supported",true));drain();
 }
 void drain() throws Exception {((java.util.concurrent.ScheduledExecutorService)field("DDS_WORKER").get(null)).submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);}
 String extraText(int slot){return ((android.widget.TextView)a.getWindow().getDecorView().findViewWithTag("watch-state:"+slot)).getText().toString();}
 @Test public void delayedExtraSnapshotIsStaleAtPaintTime() throws Exception {
  MultiFake f=extras();
  for(int slot=1;slot<4;slot++)selectExtra(slot);
  tick();paint();
  WatchSlots slots=(WatchSlots)get("watchSlots");
  WatchSlots.Frame[] frames={null,slots.frame(1),slots.frame(2),slots.frame(3)};
  call("renderExtras",new Class<?>[]{JSONObject.class,WatchSlots.Frame[].class,long.class},new JSONObject(f.snapshot),frames,System.nanoTime()-4_000_000_000L);
  for(int slot=1;slot<4;slot++){
   String status=extraText(slot);
   assertTrue("Delayed slot "+slot+" must explicitly be stale: "+status,status.contains("SEM DADOS RECENTES"));
   assertFalse(status.contains("receiving"));assertFalse(status.contains("RECEBENDO"));
   assertNotEquals(0xff48d7bb,((android.widget.TextView)a.getWindow().getDecorView().findViewWithTag("watch-state:"+slot)).getCurrentTextColor());
  }
 }
 @Test public void changingPrimaryDoesNotBlankOtherLiveWatch() throws Exception {
  extras();selectExtra(1);tick();paint();assertTrue(extraText(1).contains("RECEBENDO"));
  set("selectedWire","rt/replacement");call("resubscribe",new Class<?>[]{});
  assertTrue("A primary request must not blank independent telemetry",extraText(1).contains("RECEBENDO"));
 }
 @Test public void disconnectImmediatelyClearsAllExtraTelemetry() throws Exception {
  extras();selectExtra(1);tick();paint();assertTrue(extraText(1).contains("RECEBENDO"));
  call("disconnect",new Class<?>[]{});assertFalse(extraText(1).contains("RECEBENDO"));assertFalse(extraText(1).contains("extra-live"));
 }
 @Test public void extraQosFailureRemovalRecoveryAndHomeAreIndependent() throws Exception {
  MultiFake f=extras();selectExtra(1);selectExtra(2);selectExtra(3);tick();paint();
  assertEquals(3,f.calls.size());assertEquals(1,f.subscriptions);
  ((android.widget.CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);drain();
  assertTrue(f.calls.get(3).endsWith(":true:false"));assertEquals(1,f.subscriptions);
  f.failExtra=true;selectExtra(2);tick();paint();assertTrue(extraText(2).contains("extra reader failed"));assertTrue(extraText(1).contains("RECEBENDO"));
  set("lastUi",0L);tick();paint();assertTrue(extraText(2).contains("extra reader failed"));
  f.failExtra=false;selectExtra(2);tick();paint();assertTrue(extraText(2).contains("RECEBENDO"));
  ((Button)a.getWindow().getDecorView().findViewWithTag("watch-remove")).performClick();drain();tick();paint();assertTrue(extraText(2).contains("sem assinatura"));assertTrue(extraText(1).contains("RECEBENDO"));
  call("onStop",new Class<?>[]{});drain();paint();assertFalse(extraText(1).contains("RECEBENDO"));
  int before=f.calls.size();call("onStart",new Class<?>[]{});drain();paint();assertEquals(before+2,f.calls.size());assertEquals(2,f.subscriptions);
 }
 @Test public void queuedExtraSnapshotCannotRepaintReplacementOrRemovedSlot() throws Exception {
  extras();selectExtra(1);tick();paint();set("lastUi",0L);tick();
  selectExtra(1);paint();assertFalse(extraText(1).contains("extra-live"));
  tick();paint();assertTrue(extraText(1).contains("RECEBENDO"));set("lastUi",0L);tick();
  ((Button)a.getWindow().getDecorView().findViewWithTag("watch-remove")).performClick();drain();paint();assertFalse(extraText(1).contains("extra-live"));
 }
 @Test public void removingPrimaryWhileTickQueuedCannotRepaintOldReader() throws Exception {
  MultiFake f=extras();set("lastUi",0L);
  java.util.concurrent.ScheduledExecutorService executor=(java.util.concurrent.ScheduledExecutorService)get("worker");
  java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.Future<?> queued=executor.submit(()->{entered.countDown();try{release.await();call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
  assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
  try{((Button)a.getWindow().getDecorView().findViewWithTag("watch-remove")).performClick();}finally{release.countDown();}
  queued.get();drain();paint();assertFalse(text(R.id.counts).contains("RECEBENDO"));
 }

 @Test public void selectedWatchEditorRemainsIdentifiableAcrossRefresh() throws Exception {
  extras();selectExtra(2);tick();paint();
  android.widget.TextView editor=a.getWindow().getDecorView().findViewWithTag("watch-editor");
  assertNotNull(editor);assertTrue(editor.getText().toString().contains("Watch 3"));
 }
 @Test public void fourAccessibleSlotSelectorsKeepPrimaryReaderIndependent() throws Exception {
  tick();paint();
  for(int i=0;i<4;i++)assertNotNull("Accessible watch slot "+i,a.getWindow().getDecorView().findViewWithTag("watch-select:"+i));
  ((Button)a.getWindow().getDecorView().findViewWithTag("watch-select:1")).performClick();
  JSONObject topic=new JSONObject().put("wireName","rt/second").put("wireType","std_msgs::msg::dds_::String_").put("supported",true);
  call("select",new Class<?>[]{JSONObject.class},topic);
  ((java.util.concurrent.ScheduledExecutorService)get("worker")).submit(()->{}).get();
  assertEquals("rt/numeric",get("selectedWire"));
  assertEquals("Only the explicit extra slot may change",1,bridge.subscriptions);
 }
}
