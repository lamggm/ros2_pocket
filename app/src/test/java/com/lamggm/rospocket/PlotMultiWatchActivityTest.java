package com.lamggm.rospocket;
import android.widget.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Real Android controls; the JNI double is host-only, not DDS evidence. */
public class PlotMultiWatchActivityTest extends MultiWatchActivityTest {
 private PlotController plots()throws Exception{return (PlotController)get("plots");}
 private PlotView view(){return a.getWindow().getDecorView().findViewWithTag("live-plot");}
 private JSONObject topic()throws Exception {return new JSONObject().put("wireName","rt/same").put("wireType","nav_msgs::msg::dds_::Odometry_").put("supported",true);}
 private MultiFake livePlots()throws Exception {
  MultiFake f=extras();
  for(int slot=0;slot<4;slot++){
   a.getWindow().getDecorView().findViewWithTag("watch-select:"+slot).performClick();
   call("select",new Class<?>[]{JSONObject.class},topic());drain();
  }
  f.snapshot=PlotMultiWatchTest.snapshot(1,100).put("subscription","/same").put("readerGeneration",10).toString();
  set("lastUi",0L);tick();paint();
  for(int slot=0;slot<4;slot++){
   CheckBox choice=view().findViewWithTag("plot-field:"+PlotMultiWatchTest.watch(slot)+"\nfield[86]");
   assertNotNull("Usable typed selection for slot "+slot,choice);choice.performClick();
  }
  f.snapshot=PlotMultiWatchTest.snapshot(2,200).toString();set("lastUi",0L);tick();paint();
  assertEquals(4,plots().history.snapshot().size());return f;
 }
 private void primary(){a.getWindow().getDecorView().findViewWithTag("watch-select:0").performClick();}
 @Test public void primaryQosRequestRetiresOnlyItsTraceImmediatelyIncludingPausedDisplay()throws Exception {
  livePlots();view().findViewWithTag("plot-pause").performClick();primary();
  ((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);
  assertEquals("Only primary series is retired on request",3,plots().history.snapshot().size());
  assertEquals(3,view().displayed().size());
  for(PlotHistory.Series s:view().displayed())assertEquals(1,s.points.size());
 }
 @Test public void extraQosDuringBlockedPollRetiresImmediatelyAndCannotResurrectAfterApply()throws Exception {
  MultiFake f=livePlots();view().findViewWithTag("plot-pause").performClick();
  a.getWindow().getDecorView().findViewWithTag("watch-select:1").performClick();
  f.snapshot=PlotMultiWatchTest.snapshot(3,300).toString();
  f.pollEntered=new java.util.concurrent.CountDownLatch(1);f.pollRelease=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.Future<?> poll=((java.util.concurrent.ScheduledExecutorService)get("worker")).submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
  assertTrue(f.pollEntered.await(5,java.util.concurrent.TimeUnit.SECONDS));
  try{
   ((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);
   assertEquals("Requested slot must retire before DDS applies it",3,plots().history.snapshot().size());
   assertEquals(3,view().displayed().size());
  }finally{f.pollRelease.countDown();}
  poll.get(5,java.util.concurrent.TimeUnit.SECONDS);drain();paint();
  assertFalse("Old in-flight frame cannot resurrect retired fields",plots().fields().stream().anyMatch(field->field.watch.equals(PlotMultiWatchTest.watch(1))));
  for(int slot:new int[]{0,2,3})assertEquals(2,plots().history.points(PlotMultiWatchTest.watch(slot)+"\nfield[86]").size());
  for(PlotHistory.Series series:view().displayed())assertEquals(1,series.points.size());
 }
 @Test public void appliedSameTopicReplacementCannotAdmitOldNativeGeneration()throws Exception {
  MultiFake f=livePlots();a.getWindow().getDecorView().findViewWithTag("watch-select:1").performClick();
  ((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);drain();
  f.snapshot=PlotMultiWatchTest.snapshot(3,300).toString();set("lastUi",0L);tick();paint();
  assertFalse("Applied request is not proof an obsolete native reader is current",plots().fields().stream().anyMatch(field->field.watch.equals(PlotMultiWatchTest.watch(1))));
  JSONObject newer=PlotMultiWatchTest.snapshot(4,400);newer.getJSONArray("watches").getJSONObject(1).put("generation",12);
  f.snapshot=newer.toString();set("lastUi",0L);tick();paint();
  assertTrue(plots().fields().stream().anyMatch(field->field.watch.equals(PlotMultiWatchTest.watch(1))));
 }
 @Test public void primaryRemovalRetiresImmediatelyAndQueuedSnapshotsCannotRestoreItsSeries()throws Exception {
  MultiFake f=livePlots();view().findViewWithTag("plot-pause").performClick();primary();
  a.getWindow().getDecorView().findViewWithTag("watch-remove").performClick();
  assertEquals("Primary remove must synchronously retire its plotted fields",3,plots().history.snapshot().size());
  assertEquals(3,view().displayed().size());drain();
  f.snapshot=PlotMultiWatchTest.snapshot(3,300).toString();set("lastUi",0L);tick();paint();
  assertFalse(plots().fields().stream().anyMatch(field->field.watch.equals(PlotMultiWatchTest.watch(0))));
  for(int slot=1;slot<4;slot++)assertEquals(2,plots().history.points(PlotMultiWatchTest.watch(slot)+"\nfield[86]").size());
 }
 @Test public void nativeTopicOrTypeMismatchIsIsolatedAndHealthySlotsKeepAdvancing()throws Exception {
  MultiFake f=livePlots();JSONObject wrong=PlotMultiWatchTest.snapshot(3,300);
  wrong.getJSONArray("watches").getJSONObject(1).put("subscription","/wrong-topic");
  wrong.getJSONArray("watches").getJSONObject(2).put("type","geometry_msgs/msg/Vector3");
  f.snapshot=wrong.toString();set("lastUi",0L);tick();paint();
  assertEquals("Only the matching desired/applied slots expose fields",174,plots().fields().size());
  assertEquals(2,plots().history.snapshot().size());
  for(int slot:new int[]{0,3})assertEquals(2,plots().history.points(PlotMultiWatchTest.watch(slot)+"\nfield[86]").size());
 }
 @Test public void subscribeCompletionCannotMarkANewerPrimaryRequestApplied()throws Exception {
  livePlots();primary();
  java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.CountDownLatch gateEntered=new java.util.concurrent.CountDownLatch(1),gateRelease=new java.util.concurrent.CountDownLatch(1);
  MultiFake blocking=new MultiFake(){@Override public byte[] subscribe(String w,String t,boolean r,boolean l){
   entered.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
   return bytes("{\"ok\":true}");
  }};
  set("bridge",blocking);bridge=blocking;
  ((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);
  assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
  java.util.concurrent.Future<?> gate=((java.util.concurrent.ScheduledExecutorService)get("worker")).submit(()->{gateEntered.countDown();try{gateRelease.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
  try{
   ((CheckBox)a.findViewById(R.id.qos_transient)).setChecked(true);release.countDown();
   assertTrue(gateEntered.await(5,java.util.concurrent.TimeUnit.SECONDS));
   assertTrue("Completion belongs to the request passed to native, not the latest desired request",((WatchSlots)get("watchSlots")).frame(0).pending);
  }finally{release.countDown();gateRelease.countDown();}
  gate.get(5,java.util.concurrent.TimeUnit.SECONDS);drain();
 }
 @Test public void structuredPlotCheckboxesHaveExplicitInstructionsAndNeverCreateReaders()throws Exception {
  MultiFake f=livePlots();
  for(PlotHistory.Series s:plots().history.snapshot())view().findViewWithTag("plot-remove:"+s.id).performClick();
  JSONObject snapshot=PlotMultiWatchTest.snapshot(3,300),row=snapshot.getJSONArray("watches").getJSONObject(1);
  String path="twist.covariance[35]";
  row.put("numericFields",new JSONArray().put(new JSONObject().put("path",path).put("value",7.25).put("state","finite")));
  row.put("message",new JSONObject().put("schemaVersion",1).put("type","nav_msgs/msg/Odometry").put("numericPaths",new JSONArray().put(path))
   .put("root",new JSONObject().put("path",path).put("kind","number").put("value",7.25).put("display","NOT A NUMBER TO PARSE")));
  f.snapshot=snapshot.toString();set("lastUi",0L);tick();paint();
  assertTrue("Tell users exactly where plot selection happens",((TextView)view().getChildAt(1)).getText().toString().contains("caixas"));
  int calls=f.calls.size(),subscriptions=f.subscriptions;
  CheckBox check=view().findViewWithTag("plot-field:"+PlotMultiWatchTest.watch(1)+"\n"+path);assertNotNull(check);check.performClick();
  assertEquals(1,plots().history.snapshot().size());
  row.put("count",4).put("receivedMonotonicNs",400);f.snapshot=snapshot.toString();tick();
  assertEquals(7.25,plots().history.snapshot().get(0).points.get(0).value,0);
  assertEquals(calls,f.calls.size());assertEquals(subscriptions,f.subscriptions);
 }
 @Test public void obsoleteNativeIdentityCannotRetireANewerHealthyTrace()throws Exception {
  MultiFake f=livePlots();a.getWindow().getDecorView().findViewWithTag("watch-select:1").performClick();
  ((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);drain();
  JSONObject s=PlotMultiWatchTest.snapshot(3,300);s.getJSONArray("watches").getJSONObject(1).put("generation",12);
  f.snapshot=s.toString();tick();assertTrue(plots().select(PlotMultiWatchTest.watch(1),"field[86]"));
  s.getJSONArray("watches").getJSONObject(1).put("generation",11).put("subscription","/obsolete");
  f.snapshot=s.toString();tick();
  assertTrue("Reject obsolete reader before it can clear the current identity",plots().selected(PlotMultiWatchTest.watch(1)+"\nfield[86]"));
  assertEquals(348,plots().fields().size());
 }
 @Test public void primaryAndExtraSubscribeErrorsDoNotResetHealthyPausedIntake()throws Exception {
  MultiFake f=livePlots();view().findViewWithTag("plot-pause").performClick();primary();f.failSubscribe=true;
  ((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);drain();
  f.snapshot=PlotMultiWatchTest.snapshot(3,300).toString();set("lastUi",0L);tick();paint();
  assertEquals(3,plots().history.snapshot().size());assertTrue(text(R.id.status).contains("subscribe failed"));
  a.getWindow().getDecorView().findViewWithTag("watch-select:2").performClick();f.failExtra=true;
  ((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);drain();
  f.snapshot=PlotMultiWatchTest.snapshot(4,400).toString();set("lastUi",0L);tick();paint();
  assertEquals(2,plots().history.snapshot().size());assertEquals(2,view().displayed().size());
  for(int slot:new int[]{1,3})assertEquals(3,plots().history.points(PlotMultiWatchTest.watch(slot)+"\nfield[86]").size());
  for(PlotHistory.Series s:view().displayed())assertEquals(1,s.points.size());
  assertTrue(extraText(2).contains("extra reader failed"));
 }
 @Test public void requestBeforePolicyCaptureCannotResurrectFieldsOrAdvancePausedSurvivors()throws Exception {requestBeforePolicy(1);}
 @Test public void primaryRequestBeforePolicyCapturePreservesHealthyPlots()throws Exception {requestBeforePolicy(0);}
 private void requestBeforePolicy(int slot)throws Exception {
  MultiFake f=livePlots();view().findViewWithTag("plot-pause").performClick();
  f.snapshot=PlotMultiWatchTest.snapshot(3,300).toString();set("lastUi",0L);
  java.util.concurrent.ScheduledExecutorService executor=(java.util.concurrent.ScheduledExecutorService)get("worker");
  Thread thread=executor.submit(()->Thread.currentThread()).get(5,java.util.concurrent.TimeUnit.SECONDS);java.util.concurrent.Future<?> pending;
  synchronized(get("policy")){
   pending=executor.submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);boolean blocked=false;
   while(System.nanoTime()<deadline){for(StackTraceElement e:thread.getStackTrace())if(e.getClassName().endsWith("ConnectionPolicy")&&e.getMethodName().equals("identity")&&thread.getState()==Thread.State.BLOCKED)blocked=true;if(blocked)break;Thread.sleep(2);}
   assertTrue("Worker waits at identity before frame capture",blocked);
   a.getWindow().getDecorView().findViewWithTag("watch-select:"+slot).performClick();
   ((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);
   assertEquals(3,view().displayed().size());
  }
  pending.get(5,java.util.concurrent.TimeUnit.SECONDS);drain();paint();
  assertFalse(plots().fields().stream().anyMatch(field->field.watch.equals(PlotMultiWatchTest.watch(slot))));
  for(int other=0;other<4;other++)if(other!=slot)assertEquals(2,plots().history.points(PlotMultiWatchTest.watch(other)+"\nfield[86]").size());
  for(PlotHistory.Series s:view().displayed())assertEquals(1,s.points.size());
 }
 @Test public void stopDuringBlockedPollClearsEveryPausedTraceAndRejectsLateIntake()throws Exception {
  MultiFake f=livePlots();view().findViewWithTag("plot-pause").performClick();
  f.pollEntered=new java.util.concurrent.CountDownLatch(1);f.pollRelease=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.Future<?> pending=((java.util.concurrent.ScheduledExecutorService)get("worker")).submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
  assertTrue(f.pollEntered.await(5,java.util.concurrent.TimeUnit.SECONDS));
  try{call("onStop",new Class<?>[]{});}finally{f.pollRelease.countDown();}
  pending.get(5,java.util.concurrent.TimeUnit.SECONDS);drain();paint();
  assertTrue(plots().fields().isEmpty());
  for(PlotHistory.Series s:plots().history.snapshot())assertTrue(s.points.isEmpty());
  for(PlotHistory.Series s:view().displayed())assertTrue(s.points.isEmpty());
  call("disconnect",new Class<?>[]{});paint();assertTrue(plots().fields().isEmpty());
 }
 @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
 public void fourNativeSlotProjectionsRenderThroughRealHostCanvas()throws Exception {
  livePlots();android.view.View chart=view().getChildAt(view().getChildCount()-1);chart.layout(0,0,800,300);
  android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(800,300,android.graphics.Bitmap.Config.ARGB_8888);chart.draw(new android.graphics.Canvas(image));
  for(int color:PlotView.COLORS){int pixels=0;for(int y=40;y<265;y++)for(int x=0;x<800;x++)if(image.getPixel(x,y)==color)pixels++;assertTrue("Integrated slot trace reaches actual Canvas",pixels>0);}
 }
}
