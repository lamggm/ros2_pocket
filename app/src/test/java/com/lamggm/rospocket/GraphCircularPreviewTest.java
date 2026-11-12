package com.lamggm.rospocket;
import android.graphics.*;
import android.view.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

/** Native Android render of TEST-ONLY synthetic ROS metadata, not a device or DDS observation. */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=31,qualifiers="w1280dp-h850dp")
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GraphCircularPreviewTest {
 static JSONObject galaxy() throws Exception {
  String[][] ns={{"localization","odometry","fusion","map_server"},{"navigation","global_planner","local_planner","controller"},{"perception","lidar","camera","tracking"},{"safety","collision_monitor","watchdog","estop_monitor"},{"supervisor","mission","health","lifecycle"}};
  JSONArray names=new JSONArray();for(String[] g:ns)for(int i=1;i<g.length;i++)names.put("/robo/"+g[0]+"/"+g[i]);
  String[][] links={{"perception/lidar","localization/fusion","perception/scan"},{"localization/odometry","localization/fusion","localization/odom"},{"localization/map_server","navigation/global_planner","localization/map"},{"localization/fusion","navigation/global_planner","localization/pose"},{"navigation/global_planner","navigation/local_planner","navigation/path"},{"navigation/local_planner","navigation/controller","navigation/reference_fake"},{"perception/tracking","safety/collision_monitor","perception/obstacles"},{"perception/camera","perception/tracking","perception/image"},{"perception/lidar","safety/collision_monitor","perception/ranges"},{"safety/collision_monitor","navigation/controller","safety/limit_fake"},{"supervisor/mission","navigation/global_planner","supervisor/context_fake"},{"supervisor/health","supervisor/lifecycle","supervisor/health_fake"},{"safety/watchdog","supervisor/health","safety/heartbeat_fake"},{"safety/estop_monitor","supervisor/health","safety/input_fake"},{"supervisor/lifecycle","perception/camera","supervisor/lifecycle_fake"},{"navigation/controller","supervisor/health","navigation/progress_fake"}};
  JSONArray topics=new JSONArray();int i=0;for(String[] link:links){String writer="/robo/"+link[0],reader="/robo/"+link[1];topics.put(RosGraphModelTest.topic("/robo/"+link[2],"std_msgs/msg/String",new JSONArray().put(RosGraphModelTest.endpoint("fake-w"+i,"synthetic:"+writer,true,writer)).put(RosGraphModelTest.endpoint("fake-r"+i,"synthetic:"+reader,false,reader))));i++;}
  return new JSONObject().put("running",true).put("graph",new JSONObject().put("state","received").put("format","jazzy")).put("nodes",names).put("topics",topics);
 }
 void render(int width,int height,String label) throws Exception {
  ConnectionActivityTest f=new ConnectionActivityTest();f.setup();try{
   JSONObject sample=galaxy();f.bridge.snapshot=new JSONObject(f.bridge.snapshot).put("graph",sample.getJSONObject("graph")).put("topics",sample.getJSONArray("topics")).put("nodes",sample.getJSONArray("nodes")).toString();f.tick();f.paint();f.a.findViewById(R.id.nav_graph).performClick();RosGraphPanel p=(RosGraphPanel)f.get("rosGraph");View root=f.a.getWindow().getDecorView().findViewWithTag("app-root");File dir=new File("build/reports/graph-circular-v13");assertTrue(dir.isDirectory()||dir.mkdirs());int starts=f.bridge.starts,stops=f.bridge.stops,readers=f.bridge.subscriptions;
   while(p.physics.awake)p.advancePhysics();assertEquals(31,p.vertices.size());assertEquals(5,p.namespaceCircles(p.visibleVertices()).size());
   for(String theme:new String[]{"dark","light"}){UiTheme.save(f.a,theme);f.a.applyAppearance();p.select("");RosoutPresentationTest.layout(f.a.getWindow().getDecorView(),width,height);RosoutPresentationTest.layout(root,width,height);p.stage.fit(false);GraphPreviewTest.image(root,new File(dir,label+"-overview-"+theme+"-host-synthetic.png"),width,height);
    if(width>=1000){p.select("ns:/robo/navigation");RosoutPresentationTest.layout(root,width,height);p.stage.fit(false);GraphPreviewTest.image(root,new File(dir,label+"-namespace-"+theme+"-host-synthetic.png"),width,height);}
   }
   assertEquals(starts,f.bridge.starts);assertEquals(stops,f.bridge.stops);assertEquals(readers,f.bridge.subscriptions);
  }finally{f.cleanup();}
 }
 @Test public void nativeTabletCircularGalaxyBothThemes() throws Exception {render(1280,850,"tablet");}
 @Test @Config(qualifiers="w412dp-h850dp") public void nativePhoneCircularGalaxyBothThemes() throws Exception {render(412,850,"phone");}
}
