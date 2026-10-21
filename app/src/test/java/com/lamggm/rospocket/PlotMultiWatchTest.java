package com.lamggm.rospocket;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class) @org.robolectric.annotation.Config(sdk=31)
public class PlotMultiWatchTest {
 static String watch(int slot){return "Watch "+(slot+1)+" · /same";}
 static JSONObject snapshot(long count,long time)throws Exception {
  JSONObject root=PlotControllerTest.sample(count,time).put("sessionGeneration",1);
  JSONArray rows=new JSONArray();
  for(int slot=0;slot<4;slot++){
   JSONArray fields=new JSONArray();
   for(int n=0;n<87;n++)fields.put(new JSONObject().put("path","field["+n+"]").put("value",slot*100+n+.25).put("state","finite"));
   rows.put(new JSONObject().put("slot",slot).put("generation",10+slot).put("subscription","/same")
    .put("type","nav_msgs::msg::dds_::Odometry_").put("readerActive",true).put("fresh",true).put("subscriptionState","receiving")
    .put("count",count).put("receivedMonotonicNs",time+slot).put("sourceTimestampNs",1000+slot).put("numericFields",fields).put("error",""));
  }
  return root.put("watches",rows);
 }
 @Test public void allFourSameTopicSlotsExposeEveryOdometryLeafAndKeepIndependentSamples()throws Exception {
  PlotController c=new PlotController();c.acceptNative(snapshot(1,100),1,false);
  assertEquals("87 actual typed leaves in each slot, not the root projection",348,c.fields().size());
  for(int slot=0;slot<4;slot++)assertTrue(c.select(watch(slot),"field[86]"));
  c.acceptNative(snapshot(2,200),1,false);c.acceptNative(snapshot(2,200),1,false);
  for(int slot=0;slot<4;slot++){
   java.util.List<PlotHistory.Point> points=c.history.points(watch(slot)+"\nfield[86]");
   assertEquals(1,points.size());assertEquals(200+slot,points.get(0).time);assertEquals(slot*100+86.25,points.get(0).value,0);
  }
 }
 @Test public void primaryErrorAndPolicyChangeRetireOnlyItsPausedTrace()throws Exception {
  PlotController c=new PlotController();c.acceptNative(snapshot(1,100),1,false);
  for(int slot=0;slot<4;slot++)c.select(watch(slot),"field[86]");
  c.acceptNative(snapshot(2,200),1,false);
  PlotView view=new PlotView(org.robolectric.RuntimeEnvironment.getApplication(),c);view.refresh();
  view.findViewWithTag("plot-pause").performClick();
  c.acceptNative(snapshot(3,300),1,false);
  JSONObject bad=snapshot(4,400);bad.getJSONArray("watches").getJSONObject(0).put("error","primary failed");
  // UI policy generations may advance on primary changes; this is NOT a session reset.
  c.acceptNative(bad,2,false);view.refresh();
  assertEquals(3,c.history.snapshot().size());assertEquals(3,view.displayed().size());
  for(PlotHistory.Series s:view.displayed())assertEquals("Healthy paused copy must not advance",1,s.points.size());
  for(int slot=1;slot<4;slot++)assertEquals(3,c.history.points(watch(slot)+"\nfield[86]").size());
 }
 @Test public void obsoleteSessionsAndReadersCannotReplaceOrRetireHealthyHistories()throws Exception {
  PlotController c=new PlotController();c.acceptNative(snapshot(1,100),1,false);
  for(int slot=0;slot<4;slot++)c.select(watch(slot),"field[86]");
  c.acceptNative(snapshot(2,200),1,false);
  JSONObject old=snapshot(999,999).put("sessionGeneration",0);c.acceptNative(old,2,false);
  for(int slot=0;slot<4;slot++)assertEquals(1,c.history.points(watch(slot)+"\nfield[86]").size());
  JSONObject newSession=snapshot(1,1000).put("sessionGeneration",2);c.acceptNative(newSession,2,false);
  c.acceptNative(snapshot(999,2000).put("sessionGeneration",1),3,false);
  for(int slot=0;slot<4;slot++){
   java.util.List<PlotHistory.Point> points=c.history.points(watch(slot)+"\nfield[86]");
   assertEquals(1,points.size());assertEquals(1000+slot,points.get(0).time);
  }
  JSONObject oldReader=snapshot(999,3000).put("sessionGeneration",2);
  oldReader.getJSONArray("watches").getJSONObject(1).put("generation",10).put("error","obsolete failure");
  c.acceptNative(oldReader,2,false);assertTrue(c.selected(watch(1)+"\nfield[86]"));assertEquals(1001,c.history.points(watch(1)+"\nfield[86]").get(0).time);
 }
 @Test public void integerJsonAndMonotonicSourceClocksStaySeparateWhileNonfiniteIsAGap()throws Exception {
  PlotController c=new PlotController();JSONObject s=snapshot(1,100);
  JSONObject number=s.getJSONArray("watches").getJSONObject(0).getJSONArray("numericFields").getJSONObject(86);
  number.put("value",Long.MIN_VALUE);s=new JSONObject(s.toString());
  Object exact=s.getJSONArray("watches").getJSONObject(0).getJSONArray("numericFields").getJSONObject(86).get("value");
  assertTrue(exact instanceof Long);assertEquals(Long.MIN_VALUE,((Number)exact).longValue());
  c.acceptNative(s,1,false);c.select(watch(0),"field[86]");
  s.getJSONArray("watches").getJSONObject(0).put("count",20).put("receivedMonotonicNs",200).put("sourceTimestampNs",10);
  c.acceptNative(s,1,false);
  assertEquals("A count jump creates one latest-sample point only",1,c.history.points(watch(0)+"\nfield[86]").size());
  assertEquals(200,c.history.points(watch(0)+"\nfield[86]").get(0).time);
  s.getJSONArray("watches").getJSONObject(0).put("count",21).put("receivedMonotonicNs",300).put("sourceTimestampNs",5);
  s.getJSONArray("watches").getJSONObject(0).getJSONArray("numericFields").getJSONObject(86).put("value",JSONObject.NULL).put("state","positive_infinity");
  c.acceptNative(s,1,false);assertNull(c.history.points(watch(0)+"\nfield[86]").get(1).value);assertTrue(c.history.points(watch(0)+"\nfield[86]").get(1).breakBefore);
  assertEquals("Adapter never rewrites the exact JSON integer",Long.MIN_VALUE,((Number)exact).longValue());
 }
}
