package com.lamggm.rospocket;

import android.app.Dialog;
import android.graphics.*;
import android.view.View;
import android.widget.TextView;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.annotation.*;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31,qualifiers="night") @LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PanelPresentationTest {
 @Test public void legacyPlacementAndNamesSurviveAddingCamera()throws Exception{
  DashboardLayout old=DashboardLayout.defaults().project(8).rename("plot","Velocidade").position("plot",2,12);
  JSONObject fixture=new JSONObject(old.encode()).put("schema",5);
  JSONArray cards=fixture.getJSONArray("cards");for(int i=cards.length()-1;i>=0;i--)if(cards.getJSONObject(i).getString("id").equals("camera"))cards.remove(i);
  DashboardLayout migrated=DashboardLayout.decode(fixture.toString());
  for(int i=0;i<cards.length();i++){
   JSONObject before=cards.getJSONObject(i);DashboardLayout.Item after=migrated.get(before.getString("id"));
   assertEquals(before.getInt("column"),after.column);assertEquals(before.getInt("row"),after.row);assertEquals(before.getString("name"),after.name);
  }
  assertEquals(8,migrated.items.size());assertTrue(migrated.get("camera").row>=0);assertEquals(migrated.encode(),DashboardLayout.decode(migrated.encode()).encode());
  fixture.put("schema",6);assertThrows(IllegalArgumentException.class,()->DashboardLayout.decode(fixture.toString()));
 }
 @Test public void openingEitherSensorDialogLeavesBothTopicsActive()throws Exception{
  ConnectionActivityTest f=new ConnectionActivityTest();f.setup();
  try{
   f.controller.visible();f.a.findViewById(R.id.nav_monitor).performClick();f.paint();
   DashboardBoard b=f.a.getWindow().getDecorView().findViewWithTag("dashboard-board");
   SensorPanel scan=(SensorPanel)f.get("sensorPanel"),camera=(SensorPanel)f.get("cameraPanel");
   scan.select("rt/scan","sensor_msgs::msg::dds_::LaserScan_");camera.select("rt/image","sensor_msgs::msg::dds_::Image_");scan.available(true);camera.available(true);
   assertEquals("rt/scan",scan.desired().wire);assertNotNull("Camera must stay active alongside LiDAR",camera.desired());long oldScan=scan.epoch(),oldCamera=camera.epoch();
   for(String id:Arrays.asList("camera","sensors")){
    boolean image=id.equals("camera");View hit=b.findViewWithTag("preview-open:"+id);assertNotNull(hit);hit.performClick();f.paint();
    Dialog d=ShadowDialog.getLatestDialog();assertTrue(d.isShowing());View decor=d.getWindow().getDecorView();
    assertEquals(image?"Camera":"Lidar Scan",((TextView)decor.findViewWithTag("preview-title")).getText().toString());
    assertNull(decor.findViewWithTag("sensor-Câmera"));assertNull(decor.findViewWithTag("sensor-LaserScan"));
    assertEquals(image?"rt/image":"rt/scan",(image?camera:scan).desired().wire);assertNotNull((image?scan:camera).desired());
    decor.findViewWithTag("preview-close").performClick();f.paint();
   }
   assertTrue(scan.current(scan.desired(),oldScan));assertTrue(camera.current(camera.desired(),oldCamera));
  }finally{f.cleanup();}
 }
 @Test public void compactPlotActuallyDrawsCustomTitleScalesAndLegendInsideCard()throws Exception{
  ContextFixture f=new ContextFixture();PlotView plot=f.plot;DashboardPreview preview=new DashboardPreview(f.activity,"plot","Gráfico",plot,plot::previewMode,plot::closeDetails);
  preview.alias("Velocidade");plot.refresh();View chart=plot.findViewById(R.id.plot_chart);int w=232,h=232;chart.layout(0,0,w,h);
  Map<String,PointF> labels=new LinkedHashMap<>();Bitmap image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image){
   @Override public void drawText(String text,float x,float y,Paint p){labels.put(text,new PointF(x,y));super.drawText(text,x,y,p);}
  };
  chart.draw(canvas);
  assertTrue(labels.toString(),labels.containsKey("Velocidade"));assertTrue(labels.toString(),labels.containsKey("-2 s"));assertTrue(labels.containsKey("0 s"));assertTrue(labels.containsKey("-1"));assertTrue(labels.containsKey("3"));
  String key=labels.keySet().stream().filter(x->x.contains("linear.x")).findFirst().orElseThrow(AssertionError::new);
  assertTrue(labels.get(key).x>w/2f);assertTrue(labels.get(key).y>h*.75f);
  java.nio.file.Path dir=java.nio.file.Paths.get("build/reports/named-panels");java.nio.file.Files.createDirectories(dir);
  try(java.io.OutputStream out=java.nio.file.Files.newOutputStream(dir.resolve("plot-compact-host.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}image.recycle();f.activity.finish();
 }
 static final class ContextFixture {
  final android.app.Activity activity=org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();final PlotController controller=new PlotController();final PlotView plot=new PlotView(activity,controller);
  ContextFixture(){controller.history.select("Watch 1\nlinear.x","linear.x","m/s");controller.history.append("Watch 1\nlinear.x",1_000_000_000L,-1d,null);controller.history.append("Watch 1\nlinear.x",3_000_000_000L,3d,null);}
 }
}
