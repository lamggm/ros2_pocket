package com.lamggm.rospocket;

import static org.junit.Assert.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class NumericBatchControllerTest {
 static JSONObject snapshot(long count,JSONArray samples)throws Exception{
  JSONArray watches=new JSONArray(),slots=new JSONArray();
  for(int slot=0;slot<4;slot++){
   boolean active=slot==0;
   watches.put(new JSONObject().put("slot",slot).put("generation",5).put("subscription",active?"/batch":"")
    .put("type",active?"std_msgs::msg::dds_::Float64_":"").put("readerActive",active).put("fresh",active)
    .put("subscriptionState",active?"receiving":"inactive").put("error","").put("count",active?count:0)
    .put("receivedMonotonicNs",count*1000).put("sourceTimestampNs",count*10)
    .put("numericFields",active?new JSONArray().put(new JSONObject().put("path","data").put("unit",JSONObject.NULL).put("state","finite").put("value",(double)count)):new JSONArray()));
   slots.put(new JSONObject().put("slot",slot).put("readerGeneration",5).put("paths",active?new JSONArray().put("data"):new JSONArray())
    .put("samples",active?samples:new JSONArray()).put("droppedLocal",0));
  }
  return new JSONObject().put("running",true).put("sessionGeneration",1).put("watches",watches)
   .put("numericBatch",new JSONObject().put("schema",1).put("epoch",1).put("slots",slots));
 }
 @Test public void selectedHistoryGetsEveryBatchSampleIncludingGapAndOriginalClocks()throws Exception{
  PlotController controller=new PlotController();controller.acceptNative(snapshot(1,new JSONArray()),1,false);
  String watch="Watch 1 · /batch";assertTrue(controller.select(watch,"data"));
  JSONArray samples=new JSONArray().put(new JSONArray().put(2).put(2000).put(20).put(2.0))
   .put(new JSONArray().put(3).put(3000).put(30).put(JSONObject.NULL))
   .put(new JSONArray().put(4).put(4000).put(40).put(4.0));
  JSONObject batch=snapshot(4,samples);controller.acceptNative(batch,1,false);
  java.util.List<PlotHistory.Point> points=controller.history.points(watch+"\ndata");assertEquals("Not just the latest sample",3,points.size());
  assertEquals(2000,points.get(0).time);assertEquals(Long.valueOf(20),points.get(0).source);assertEquals(Double.valueOf(2),points.get(0).value);
  assertEquals(3000,points.get(1).time);assertNull(points.get(1).value);assertEquals(Long.valueOf(30),points.get(1).source);
  assertEquals(4000,points.get(2).time);assertEquals(Long.valueOf(40),points.get(2).source);assertEquals(Double.valueOf(4),points.get(2).value);
  controller.acceptNative(batch,1,false);assertEquals("Replayed poll must not duplicate",3,controller.history.points(watch+"\ndata").size());
 }
 @Test public void distinctNativeSequencesPreserveCoincidentReceiveTimeAndNonfiniteGap()throws Exception{
  PlotController controller=new PlotController();controller.acceptNative(snapshot(1,new JSONArray()),1,false);String watch="Watch 1 · /batch";assertTrue(controller.select(watch,"data"));
  JSONObject batch=snapshot(4,new JSONArray().put(new JSONArray().put(2).put(2000).put(20).put(2.0)).put(new JSONArray().put(3).put(2000).put(30).put(JSONObject.NULL)).put(new JSONArray().put(4).put(4000).put(40).put(4.0)));
  controller.acceptNative(batch,1,false);java.util.List<PlotHistory.Point> points=controller.history.points(watch+"\ndata");assertEquals("Timestamp equality cannot discard a distinct native sequence",3,points.size());
  assertEquals(2000,points.get(0).time);assertEquals(2000,points.get(1).time);assertNull(points.get(1).value);assertEquals(Long.valueOf(30),points.get(1).source);
  String csv=new String(PlotCsvExporter.encode(controller.history.snapshot(),false),java.nio.charset.StandardCharsets.UTF_8);assertTrue(csv.contains(",0,2000,30,,gap,false,0"));
  controller.acceptNative(batch,1,false);assertEquals("Deduplicate sequences, not clocks",3,controller.history.points(watch+"\ndata").size());
 }
 @Test public void mismatchedSelectedPathsCannotInsertGapIntoCurrentSeries()throws Exception{
  PlotController controller=new PlotController();controller.acceptNative(snapshot(1,new JSONArray()),1,false);String watch="Watch 1 · /batch";assertTrue(controller.select(watch,"data"));
  JSONObject foreign=snapshot(2,new JSONArray().put(new JSONArray().put(2).put(2000).put(20).put(7.0)));
  foreign.getJSONObject("numericBatch").getJSONArray("slots").getJSONObject(0).put("paths",new JSONArray().put("unselected"));
  controller.acceptNative(foreign,1,false);assertEquals("A same-epoch wrong field is not a real data gap",0,controller.history.points(watch+"\ndata").size());
 }
 private Object request(PlotController controller)throws Exception{
  try{java.lang.reflect.Method method=PlotController.class.getDeclaredMethod("numericSelection");method.setAccessible(true);return method.invoke(controller);}
  catch(NoSuchMethodException missing){return null;}
 }
 private long epoch(Object request)throws Exception{java.lang.reflect.Field field=request.getClass().getDeclaredField("epoch");field.setAccessible(true);return field.getLong(request);}
 private java.util.List<?> fields(Object request)throws Exception{java.lang.reflect.Field field=request.getClass().getDeclaredField("fields");field.setAccessible(true);return (java.util.List<?>)field.get(request);}
 @Test public void immutableNativeSelectionRequestTracksOnlyExplicitSelections()throws Exception{
  PlotController controller=new PlotController();controller.acceptNative(snapshot(1,new JSONArray()),1,false);
  Object empty=request(controller);assertNotNull("A bounded selection request must exist",empty);assertEquals(0,epoch(empty));assertEquals(0,fields(empty).size());
  String watch="Watch 1 · /batch";assertTrue(controller.select(watch,"data"));Object selected=request(controller);assertEquals(1,epoch(selected));assertEquals(1,fields(selected).size());
  BenchProfiles.Series choice=(BenchProfiles.Series)fields(selected).get(0);assertEquals(0,choice.slot);assertEquals("data",choice.path);
  try{fields(selected).clear();fail("Request must be immutable");}catch(UnsupportedOperationException expected){}
  assertTrue(controller.select(watch,"data"));assertEquals("Idempotent select cannot churn native generation",1,epoch(request(controller)));
  controller.removeSelection(watch+"\ndata");Object removed=request(controller);assertEquals(2,epoch(removed));assertEquals(0,fields(removed).size());assertEquals(1,fields(selected).size());
 }
 @Test public void knownNativeDiscardBreaksTraceAndSurvivesFrozenCsvMetadata()throws Exception{
  PlotController controller=new PlotController();controller.acceptNative(snapshot(1,new JSONArray()),1,false);String watch="Watch 1 · /batch";controller.select(watch,"data");
  controller.acceptNative(snapshot(2,new JSONArray().put(new JSONArray().put(2).put(2000).put(20).put(2.0))),1,false);
  java.util.List<PlotHistory.Series> frozen=controller.history.snapshot();
  JSONObject overflow=snapshot(10,new JSONArray().put(new JSONArray().put(9).put(9000).put(90).put(9.0)).put(new JSONArray().put(10).put(10000).put(100).put(10.0)));
  overflow.getJSONObject("numericBatch").getJSONArray("slots").getJSONObject(0).put("droppedLocal",6);controller.acceptNative(overflow,1,false);
  java.util.List<PlotHistory.Point> points=controller.history.points(watch+"\ndata");assertEquals(3,points.size());assertTrue("Known local discard cannot be bridged as a continuous trace",points.get(1).breakBefore);
  String live=new String(PlotCsvExporter.encode(controller.history.snapshot(),false),java.nio.charset.StandardCharsets.UTF_8);
  assertTrue(live.contains("watch_local_batch_discarded_total\r\n"));assertTrue(live.contains("2,numeric_batch,live"));
  for(String line:live.split("\r\n"))if(line.startsWith("2,"))assertTrue(line.endsWith(",6"));
  String paused=new String(PlotCsvExporter.encode(frozen,true),java.nio.charset.StandardCharsets.UTF_8);assertTrue(paused.contains("2,numeric_batch,paused"));
  for(String line:paused.split("\r\n"))if(line.startsWith("2,"))assertTrue("Frozen metadata cannot borrow a later counter",line.endsWith(",0"));
  JSONObject empty=snapshot(11,new JSONArray());empty.getJSONObject("numericBatch").getJSONArray("slots").getJSONObject(0).put("droppedLocal",7);controller.acceptNative(empty,1,false);
  String afterEmpty=new String(PlotCsvExporter.encode(controller.history.snapshot(),false),java.nio.charset.StandardCharsets.UTF_8);
  for(String line:afterEmpty.split("\r\n"))if(line.startsWith("2,"))assertTrue("Discard metadata advances even without retained samples",line.endsWith(",7"));
 }
 @Test public void replayedBatchCannotRollBackKnownDiscardMetadata()throws Exception{
  PlotController controller=new PlotController();controller.acceptNative(snapshot(1,new JSONArray()),1,false);controller.select("Watch 1 · /batch","data");
  JSONObject current=snapshot(2,new JSONArray().put(new JSONArray().put(2).put(2000).put(20).put(2.0)));current.getJSONObject("numericBatch").getJSONArray("slots").getJSONObject(0).put("droppedLocal",6);controller.acceptNative(current,1,false);
  byte[] before=PlotCsvExporter.encode(controller.history.snapshot(),false);
  JSONObject replay=new JSONObject(current.toString());replay.getJSONObject("numericBatch").getJSONArray("slots").getJSONObject(0).put("droppedLocal",1);controller.acceptNative(replay,1,false);
  assertArrayEquals("Deduplication must preserve immutable reader counter progression",before,PlotCsvExporter.encode(controller.history.snapshot(),false));
 }
 private String text(android.view.View view){StringBuilder value=new StringBuilder();if(view instanceof android.widget.TextView)value.append(((android.widget.TextView)view).getText());if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)value.append(text(((android.view.ViewGroup)view).getChildAt(i)));return value.toString();}
 @Test public void knownLocalCounterIsVisibleAndFrozenWithThePausedCopy()throws Exception{
  PlotController controller=new PlotController();controller.acceptNative(snapshot(1,new JSONArray()),1,false);controller.select("Watch 1 · /batch","data");
  JSONObject current=snapshot(2,new JSONArray().put(new JSONArray().put(2).put(2000).put(20).put(2.0)));current.getJSONObject("numericBatch").getJSONArray("slots").getJSONObject(0).put("droppedLocal",6);controller.acceptNative(current,1,false);
  PlotView view=new PlotView(org.robolectric.RuntimeEnvironment.getApplication(),controller);view.refresh();assertTrue(text(view).contains("descartes locais do lote: 6"));
  view.findViewById(R.id.plot_pause).performClick();JSONObject later=snapshot(3,new JSONArray().put(new JSONArray().put(3).put(3000).put(30).put(3.0)));later.getJSONObject("numericBatch").getJSONArray("slots").getJSONObject(0).put("droppedLocal",9);controller.acceptNative(later,1,false);view.refresh();
  assertTrue(text(view).contains("descartes locais do lote: 6"));assertFalse(text(view).contains("descartes locais do lote: 9"));
  view.findViewById(R.id.plot_pause).performClick();assertTrue(text(view).contains("descartes locais do lote: 9"));
 }
}
