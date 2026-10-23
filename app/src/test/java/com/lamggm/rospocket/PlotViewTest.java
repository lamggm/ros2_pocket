package com.lamggm.rospocket;
import android.graphics.*;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlotViewTest {
 @Test public void actualControlsSelectOverlayAndPauseOnlyPresentation()throws Exception {
  PlotController c=new PlotController();c.acceptNative(PlotControllerTest.sample(1,100),1,false);
  PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);v.refresh();
  ((CheckBox)v.findViewWithTag("plot-field:/value\ndata")).performClick();
  c.acceptNative(PlotControllerTest.sample(2,200),1,false);v.refresh();assertEquals(1,v.displayed().get(0).points.size());
  ((Button)v.findViewWithTag("plot-pause")).performClick();
  c.acceptNative(PlotControllerTest.sample(3,300),1,false);v.refresh();
  assertEquals(2,c.history.points("/value\ndata").size());assertEquals(1,v.displayed().get(0).points.size());
  ((Button)v.findViewWithTag("plot-pause")).performClick();assertEquals(2,v.displayed().get(0).points.size());
 }
 @Test public void deselectWhilePausedDoesNotKeepAnUnselectedTrace()throws Exception {
  PlotController c=new PlotController();c.acceptNative(PlotControllerTest.sample(1,100),1,false);c.select("/value","data");c.acceptNative(PlotControllerTest.sample(2,200),1,false);
  PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);v.refresh();((Button)v.findViewWithTag("plot-pause")).performClick();
  ((CheckBox)v.findViewWithTag("plot-field:/value\ndata")).performClick();assertTrue(v.displayed().isEmpty());
 }
 @Test public void resetClearsPausedCopyAndRemovedSourceRemainsRemovable()throws Exception {
  PlotController c=new PlotController();c.acceptNative(PlotControllerTest.sample(1,100),1,false);c.select("/value","data");c.acceptNative(PlotControllerTest.sample(2,200),1,false);
  PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);v.refresh();((Button)v.findViewWithTag("plot-pause")).performClick();
  c.reset(2);v.refresh();assertTrue(v.displayed().get(0).points.isEmpty());
  ((Button)v.findViewWithTag("plot-remove:/value\ndata")).performClick();assertTrue(c.history.snapshot().isEmpty());
 }
 @Test public void nativeCanvasOverlaysFourColorsButDoesNotBridgeNonfiniteGaps(){
  PlotController c=new PlotController();PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);
  for(int i=0;i<4;i++){String id="s"+i;c.history.select(id,"x",null);c.history.append(id,1,(double)i,null);c.history.append(id,2,Double.NaN,null);c.history.append(id,3,(double)i+1,null);}
  v.refresh();android.view.View chart=v.getChildAt(v.getChildCount()-1);chart.layout(0,0,800,300);
  Bitmap image=Bitmap.createBitmap(800,300,Bitmap.Config.ARGB_8888);chart.draw(new Canvas(image));
  for(int color:PlotView.COLORS){int count=0;for(int y=40;y<265;y++)for(int x=0;x<800;x++)if(image.getPixel(x,y)==color)count++;assertTrue("each series is drawn",count>0);}
  for(int y=40;y<265;y++)for(int color:PlotView.COLORS)assertNotEquals("No invented continuous line across NaN",color,image.getPixel(400,y));
 }
 @Test public void realCanvasDrawsExtremeFiniteValuesWithFiniteProjection() {
  PlotController c=new PlotController();PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);
  c.history.select("s","x",null);c.history.append("s",1,-Double.MAX_VALUE,null);c.history.append("s",2,Double.MAX_VALUE,null);
  v.refresh();v.measure(android.view.View.MeasureSpec.makeMeasureSpec(800,1073741824),android.view.View.MeasureSpec.makeMeasureSpec(600,1073741824));v.layout(0,0,800,600);
  Bitmap image=Bitmap.createBitmap(800,600,Bitmap.Config.ARGB_8888);v.draw(new Canvas(image));
  int colorCount=0;for(int y=0;y<600;y++)for(int x=0;x<800;x++)if(image.getPixel(x,y)==PlotView.COLORS[0])colorCount++;
  assertTrue("Actual native Canvas must contain the plotted line",colorCount>20);
  assertEquals(0,PlotView.fraction(-Double.MAX_VALUE,-Double.MAX_VALUE,Double.MAX_VALUE),0);
  assertEquals(1,PlotView.fraction(Double.MAX_VALUE,-Double.MAX_VALUE,Double.MAX_VALUE),0);
  assertEquals(.5,PlotView.fraction(7,7,7),0);
 }

 @Test public void invalidationBetweenRevisionObservationAndPaintingIsNotConsumed()throws Exception {
  PlotController c=new PlotController();c.acceptNative(PlotControllerTest.sample(1,100),1,false);c.select("/value","data");c.acceptNative(PlotControllerTest.sample(2,200),1,false);
  PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);v.refresh();((Button)v.findViewWithTag("plot-pause")).performClick();
  // Deterministically interleave DDS teardown after refresh observes its revision.
  long observed=c.revision();c.reset(2);v.refresh(observed);v.refresh();
  assertTrue("A reset cannot be acknowledged without discarding paused points",v.displayed().get(0).points.isEmpty());
 }
 @Test public void removeButtonWhilePausedKeepsOtherSeriesFrozen(){removeOnePausedSeries(true);}
 @Test public void uncheckWhilePausedKeepsOtherSeriesFrozen(){removeOnePausedSeries(false);}
 private void removeOnePausedSeries(boolean button){
  PlotController c=new PlotController();
  for(String w:new String[]{"first","second"}){
   java.util.List<PlotController.Field> fs=java.util.Collections.singletonList(new PlotController.Field(w,"data",null,1.0));
   c.accept(new PlotController.Frame(w,1,1,100,null,fs));c.select(w,"data");c.accept(new PlotController.Frame(w,1,2,200,null,fs));
  }
  PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);v.refresh();((Button)v.findViewWithTag("plot-pause")).performClick();
  for(String w:new String[]{"first","second"})c.accept(new PlotController.Frame(w,1,3,300,null,java.util.Collections.singletonList(new PlotController.Field(w,"data",null,2.0))));
  v.refresh();String tag=(button?"plot-remove:":"plot-field:")+"first\ndata";v.findViewWithTag(tag).performClick();
  assertEquals(1,v.displayed().size());assertEquals("second\ndata",v.displayed().get(0).id);
  assertEquals("Removing a different series must not advance the paused trace",1,v.displayed().get(0).points.size());
  assertEquals(200,v.displayed().get(0).points.get(0).time);assertEquals(2,c.history.points("second\ndata").size());
  v.refresh();assertEquals(1,v.displayed().get(0).points.size());
  ((Button)v.findViewWithTag("plot-pause")).performClick();assertEquals(2,v.displayed().get(0).points.size());
 }
}
