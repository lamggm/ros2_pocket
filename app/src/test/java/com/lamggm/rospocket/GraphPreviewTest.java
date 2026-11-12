package com.lamggm.rospocket;
import android.graphics.*;
import android.view.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;
/** Real Android Canvas with an explicitly synthetic topology. Not a physical ROS/device test. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31,qualifiers="w1280dp-h850dp") @LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GraphPreviewTest {
 static JSONObject sample() throws Exception {
  String[][] links={{"/camera/image","sensor_msgs/msg/CompressedImage","/camera","/vision"},{"/detections","std_msgs/msg/String","/vision","/dashboard"},{"/scan","sensor_msgs/msg/LaserScan","/lidar","/localization"},{"/pose","geometry_msgs/msg/Pose","/localization","/planner"},{"/path","nav_msgs/msg/Path","/planner","/dashboard"}};
  JSONArray topics=new JSONArray(),nodes=new JSONArray();java.util.Set<String> names=new java.util.LinkedHashSet<>();int i=0;for(String[] link:links){String writer="synthetic"+link[2],reader="synthetic"+link[3];names.add(link[2]);names.add(link[3]);topics.put(RosGraphModelTest.topic(link[0],link[1],new JSONArray().put(RosGraphModelTest.endpoint("w"+i,writer,true,link[2])).put(RosGraphModelTest.endpoint("r"+i,reader,false,link[3]))));i++;}for(String name:names)nodes.put(name);return new JSONObject().put("running",true).put("graph",new JSONObject().put("state","received").put("format","jazzy")).put("topics",topics).put("nodes",nodes);
 }
 static void image(View view,File path,int width,int height) throws Exception {Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(path)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();}
 private void render(int width,int height,String prefix) throws Exception {
  ConnectionActivityTest f=new ConnectionActivityTest();f.setup();try{
   JSONObject data=sample();f.bridge.snapshot=new JSONObject(f.bridge.snapshot).put("graph",data.getJSONObject("graph")).put("topics",data.getJSONArray("topics")).put("nodes",data.getJSONArray("nodes")).toString();f.tick();f.paint();f.a.findViewById(R.id.nav_graph).performClick();RosGraphPanel p=(RosGraphPanel)f.get("rosGraph");View root=f.a.getWindow().getDecorView().findViewWithTag("app-root");File dir=new File("build/reports/graph-v13");assertTrue(dir.isDirectory()||dir.mkdirs());int starts=f.bridge.starts,stops=f.bridge.stops,subscriptions=f.bridge.subscriptions;
   for(int i=0;i<210;i++)p.advancePhysics();assertFalse(p.physics.awake);
   for(String theme:new String[]{"dark","light"}){
    UiTheme.save(f.a,theme);f.a.applyAppearance();p.select("");RosoutPresentationTest.layout(f.a.getWindow().getDecorView(),width,height);RosoutPresentationTest.layout(root,width,height);p.stage.fit(false);assertTrue(p.stage.getHeight()>=300);assertTrue(p.positions.size()>8);image(root,new File(dir,prefix+"-overview-"+theme+"-host-synthetic.png"),width,height);
    if(width>=1000){p.select(GraphInteractionTest.named(p,"/localization"));RosoutPresentationTest.layout(root,width,height);Rect rect=new Rect();assertTrue(p.pinButton.getGlobalVisibleRect(rect));assertEquals(p.pinButton.getHeight(),rect.height());assertEquals(View.VISIBLE,p.inspector.getVisibility());image(root,new File(dir,prefix+"-inspector-"+theme+"-host-synthetic.png"),width,height);}
   }
   assertEquals(starts,f.bridge.starts);assertEquals(stops,f.bridge.stops);assertEquals(subscriptions,f.bridge.subscriptions);
   if(width>=1000){UiTheme.save(f.a,"dark");f.a.applyAppearance();p.select("");RosoutPresentationTest.layout(root,width,height);p.physics.wake();String id=GraphInteractionTest.named(p,"/localization");RectF pos=p.positions.get(id);p.physics.move(id,pos.centerX()-180,pos.centerY()-60);p.advancePhysics();p.stage.fit(false);for(int frame=0;frame<32;frame++){for(int j=0;j<6;j++)p.advancePhysics();image(root,new File(dir,String.format(java.util.Locale.ROOT,"motion-%02d-host-synthetic.png",frame)),width,height);}}
  }finally{f.cleanup();}
 }
 @Test public void tabletPhysicsGraphAndInspectorRenderBothThemes() throws Exception {render(1280,850,"tablet");}
 @Test @Config(qualifiers="w412dp-h850dp") public void phonePhysicsGraphRendersBothThemes() throws Exception {render(412,850,"phone");}
}
