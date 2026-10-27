package com.lamggm.rospocket;
import android.graphics.*;
import android.view.View;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
/** Actual host native Canvas; typed native rows are synthetic, not JNI/device evidence. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GraphProvenanceViewTest {
 private PlotController live() throws Exception {
  PlotController c=new PlotController();c.acceptNative(PlotMultiWatchTest.snapshot(1,100),1,false);
  for(int i=0;i<2;i++)assertTrue(c.select(PlotMultiWatchTest.watch(i),"field[86]"));
  c.acceptNative(PlotMultiWatchTest.snapshot(2,200),1,false);c.acceptNative(PlotMultiWatchTest.snapshot(3,300),1,false);return c;
 }
 private void draw(PlotView v){
  View chart=v.findViewById(R.id.plot_chart);chart.layout(0,0,800,300);
  Bitmap image=Bitmap.createBitmap(800,300,Bitmap.Config.ARGB_8888);chart.draw(new Canvas(image));
  for(int color:new int[]{PlotView.COLORS[0],PlotView.COLORS[1]}){
   int pixels=0;for(int y=40;y<265;y++)for(int x=0;x<800;x++)if(image.getPixel(x,y)==color)pixels++;
   assertTrue("both genuine series reach Canvas",pixels>0);
  }
 }
 @Test public void completedDrawCannotRelabelAnOldPreparedCopyWithCurrentIdentity() throws Exception {
  PlotController c=live();PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);v.refresh();
  JSONObject replacement=PlotMultiWatchTest.snapshot(4,400).put("sessionGeneration",2);
  for(int i=0;i<4;i++)replacement.getJSONArray("watches").getJSONObject(i).put("generation",90+i);
  c.acceptNative(replacement,2,false);draw(v);
  JSONObject state=v.qaState(System.nanoTime());assertFalse(state.getBoolean("drawPending"));
  JSONArray prepared=state.getJSONArray("series"),drawn=state.getJSONArray("drawnSeries");
  assertEquals(2,drawn.length());assertEquals(prepared.toString(),drawn.toString());
  for(int i=0;i<2;i++){
   JSONObject s=drawn.getJSONObject(i);assertEquals("old copy session",1,s.optLong("sessionGeneration",-1));
   assertEquals("old copy reader",10+i,s.optLong("readerGeneration",-1));assertEquals(i,s.getInt("slot"));
   assertEquals(PlotMultiWatchTest.watch(i)+"\nfield[86]",s.getString("seriesId"));assertEquals("field[86]",s.getString("fieldPath"));
  }
  v.refresh();assertTrue(v.qaState(System.nanoTime()).getBoolean("drawPending"));draw(v);
  assertEquals(2,v.qaState(System.nanoTime()).getJSONArray("drawnSeries").getJSONObject(0).getLong("sessionGeneration"));
 }
 @Test public void pausedCopiesRetainIdentityAndStatisticsAsIntakeAdvances() throws Exception {
  PlotController c=live();PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);v.refresh();v.findViewWithTag("plot-pause").performClick();draw(v);
  String frozen=v.qaState(System.nanoTime()).getJSONArray("drawnSeries").toString();
  c.acceptNative(PlotMultiWatchTest.snapshot(4,400),1,false);v.refresh();draw(v);
  assertEquals(frozen,v.qaState(System.nanoTime()).getJSONArray("drawnSeries").toString());
  assertEquals(3,c.qaStatistics().getJSONArray("series").getJSONObject(0).getInt("pointCount"));
  assertEquals(2,v.qaState(System.nanoTime()).getJSONArray("series").getJSONObject(0).getInt("pointCount"));
 }
}
