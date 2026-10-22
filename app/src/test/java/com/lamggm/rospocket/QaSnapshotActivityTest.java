package com.lamggm.rospocket;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Host Android runtime with an explicit JNI double; not physical DDS evidence. */
public class QaSnapshotActivityTest extends ConnectionActivityTest {
 @org.junit.Before public void debugOnly(){org.junit.Assume.assumeTrue(BuildConfig.DEBUG);}
 private static final String BEGIN="ROS_POCKET_QA_V1_BEGIN",END="ROS_POCKET_QA_V1_END";
 private static JSONObject sample(long count) throws Exception {
  JSONObject root=new JSONObject().put("running",true).put("sessionGeneration",7).put("topics",new JSONArray()).put("nodes",new JSONArray());
  JSONArray rows=new JSONArray();
  for(int slot=0;slot<4;slot++)rows.put(new JSONObject().put("slot",slot).put("generation",10+slot)
   .put("subscription","/numeric").put("type","std_msgs::msg::dds_::Float64_")
   .put("readerActive",true).put("subscriptionState","receiving").put("fresh",true)
   .put("count",count+slot).put("matched",1).put("age",0.1).put("hz",2.0).put("error","")
   .put("receivedMonotonicNs",System.nanoTime()).put("last","PRIVATE_PAYLOAD_DO_NOT_EXPORT")
   .put("numericFields",new JSONArray().put(new JSONObject().put("path","data").put("value",12.5).put("state","finite"))));
  return root.put("watches",rows);
 }
 private MultiWatchActivityTest.MultiFake live() throws Exception {
  MultiWatchActivityTest.MultiFake f=new MultiWatchActivityTest.MultiFake();bridge=f;set("bridge",f);
  for(int slot=1;slot<4;slot++){
   a.getWindow().getDecorView().findViewWithTag("watch-select:"+slot).performClick();
   call("select",new Class<?>[]{JSONObject.class},new JSONObject().put("wireName","rt/numeric").put("wireType","std_msgs::msg::dds_::Float64_").put("supported",true));
  }
  drainQa();f.snapshot=sample(20).toString();tick();paint();return f;
 }
 private void drainQa() throws Exception {((ScheduledExecutorService)get("worker")).submit(()->{}).get(5,TimeUnit.SECONDS);}
 private String rawDump(String... args){StringWriter out=new StringWriter();a.dump("",null,new PrintWriter(out),args);return out.toString();}
 private JSONObject dump() throws Exception {
  String out=rawDump("--pocket-qa-v1");
  assertTrue("Exact QA command must produce a bounded frame",out.contains(BEGIN+"\n"));
  assertTrue(out.contains("\n"+END));
  return new JSONObject(out.substring(out.indexOf(BEGIN)+BEGIN.length(),out.indexOf(END)).trim());
 }
 @Test public void dumpExportsFourCachedCountersWithoutPollingOrLeakingPayload() throws Exception {
  live();int polls=bridge.polls,subscriptions=bridge.subscriptions;
  JSONObject first=dump();assertEquals(1,first.getInt("schemaVersion"));assertTrue(first.getBoolean("valid"));
  JSONArray watches=first.getJSONArray("watches");assertEquals(4,watches.length());
  for(int slot=0;slot<4;slot++){
   JSONObject w=watches.getJSONObject(slot);assertEquals(slot,w.getInt("slot"));
   assertEquals(20+slot,w.getLong("count"));assertEquals(10+slot,w.getLong("readerGeneration"));
  }
  assertFalse(first.toString().contains("PRIVATE_PAYLOAD"));
  assertEquals(first.getLong("capturedMonotonicNs"),dump().getLong("capturedMonotonicNs"));
  assertEquals("Export is not a second DDS intake path",polls,bridge.polls);
  assertEquals(subscriptions,bridge.subscriptions);
 }
 @Test public void stopDuringBlockedPollInvalidatesCacheAndLatePollCannotRestoreIt() throws Exception {
  MultiWatchActivityTest.MultiFake f=live();assertTrue(dump().getBoolean("valid"));
  f.pollEntered=new CountDownLatch(1);f.pollRelease=new CountDownLatch(1);
  Future<?> pending=((ScheduledExecutorService)get("worker")).submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
  assertTrue(f.pollEntered.await(5,TimeUnit.SECONDS));
  try{call("onStop",new Class<?>[]{});assertFalse(dump().getBoolean("valid"));}
  finally{f.pollRelease.countDown();}
  pending.get(5,TimeUnit.SECONDS);paint();assertFalse(dump().getBoolean("valid"));
  assertEquals(0,dump().getJSONArray("watches").length());
 }
 @Test public void disconnectAndNewConnectionCannotExportPreviousSession() throws Exception {
  live();call("disconnect",new Class<?>[]{});assertFalse(dump().getBoolean("valid"));
  drainQa();call("requestConnection",new Class<?>[]{boolean.class},false);
  assertFalse(dump().getBoolean("valid"));
 }
 @Test public void extraReplacementImmediatelyInvalidatesCacheWhilePollIsBlocked() throws Exception {
  MultiWatchActivityTest.MultiFake f=live();
  f.pollEntered=new CountDownLatch(1);f.pollRelease=new CountDownLatch(1);
  Future<?> pending=((ScheduledExecutorService)get("worker")).submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
  assertTrue(f.pollEntered.await(5,TimeUnit.SECONDS));
  try{
   a.getWindow().getDecorView().findViewWithTag("watch-select:2").performClick();
   call("requestExtra",new Class<?>[]{int.class,String.class,String.class,boolean.class,boolean.class},2,"rt/replacement","std_msgs::msg::dds_::Float64_",false,false);
   assertFalse(dump().getBoolean("valid"));
  }finally{f.pollRelease.countDown();}
  pending.get(5,TimeUnit.SECONDS);drainQa();assertFalse(dump().getBoolean("valid"));
 }
 @Test public void actualNetworkLossInvalidatesCachedCounters() throws Exception {
  live();cm.clearAllNetworks();tick();paint();assertFalse(dump().getBoolean("valid"));
 }
 @Test public void plotStatisticsSeparateFrozenDisplayFromRealIntake() throws Exception {
  MultiWatchActivityTest.MultiFake f=live();PlotView view=a.getWindow().getDecorView().findViewWithTag("live-plot");
  for(int slot=0;slot<4;slot++)view.findViewWithTag("plot-field:Watch "+(slot+1)+" · /numeric\ndata").performClick();
  f.snapshot=sample(30).toString();set("lastUi",0L);tick();paint();
  view.findViewWithTag("plot-pause").performClick();
  f.snapshot=sample(40).toString();tick();
  JSONObject data=dump(),intake=data.getJSONObject("plotIntake"),display=data.getJSONObject("plotDisplay");
  assertTrue(display.getBoolean("paused"));assertEquals(600,intake.getInt("capacityPerSeries"));
  assertEquals(30_000_000_000L,intake.getLong("windowNs"));
  assertEquals(4,intake.getJSONArray("series").length());
  for(int slot=0;slot<4;slot++){
   assertEquals(slot,intake.getJSONArray("series").getJSONObject(slot).getInt("slot"));
   assertEquals(2,intake.getJSONArray("series").getJSONObject(slot).getInt("pointCount"));
   assertEquals(1,display.getJSONArray("series").getJSONObject(slot).getInt("pointCount"));
  }
  assertFalse(display.getBoolean("chartFullyVisible"));
 }
 @Test public void expiredCaptureAndMissingMetricsFailClosedRatherThanInventingZero() throws Exception {
  MultiWatchActivityTest.MultiFake f=live();JSONObject first=dump();
  QaSnapshot qa=(QaSnapshot)get("qa");
  assertFalse(qa.export(first.getLong("capturedMonotonicNs")+2_000_000_001L).getBoolean("valid"));
  JSONObject missing=sample(30);missing.getJSONArray("watches").getJSONObject(2).remove("matched");
  f.snapshot=missing.toString();tick();JSONObject broken=dump();assertFalse(broken.getBoolean("valid"));
  assertTrue(broken.getJSONArray("watches").getJSONObject(2).isNull("matched"));
 }
 @Test public void stableResourceIdsIdentifyEachWatchAndTheActualChart() throws Exception {
  String[] ids={"watch_select_0","watch_select_1","watch_select_2","watch_select_3","watch_state_1","watch_state_2","watch_state_3","watch_editor","watch_remove","plot_panel","plot_pause","plot_chart"};
  for(String name:ids){
   int id=a.getResources().getIdentifier(name,"id",a.getPackageName());
   assertNotEquals("Stable resource ID "+name,0,id);assertNotNull(name,a.findViewById(id));
  }
  assertSame(a.findViewById(R.id.counts),a.getWindow().getDecorView().findViewWithTag("watch-state:0"));
 }
 @Test public void appliedSameTopicRequestCannotExportAnObsoleteNativeReaderAsCurrent() throws Exception {
  MultiWatchActivityTest.MultiFake f=live();
  call("requestExtra",new Class<?>[]{int.class,String.class,String.class,boolean.class,boolean.class},2,"rt/numeric","std_msgs::msg::dds_::Float64_",true,false);
  drainQa();f.snapshot=sample(30).toString();tick();assertFalse(dump().getBoolean("valid"));
  JSONObject current=sample(40);current.getJSONArray("watches").getJSONObject(2).put("generation",13);
  f.snapshot=current.toString();tick();assertTrue(dump().getBoolean("valid"));
 }
 @Test public void metadataIsBoundedAllowlistedAndImmutableWithExactLongCounters() throws Exception {
  MultiWatchActivityTest.MultiFake f=live();JSONObject source=sample(30);
  source.put("last","PRIVATE_PAYLOAD").put("rosout",new JSONObject().put("logs","PRIVATE_LOG"));
  JSONObject row=source.getJSONArray("watches").getJSONObject(2);
  row.put("error","PRIVATE_ERROR_"+"x".repeat(100_000)).put("last","PRIVATE_MESSAGE")
   .put("count",Long.MAX_VALUE-10);
  f.snapshot=source.toString();tick();String output=rawDump();JSONObject first=dump();
  assertTrue(output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<QaSnapshot.MAX_JSON_BYTES);
  for(String privateValue:new String[]{"PRIVATE_PAYLOAD","PRIVATE_LOG","PRIVATE_ERROR","PRIVATE_MESSAGE","rt/numeric","std_msgs::"})assertFalse(privateValue,output.contains(privateValue));
  assertEquals(Long.MAX_VALUE-10,first.getJSONArray("watches").getJSONObject(2).getLong("count"));
  assertTrue(first.getJSONArray("watches").getJSONObject(2).getBoolean("hasError"));
  assertFalse(first.getJSONArray("watches").getJSONObject(2).getBoolean("freshAtExport"));
  first.getJSONArray("watches").getJSONObject(0).put("count",999);
  assertEquals(30,dump().getJSONArray("watches").getJSONObject(0).getLong("count"));
  int polls=f.polls,starts=f.starts,subscriptions=f.subscriptions,calls=f.calls.size();
  for(int i=0;i<5;i++)dump();
  assertEquals(polls,f.polls);assertEquals(starts,f.starts);assertEquals(subscriptions,f.subscriptions);assertEquals(calls,f.calls.size());
 }
 @Test public void malformedOrderCountersAndNativeStatesNeverBecomeValidZeros() throws Exception {
  MultiWatchActivityTest.MultiFake f=live();JSONObject source=sample(30);
  source.getJSONArray("watches").getJSONObject(1).put("slot",0);
  source.getJSONArray("watches").getJSONObject(2).put("count",1.5);
  source.getJSONArray("watches").getJSONObject(3).put("subscriptionState","PRIVATE_STATE_"+"x".repeat(100_000));
  f.snapshot=source.toString();tick();JSONObject out=dump();assertFalse(out.getBoolean("valid"));
  assertTrue(out.getJSONArray("watches").getJSONObject(1).isNull("count"));
  assertTrue(out.getJSONArray("watches").getJSONObject(2).isNull("count"));
  assertEquals("unknown",out.getJSONArray("watches").getJSONObject(3).getString("state"));
  assertFalse(rawDump().contains("PRIVATE_STATE_"));
 }
 @Test public void fractionalNegativeAgeIsInvalidAndDumpDelayCannotExtendFreshness() throws Exception {
  MultiWatchActivityTest.MultiFake f=live();JSONObject source=sample(30);
  source.getJSONArray("watches").getJSONObject(0).put("age",-0.5);
  f.snapshot=source.toString();tick();assertFalse(dump().getBoolean("valid"));
  source.getJSONArray("watches").getJSONObject(0).put("age",2.9);
  f.snapshot=source.toString();tick();JSONObject current=dump();assertTrue(current.getBoolean("valid"));
  QaSnapshot qa=(QaSnapshot)get("qa");JSONObject delayed=qa.export(current.getLong("capturedMonotonicNs")+500_000_000L);
  assertFalse(delayed.getJSONArray("watches").getJSONObject(0).getBoolean("freshAtExport"));
  assertFalse(qa.export(current.getLong("capturedMonotonicNs")-1).getBoolean("valid"));
 }
 @Test public void disconnectCannotBeOverwrittenByPollAlreadyInsidePublicationLocks() throws Exception {
  live();PlotController plots=(PlotController)get("plots");QaSnapshot cache=(QaSnapshot)get("qa");
  CountDownLatch held=new CountDownLatch(1),releasePlot=new CountDownLatch(1),releaseTeardown=new CountDownLatch(1);
  Thread holder=new Thread(()->{synchronized(plots){held.countDown();try{releasePlot.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}});
  holder.start();assertTrue(held.await(2,TimeUnit.SECONDS));
  java.util.concurrent.atomic.AtomicReference<Thread> tickThread=new java.util.concurrent.atomic.AtomicReference<>();
  Future<?> old=((ScheduledExecutorService)get("worker")).submit(()->{tickThread.set(Thread.currentThread());try{call("tick",new Class<?>[]{int.class},1);}catch(Exception ex){throw new RuntimeException(ex);}});
  long deadline=System.nanoTime()+2_000_000_000L;
  while((tickThread.get()==null||tickThread.get().getState()!=Thread.State.BLOCKED)&&System.nanoTime()<deadline)Thread.yield();
  assertNotNull(tickThread.get());assertEquals(Thread.State.BLOCKED,tickThread.get().getState());
  Future<?> teardownGate=((ScheduledExecutorService)get("worker")).submit(()->{try{releaseTeardown.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}});
  java.lang.reflect.Field cached=QaSnapshot.class.getDeclaredField("cached");cached.setAccessible(true);
  Thread observer=new Thread(()->{try{long end=System.nanoTime()+2_000_000_000L;while(cached.get(cache)!=null&&System.nanoTime()<end)Thread.yield();}catch(Exception ex){throw new RuntimeException(ex);}finally{releasePlot.countDown();}});
  observer.start();
  try{
   call("disconnect",new Class<?>[]{});old.get(3,TimeUnit.SECONDS);
   assertNull("Disconnect must clear the cache even before queued teardown or expiry",cached.get(cache));
  }finally{releasePlot.countDown();releaseTeardown.countDown();holder.join(3000);observer.join(3000);teardownGate.get(3,TimeUnit.SECONDS);drainQa();}
 }
 @Test public void preparedStatisticsCannotMasqueradeAsAnAlreadyDrawnFrame() throws Exception {
  MultiWatchActivityTest.MultiFake f=live();
  PlotView view=a.findViewById(R.id.plot_panel);
  ((android.widget.CheckBox)view.findViewWithTag("plot-field:Watch 1 · /numeric\ndata")).performClick();
  f.snapshot=sample(30).toString();set("lastUi",0L);tick();paint();
  android.view.View chart=a.findViewById(R.id.plot_chart);chart.layout(0,0,600,300);
  android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(600,300,android.graphics.Bitmap.Config.ARGB_8888);
  chart.draw(new android.graphics.Canvas(bitmap));
  JSONObject first=dump().getJSONObject("plotDisplay");assertFalse(first.getBoolean("drawPending"));
  assertEquals(1,first.getJSONArray("drawnSeries").getJSONObject(0).getInt("pointCount"));
  f.snapshot=sample(40).toString();set("lastUi",0L);tick();paint();
  JSONObject pending=dump().getJSONObject("plotDisplay");assertTrue(pending.getBoolean("drawPending"));
  assertEquals(first.getLong("lastDrawMonotonicNs"),pending.getLong("lastDrawMonotonicNs"));
  assertEquals(2,pending.getJSONArray("series").getJSONObject(0).getInt("pointCount"));
  assertEquals(1,pending.getJSONArray("drawnSeries").getJSONObject(0).getInt("pointCount"));
  chart.draw(new android.graphics.Canvas(bitmap));JSONObject drawn=dump().getJSONObject("plotDisplay");
  assertFalse(drawn.getBoolean("drawPending"));assertEquals(drawn.getLong("preparedRevision"),drawn.getLong("drawnRevision"));
  assertEquals(2,drawn.getJSONArray("drawnSeries").getJSONObject(0).getInt("pointCount"));
 }
}
