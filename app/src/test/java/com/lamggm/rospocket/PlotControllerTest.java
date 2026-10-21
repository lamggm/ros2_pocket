package com.lamggm.rospocket;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class) @org.robolectric.annotation.Config(sdk=31)
public class PlotControllerTest {
 static JSONObject sample(long count,long time)throws Exception {
  return new JSONObject().put("running",true).put("readerActive",true).put("fresh",true).put("subscriptionState","receiving")
   .put("subscription","/value").put("error","").put("count",count).put("sessionGeneration",1).put("readerGeneration",2)
   .put("receivedMonotonicNs",time).put("sourceTimestampNs",1000).put("numericFields",new JSONArray().put(new JSONObject().put("path","data").put("value",42.5).put("state","finite").put("unit",JSONObject.NULL)));
 }
 @Test public void typedPrimitiveAdapterDeduplicatesAndNeverParsesDisplayText()throws Exception {
  PlotController c=new PlotController();JSONObject s=sample(1,100);
  c.acceptNative(s,1,false);assertEquals(1,c.fields().size());assertTrue(c.select("/value","data"));
  c.acceptNative(sample(2,200),1,false);c.acceptNative(sample(2,200),1,false);
  assertEquals(1,c.history.snapshot().get(0).points.size());assertEquals(42.5,c.history.snapshot().get(0).points.get(0).value,0);
  s=sample(3,300);s.getJSONArray("numericFields").getJSONObject(0).put("value","99");s.put("last","1234");
  c.acceptNative(s,1,false);assertNull(c.history.snapshot().get(0).points.get(1).value);
 }
 @Test public void inactivePendingErrorsAndOldEpochsCannotInventLivePoints()throws Exception {
  PlotController c=new PlotController();c.acceptNative(sample(1,100),2,false);c.select("/value","data");
  c.acceptNative(sample(2,200),2,false);assertEquals(1,c.history.points("/value\ndata").size());
  c.acceptNative(sample(3,300).put("fresh",false),2,false);assertTrue(c.fields().isEmpty());assertTrue(c.history.points("/value\ndata").isEmpty());
  c.acceptNative(sample(4,400),1,false);assertTrue(c.fields().isEmpty());
  c.acceptNative(sample(5,500),2,true);assertTrue(c.fields().isEmpty());
  c.acceptNative(sample(6,600).put("error","reader failure"),2,false);assertTrue(c.fields().isEmpty());
  c.acceptNative(sample(7,700).put("readerActive",false),2,false);assertTrue(c.fields().isEmpty());
  c.acceptNative(sample(8,800),3,false);assertEquals(1,c.fields().size());
  c.acceptNative(sample(1,900).put("readerGeneration",3),3,false);assertEquals(1,c.history.points("/value\ndata").size());assertEquals(900,c.history.points("/value\ndata").get(0).time);
 }
 @Test public void retiredNativeReaderSnapshotCannotReplaceNewReader()throws Exception {
  PlotController c=new PlotController();c.acceptNative(sample(1,100).put("readerGeneration",3),2,false);c.select("/value","data");
  c.acceptNative(sample(2,200).put("readerGeneration",3),2,false);
  c.acceptNative(sample(99,300).put("readerGeneration",2),2,false);
  assertEquals(1,c.history.points("/value\ndata").size());assertEquals(200,c.history.points("/value\ndata").get(0).time);
 }
 @Test public void absentFieldCreatesGapRatherThanBridgingMissingSamples() {
  PlotController c=new PlotController();java.util.List<PlotController.Field> f=java.util.Collections.singletonList(new PlotController.Field("w","x",null,5.0));
  c.accept(new PlotController.Frame("w",1,1,100,null,f));c.select("w","x");c.accept(new PlotController.Frame("w",1,2,200,null,f));
  c.accept(new PlotController.Frame("w",1,3,300,null,java.util.Collections.emptyList()));
  c.accept(new PlotController.Frame("w",1,4,400,null,f));assertEquals(3,c.history.points("w\nx").size());assertNull(c.history.points("w\nx").get(1).value);
 }
 @Test public void retiringWatchFreesItsBudgetWithoutChangingOtherHistories(){
  PlotController c=new PlotController();
  for(int i=0;i<4;i++){String w="w"+i;java.util.List<PlotController.Field> f=java.util.Collections.singletonList(new PlotController.Field(w,"x",null,5.0));c.accept(new PlotController.Frame(w,1,1,100,null,f));c.select(w,"x");c.accept(new PlotController.Frame(w,1,2,200,null,f));}
  c.retire("w0");assertEquals(3,c.history.snapshot().size());assertEquals(1,c.history.points("w1\nx").size());
  java.util.List<PlotController.Field> f=java.util.Collections.singletonList(new PlotController.Field("new","x",null,5.0));
  c.accept(new PlotController.Frame("new",1,1,300,null,f));assertTrue(c.select("new","x"));
 }
 @Test public void contractAllowsIndependentOverlaidWatchesAndResetFencesOldFrames(){
  PlotController c=new PlotController();
  for(int i=0;i<4;i++){
   String w="w"+i;java.util.List<PlotController.Field> fs=java.util.Collections.singletonList(new PlotController.Field(w,"x",null,1.0));
   c.accept(new PlotController.Frame(w,2,1,100,null,fs));assertTrue(c.select(w,"x"));
   c.accept(new PlotController.Frame(w,2,2,200,null,fs));
  }
  assertEquals(4,c.history.snapshot().size());for(PlotHistory.Series s:c.history.snapshot())assertEquals(1,s.points.size());
  c.reset(3);c.accept(new PlotController.Frame("w0",2,99,300,null,java.util.Collections.emptyList()));
  assertTrue(c.fields().isEmpty());for(PlotHistory.Series s:c.history.snapshot())assertTrue(s.points.isEmpty());
 }
}
