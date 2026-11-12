package com.lamggm.rospocket;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppearanceCanvasTest {
 @Test public void sameSensorCanvasFollowsThemeWithoutReaderReplacement() {
  Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("appearance",0).edit().putString("mode","light").commit();
  SensorPanel p=new SensorPanel(c);View canvas=p.findViewWithTag("sensor-surface");canvas.layout(0,0,300,200);
  Bitmap light=Bitmap.createBitmap(300,200,Bitmap.Config.ARGB_8888);canvas.draw(new Canvas(light));assertEquals(0xffeaf1f4,light.getPixel(2,2));
  c.getSharedPreferences("appearance",0).edit().putString("mode","dark").commit();
  Bitmap dark=Bitmap.createBitmap(300,200,Bitmap.Config.ARGB_8888);canvas.draw(new Canvas(dark));assertEquals(0xff101820,dark.getPixel(2,2));p.dispose();
 }
}
