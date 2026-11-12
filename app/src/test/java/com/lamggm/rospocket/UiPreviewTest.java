package com.lamggm.rospocket;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
/** Real native Android Canvas rendering of synthetic host inputs; not physical acceptance. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31,qualifiers="w1000dp-h700dp")
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiPreviewTest {
 private void render(int width,int height,String prefix) throws Exception {
  ConnectionActivityTest f=new ConnectionActivityTest();f.setup();
  try {
   JSONObject topic=new JSONObject().put("name","/numeric").put("type","std_msgs/msg/Float64").put("wireName","rt/numeric").put("wireType","std_msgs::msg::dds_::Float64_").put("supported",true).put("publishers",1).put("subscribers",1);
   f.bridge.snapshot=new JSONObject(f.bridge.snapshot).put("topics",new JSONArray().put(topic)).toString();f.tick();f.paint();
   File dir=new File("build/reports/ui-v13");assertTrue(dir.isDirectory()||dir.mkdirs());
   for(String theme:new String[]{"light","dark"})for(String section:new String[]{"monitor","topics"}){
    UiTheme.save(f.a,theme);f.a.applyAppearance();int id=f.a.getResources().getIdentifier("nav_"+section,"id",f.a.getPackageName());f.a.findViewById(id).performClick();f.call("refreshDashboard",new Class<?>[]{});
    if(section.equals("topics"))f.a.findViewById(R.id.topics).findViewWithTag("details:rt/numeric\nstd_msgs::msg::dds_::Float64_").performClick();
    View root=f.a.getWindow().getDecorView().findViewWithTag("app-root");root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);root.getViewTreeObserver().dispatchOnGlobalLayout();
    Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));int varied=0;for(int y=0;y<height;y+=4)for(int x=0;x<width;x+=4)if(b.getPixel(x,y)!=b.getPixel(5,5))varied++;assertTrue("UI cannot be blank",varied>100);
    try(FileOutputStream out=new FileOutputStream(new File(dir,prefix+"-"+section+"-"+theme+"-host-synthetic.png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();
   }
  }finally{f.cleanup();}
 }
 @Test public void tabletNativeViewsRenderBothThemes() throws Exception {render(1280,850,"tablet");}
 @Test @Config(qualifiers="w412dp-h850dp") public void phoneNativeViewsRenderBothThemes() throws Exception {render(412,850,"phone");}
 @Test public void populatedReferencePanelRendersOnlySyntheticHostInputs() throws Exception {
  MultiWatchActivityTest f=new MultiWatchActivityTest();f.setup();
  try{
   MultiWatchActivityTest.MultiFake bridge=f.extras();
   for(int slot=1;slot<=2;slot++){f.call("chooseWatch",new Class<?>[]{int.class},slot);f.call("select",new Class<?>[]{JSONObject.class},new JSONObject().put("wireName",slot==1?"rt/state":"rt/voltage").put("wireType",slot==1?"std_msgs::msg::dds_::String_":"std_msgs::msg::dds_::Float64_").put("supported",true));f.drain();}
   JSONObject s=new JSONObject(bridge.snapshot).put("sessionGeneration",1).put("readerGeneration",1);
   s.getJSONArray("watches").getJSONObject(1).put("last","READY");s.getJSONArray("watches").getJSONObject(2).put("last","24.8");
   long start=System.nanoTime()-4_000_000_000L;
   s.put("receivedMonotonicNs",start).put("numericFields",new JSONArray().put(new JSONObject().put("path","data").put("value",42.5).put("state","finite").put("unit",JSONObject.NULL)));
   JSONObject primary=s.getJSONArray("watches").getJSONObject(0);primary.put("generation",1).put("subscription","/numeric").put("type","std_msgs::msg::dds_::Float64_").put("receivedMonotonicNs",start).put("numericFields",s.getJSONArray("numericFields"));
   bridge.snapshot=s.toString();f.set("lastUi",0L);f.tick();f.paint();
   View root=f.a.getWindow().getDecorView().findViewWithTag("app-root");root.findViewWithTag("plot-settings").performClick();PlotController controller=(PlotController)f.get("plots");assertEquals(1,controller.fields().size());((CheckBox)root.findViewWithTag("plot-field:"+controller.fields().get(0).id())).performClick();root.findViewWithTag("plot-settings").performClick();
   for(int i=1;i<=20;i++){double value=42.5+Math.sin(i*.3)*2;s.put("last",String.format(java.util.Locale.ROOT,"%.2f",value)).put("count",20+i).put("receivedMonotonicNs",start+i*200_000_000L);primary.put("count",20+i).put("receivedMonotonicNs",start+i*200_000_000L);s.getJSONArray("numericFields").getJSONObject(0).put("value",value);bridge.snapshot=s.toString();f.set("lastUi",0L);f.tick();f.paint();}
   f.call("chooseWatch",new Class<?>[]{int.class},0);f.a.findViewById(R.id.nav_monitor).performClick();
   SensorPanel panel=(SensorPanel)f.get("sensorPanel");panel.select("rt/scan","sensor_msgs::msg::dds_::LaserScan_");panel.available(true);panel.visible(true);
   java.nio.ByteBuffer xy=java.nio.ByteBuffer.allocate(240*8).order(java.nio.ByteOrder.LITTLE_ENDIAN);for(int i=0;i<240;i++){double angle=i*Math.PI*2/240,r=4+Math.sin(i*.13)*.5;xy.putFloat((float)(Math.cos(angle)*r)).putFloat((float)(Math.sin(angle)*r));}
   File dir=new File("build/reports/ui-v13");assertTrue(dir.isDirectory()||dir.mkdirs());
   for(String theme:new String[]{"light","dark"}){
    UiTheme.save(f.a,theme);f.a.applyAppearance();f.call("refreshDashboard",new Class<?>[]{});
    byte[] packet=SensorPacketTest.packet(new JSONObject().put("running",true).put("kind","scan").put("ageMs",0).put("fresh",true).put("rangeMax",10).put("points",240).put("ranges",240).put("stride",1).put("frameId","host_synthetic_scan"),xy.array());assertTrue(panel.accept(panel.desired(),packet,()->true));
    java.util.concurrent.ExecutorService decoder=org.robolectric.util.ReflectionHelpers.getField(panel,"ownedDecoder");decoder.submit(()->{}).get(3,java.util.concurrent.TimeUnit.SECONDS);java.lang.reflect.Method deliver=SensorPanel.class.getDeclaredMethod("deliverUi");deliver.setAccessible(true);deliver.invoke(panel);
    root.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(850,View.MeasureSpec.EXACTLY));root.layout(0,0,1280,850);root.getViewTreeObserver().dispatchOnGlobalLayout();
    assertEquals(1,((PlotView)f.get("plotView")).displayed().size());assertNotNull(org.robolectric.util.ReflectionHelpers.getField(panel,"shown"));
    Bitmap bitmap=Bitmap.createBitmap(1280,850,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));try(FileOutputStream out=new FileOutputStream(new File(dir,"tablet-reference-"+theme+"-host-synthetic.png"))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
   }
  }finally{((SensorPanel)f.get("sensorPanel")).dispose();f.cleanup();}
 }
}
