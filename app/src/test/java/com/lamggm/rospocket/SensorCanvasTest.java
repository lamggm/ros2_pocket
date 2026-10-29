package com.lamggm.rospocket;
import android.graphics.*;import android.view.View;import org.junit.*;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
import java.io.*;import java.nio.file.*;import org.json.JSONObject;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SensorCanvasTest {
 private SensorPanelTest.QueuedPanel panel;
 @Before public void setup(){panel=new SensorPanelTest.QueuedPanel();panel.available(true);panel.visible(true);}
 @After public void cleanup(){panel.dispose();}
 private void show(String type,byte[] packet){panel.select("rt/test",type);assertTrue(panel.accept(panel.desired(),packet,()->true));while(!panel.callbacks.isEmpty())panel.callbacks.remove(0).run();}
 private Bitmap surface(){View v=panel.findViewWithTag("sensor-surface");v.layout(0,0,400,400);Bitmap b=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));return b;}
 @Test public void cameraColorsReachCanvasAndSwitchingClearsThem()throws Exception{
  show("sensor_msgs::msg::dds_::Image_",SensorPanelTest.raw(255));assertEquals(0xffff0000,surface().getPixel(200,200));
  panel.findViewWithTag("sensor-LaserScan").performClick();assertEquals(0xff101820,surface().getPixel(200,200));assertNull(panel.desired());
  panel.findViewWithTag("sensor-Câmera").performClick();assertNotNull(panel.desired());assertEquals(0xff101820,surface().getPixel(200,200));
 }
 @Test public void pngAndJpegUseRealDecoderAndOversizedDimensionsAreRejected()throws Exception{
  Bitmap input=Bitmap.createBitmap(4,3,Bitmap.Config.ARGB_8888);input.eraseColor(Color.RED);
  for(Bitmap.CompressFormat format:new Bitmap.CompressFormat[]{Bitmap.CompressFormat.PNG,Bitmap.CompressFormat.JPEG}){
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();assertTrue(input.compress(format,100,bytes));
   JSONObject m=new JSONObject().put("kind","compressed").put("format",format==Bitmap.CompressFormat.PNG?"png":"jpeg").put("ageMs",0);
   SensorPacket.Decoded d=new SensorPacket(SensorPacketTest.packet(m,bytes.toByteArray())).decode();assertEquals(4,d.image.getWidth());assertEquals(3,d.image.getHeight());assertTrue(Color.red(d.image.getPixel(1,1))>240);
  }
  Bitmap huge=Bitmap.createBitmap(1281,1,Bitmap.Config.ARGB_8888);ByteArrayOutputStream bytes=new ByteArrayOutputStream();huge.compress(Bitmap.CompressFormat.PNG,100,bytes);
  try{new SensorPacket(SensorPacketTest.packet(new JSONObject().put("kind","compressed").put("format","png"),bytes.toByteArray())).decode();fail("Oversized width accepted");}catch(IllegalArgumentException expected){}
 }
 @Test public void realDdsPacketsDecodeAndRenderWithoutSyntheticCoordinates()throws Exception{
  String root=System.getenv("POCKET_SENSOR_PACKETS");Assume.assumeTrue("Explicit real DDS replay gate",root!=null);
  for(String rmw:new String[]{"rmw_fastrtps_cpp","rmw_cyclonedds_cpp"}){
   java.nio.file.Path dir=Paths.get(root,rmw);
   show("sensor_msgs::msg::dds_::LaserScan_",Files.readAllBytes(dir.resolve("scan.packet")));
   Bitmap scan=surface();assertEquals("ROS +X is rendered upwards",0xff5cdbb8,scan.getPixel(200,164));
   SensorPacket.Decoded d=org.robolectric.util.ReflectionHelpers.getField(panel,"shown");assertEquals(4,d.xy.length);assertEquals(2,d.xy[0],.0001);assertEquals(0,d.xy[1],.0001);
   d.initialAgeMs=4000;surface();android.widget.TextView status=panel.findViewWithTag("sensor-status");assertTrue(status.getText().toString().contains("OBSOLETO"));
   File output=new File("build/reports/sensors");assertTrue(output.isDirectory()||output.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(output,rmw+"-scan.png"))){assertTrue(scan.compress(Bitmap.CompressFormat.PNG,100,out));}
   show("sensor_msgs::msg::dds_::Image_",Files.readAllBytes(dir.resolve("image.packet")));assertEquals(0xffff0000,surface().getPixel(200,200));
   show("sensor_msgs::msg::dds_::CompressedImage_",Files.readAllBytes(dir.resolve("compressed.packet")));SensorPacket.Decoded image=org.robolectric.util.ReflectionHelpers.getField(panel,"shown");assertEquals(320,image.image.getWidth());assertEquals(240,image.image.getHeight());surface();
  }
 }
}
