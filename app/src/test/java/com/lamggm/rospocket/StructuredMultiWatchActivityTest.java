package com.lamggm.rospocket;

import android.widget.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real host Views/JSON and deterministic DDS-worker barriers; not physical JNI evidence. */
public class StructuredMultiWatchActivityTest extends MultiWatchActivityTest {
 private JSONObject tree(int slot) throws Exception {
  return new JSONObject().put("schemaVersion",1).put("type","geometry_msgs/msg/Vector3")
   .put("numericPaths",new JSONArray().put("x")).put("root",new JSONObject().put("path","").put("kind","object")
    .put("children",new JSONArray().put(new JSONObject().put("path","x").put("kind","number").put("display","slot-"+slot).put("value",slot+.25))));
 }
 private MultiFake liveTrees() throws Exception {
  MultiFake f=extras();JSONObject s=new JSONObject(f.snapshot);s.put("message",tree(0));
  for(int i=0;i<4;i++)s.getJSONArray("watches").getJSONObject(i).put("message",tree(i)).put("generation",100+i);
  f.snapshot=s.toString();for(int i=1;i<4;i++)selectExtra(i);
  set("lastUi",0L);tick();paint();return f;
 }
 private StructuredMessageView view(int slot) {
  StructuredMessageView v=a.getWindow().getDecorView().findViewWithTag(slot==0?"structuredMessage":"structuredMessage:"+slot);
  assertNotNull("Missing independent structured inspector for Watch "+(slot+1),v);return v;
 }
 private void hasTree(int slot) {
  Button x=view(slot).findViewWithTag("numeric:x");assertNotNull("Missing slot "+slot+" tree",x);
  assertTrue("Wrong slot projected into Watch "+(slot+1),x.getText().toString().contains("slot-"+slot));
 }
 private void cleared(int slot) {assertNull("Old tree resurrected in slot "+slot,view(slot).findViewWithTag("numeric:x"));}
 @Test public void noPrimarySelectionDoesNotInventPendingReader() throws Exception {
  set("selectedWire","");set("selectedType","");
  bridge.snapshot=new JSONObject(bridge.snapshot).put("readerActive",false).put("subscription","").put("count",0).put("fresh",false).put("age",-1).toString();
  tick();paint();assertEquals("Sem assinatura ativa",text(R.id.counts));
  assertFalse(text(R.id.counts).contains("RECONECTANDO"));
 }
 @Test public void allFourTreesAreIndependentAndFieldClicksNeverMutateReaders() throws Exception {
  MultiFake f=liveTrees();int calls=f.calls.size(),subscriptions=f.subscriptions;
  for(int i=0;i<4;i++){hasTree(i);((Button)view(i).findViewWithTag("numeric:x")).performClick();}
  for(int i=0;i<4;i++)assertTrue(((TextView)view(i).findViewWithTag("selectedNumericPath")).getText().toString().endsWith("x"));
  assertEquals(calls,f.calls.size());assertEquals(subscriptions,f.subscriptions);
 }
 @Test public void extraReplacementAndRemovalClearOnlyTheirTreeAndSelection() throws Exception {
  liveTrees();((Button)view(1).findViewWithTag("numeric:x")).performClick();set("lastUi",0L);tick();
  selectExtra(1);cleared(1);hasTree(0);hasTree(2);hasTree(3);paint();cleared(1);
  tick();paint();hasTree(1);assertFalse(((TextView)view(1).findViewWithTag("selectedNumericPath")).getText().toString().endsWith("x"));
  set("lastUi",0L);tick();((Button)a.getWindow().getDecorView().findViewWithTag("watch-remove")).performClick();drain();paint();cleared(1);hasTree(2);
 }
 @Test public void primaryRemovalImmediatelyClearsTreeWithoutBlankingExtras() throws Exception {
  liveTrees();((Button)a.getWindow().getDecorView().findViewWithTag("watch-select:0")).performClick();
  ((Button)a.getWindow().getDecorView().findViewWithTag("watch-remove")).performClick();cleared(0);hasTree(1);hasTree(2);hasTree(3);
 }
 @Test public void qosRequestDuringBlockedPollCannotResurrectOldTreeAfterApply() throws Exception {
  MultiFake f=liveTrees();((Button)a.getWindow().getDecorView().findViewWithTag("watch-select:1")).performClick();
  f.pollEntered=new CountDownLatch(1);f.pollRelease=new CountDownLatch(1);set("lastUi",0L);
  Future<?> pending=((ScheduledExecutorService)get("worker")).submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
  assertTrue(f.pollEntered.await(5,TimeUnit.SECONDS));
  try{((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);cleared(1);hasTree(2);}finally{f.pollRelease.countDown();}
  pending.get(5,TimeUnit.SECONDS);drain();paint();cleared(1);hasTree(2);hasTree(3);
  f.pollEntered=null;f.pollRelease=null;tick();paint();hasTree(1);
 }
 @Test public void pendingFrameBeforePolicyCaptureCannotPaintAfterQueuedReplacement() throws Exception {
  liveTrees();set("lastUi",0L);ScheduledExecutorService executor=(ScheduledExecutorService)get("worker");
  Thread thread=executor.submit(()->Thread.currentThread()).get(5,TimeUnit.SECONDS);Future<?> pending;
  synchronized(get("policy")){
   pending=executor.submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean blocked=false;
   while(System.nanoTime()<deadline){for(StackTraceElement e:thread.getStackTrace())if(e.getClassName().endsWith("ConnectionPolicy")&&e.getMethodName().equals("identity")&&thread.getState()==Thread.State.BLOCKED)blocked=true;if(blocked)break;Thread.sleep(2);}
   assertTrue(blocked);
   ((Button)a.getWindow().getDecorView().findViewWithTag("watch-select:1")).performClick();
   call("select",new Class<?>[]{JSONObject.class},new JSONObject().put("wireName","rt/reassigned").put("wireType","geometry_msgs::msg::dds_::Vector3_").put("supported",true));
  }
  pending.get(5,TimeUnit.SECONDS);drain();paint();cleared(1);hasTree(2);
 }
 @Test public void delayedPaintMarksEveryTreeStaleAndFailureClearsOnlyItsTree() throws Exception {
  MultiFake f=liveTrees();WatchSlots slots=(WatchSlots)get("watchSlots");
  WatchSlots.Frame[] frames={null,slots.frame(1),slots.frame(2),slots.frame(3)};
  call("renderExtras",new Class<?>[]{JSONObject.class,WatchSlots.Frame[].class,long.class},new JSONObject(f.snapshot),frames,System.nanoTime()-4_000_000_000L);
  for(int i=1;i<4;i++){hasTree(i);assertTrue(((TextView)view(i).findViewWithTag("structuredFreshness")).getText().toString().contains("ÚLTIMO VALOR"));assertTrue(view(i).findViewWithTag("numeric:x").getAlpha()<1f);}
  f.failExtra=true;selectExtra(2);tick();paint();cleared(2);hasTree(1);hasTree(3);
 }
 @Test public void stopDisconnectAndNetworkLossFenceEveryQueuedTree() throws Exception {
  liveTrees();set("lastUi",0L);tick();cm.clearAllNetworks();tick();paint();for(int i=0;i<4;i++)cleared(i);
  call("onStop",new Class<?>[]{});drain();paint();for(int i=0;i<4;i++)cleared(i);
  call("disconnect",new Class<?>[]{});paint();for(int i=0;i<4;i++)cleared(i);
 }
}
