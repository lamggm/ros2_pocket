package com.lamggm.rospocket;
import android.graphics.Bitmap;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
/** Real native host Canvas; the incoming batch is explicitly synthetic. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NumericBatchCanvasTest {
 @Test public void coincidentReceiveGapCannotBecomeAVisibleConnectingSegment()throws Exception{
  for(boolean gap:new boolean[]{false,true}){
   PlotController c=new PlotController();c.acceptNative(NumericBatchControllerTest.snapshot(1,new JSONArray()),1,false);assertTrue(c.select("Watch 1 · /batch","data"));
   JSONArray samples=new JSONArray().put(new JSONArray().put(2).put(2000).put(20).put(2.0));
   if(gap)samples.put(new JSONArray().put(3).put(2000).put(30).put(JSONObject.NULL));
   samples.put(new JSONArray().put(4).put(4000).put(40).put(4.0));c.acceptNative(NumericBatchControllerTest.snapshot(4,samples),1,false);
   PlotView view=new PlotView(RuntimeEnvironment.getApplication(),c);view.refresh();Bitmap image=PlotQualificationTest.draw(view);
   int middle=0;for(int y=42;y<263;y++)if(image.getPixel(400,y)==PlotView.COLORS[0])middle++;
   if(gap)assertEquals("A distinct same-clock null sample must cut the actual Canvas trace",0,middle);
   else assertTrue("Positive control must really draw the connecting segment",middle>0);
  }
 }
}
