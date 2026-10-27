package com.lamggm.rospocket;

import android.graphics.*;
import android.view.View;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;

/** Real host model/Canvas; native rows in unit cases are explicitly synthetic. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlotQualificationTest {
 static JSONObject rows(long count,long received,long reader)throws Exception {
  JSONArray watches=new JSONArray();
  for(int slot=0;slot<4;slot++)watches.put(new JSONObject().put("slot",slot).put("generation",slot==0?reader:2)
   .put("subscription","/q"+slot).put("readerActive",slot<2).put("fresh",true).put("subscriptionState","receiving")
   .put("count",count).put("error","").put("receivedMonotonicNs",received).put("sourceTimestampNs",1000+count)
   .put("numericFields",new JSONArray().put(new JSONObject().put("path","data").put("state","finite").put("value",10.0+slot+count))));
  return new JSONObject().put("running",true).put("sessionGeneration",7).put("watches",watches);
 }
 static String watch(int slot){return "Watch "+(slot+1)+" · /q"+slot;}
 static void select(PlotController c,int slot){assertTrue(c.select(watch(slot),"data"));}
 static Bitmap draw(PlotView v){
  View chart=v.findViewById(R.id.plot_chart);chart.layout(0,0,800,300);
  Bitmap b=Bitmap.createBitmap(800,300,Bitmap.Config.ARGB_8888);chart.draw(new Canvas(b));return b;
 }
 @Test public void exact600BoundaryIsPerRingAndWindowBoundaryIsInclusive(){
  PlotHistory h=new PlotHistory(600,30_000_000_000L);
  for(int i=0;i<4;i++)assertTrue(h.select("s"+i,"data",null));
  for(int n=1;n<=599;n++)for(int i=0;i<4;i++)h.append("s"+i,n,40.0+n,null);
  for(PlotHistory.Series s:h.snapshot())assertEquals(599,s.points.size());
  for(int i=0;i<4;i++)h.append("s"+i,600,640.0,null);
  java.util.List<PlotHistory.Series> at600=h.snapshot();
  for(PlotHistory.Series s:at600){assertEquals(600,s.points.size());assertEquals(1,s.points.get(0).time);}
  for(int i=0;i<4;i++)h.append("s"+i,601,641.0,null);
  for(PlotHistory.Series s:h.snapshot()){assertEquals(600,s.points.size());assertEquals(2,s.points.get(0).time);assertEquals(601,s.points.get(599).time);}
  for(PlotHistory.Series s:at600)assertEquals(1,s.points.get(0).time);
  h.append("s0",30_000_000_002L,90.0,null);
  assertEquals("Exactly-window-old survives",2,h.points("s1").get(0).time);
  h.append("s0",30_000_000_003L,91.0,null);
  assertEquals("One nanosecond beyond window evicts in the OTHER rings too",3,h.points("s1").get(0).time);
 }
 @Test public void nativeCanvasDoesNotBridgeReceiveOrSourceClockBreaks()throws Exception {
  for(boolean sourceReset:new boolean[]{false,true}){
   PlotController c=new PlotController();c.history.select("s","data",null);
   c.history.append("s",1_000_000_000L,40.0,1000L);
   c.history.append("s",2_000_000_000L,41.0,2000L);
   c.history.append("s",sourceReset?3_000_000_000L:6_000_000_000L,42.0,sourceReset?50L:3000L);
   c.history.append("s",sourceReset?4_000_000_000L:7_000_000_000L,43.0,sourceReset?60L:4000L);
   PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);v.refresh();Bitmap image=draw(v);
   int colored=0;for(int y=42;y<263;y++)for(int x=20;x<780;x++)if(image.getPixel(x,y)==PlotView.COLORS[0])colored++;
   assertTrue("Real finite segments must be drawn",colored>10);
   for(int y=42;y<263;y++)assertNotEquals("No line across receive gap or backwards source stamp",PlotView.COLORS[0],image.getPixel(400,y));
   JSONObject qa=v.qaState(System.nanoTime());assertFalse(qa.getBoolean("drawPending"));
   assertEquals(2,qa.getJSONArray("drawnSeries").getJSONObject(0).getInt("breakBeforePoints"));
  }
 }
 @Test public void actualPausePixelsFreezeWhileTwoHistoriesAdvance()throws Exception {
  PlotController c=new PlotController();c.acceptNative(rows(1,100,2),1,false);select(c,0);select(c,1);
  c.acceptNative(rows(2,200,2),1,false);c.acceptNative(rows(3,300,2),1,false);
  PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);v.refresh();v.findViewById(R.id.plot_pause).performClick();Bitmap before=draw(v);
  for(int n=4;n<=20;n++)c.acceptNative(rows(n,100*n,2),1,false);
  v.refresh();Bitmap paused=draw(v);assertTrue("Actual chart pixels are frozen",before.sameAs(paused));
  assertEquals(19,c.history.snapshot().get(0).points.size());assertEquals(2,v.displayed().get(0).points.size());
  v.findViewById(R.id.plot_pause).performClick();Bitmap resumed=draw(v);assertFalse("Resume draws new intake",before.sameAs(resumed));
 }
 @Test public void pausedSameNameReplacementCannotKeepOldReaderCopy()throws Exception {
  PlotController c=new PlotController();c.acceptNative(rows(1,100,2),1,false);select(c,0);select(c,1);
  c.acceptNative(rows(2,200,2),1,false);
  PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);v.refresh();v.findViewById(R.id.plot_pause).performClick();
  PlotHistory.Series survivor=v.displayed().get(1);
  // Retirement and explicit reselection may finish on the worker/UI before refresh.
  c.acceptNative(rows(1,300,3),1,false);select(c,0);c.acceptNative(rows(2,400,3),1,false);v.refresh();
  assertEquals("Retired frozen identity must not resurrect just because its ID was reselected",1,v.displayed().size());
  assertSame("The other paused trace must not advance",survivor,v.displayed().get(0));
  draw(v);JSONObject qa=v.qaState(System.nanoTime());
  assertFalse(qa.getBoolean("drawPending"));assertEquals(1,qa.getJSONArray("drawnSeries").length());
  v.findViewById(R.id.plot_pause).performClick();assertEquals(2,v.displayed().size());
  assertEquals(3,v.displayed().get(1).provenance.reader);
 }
}
