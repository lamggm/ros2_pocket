package com.lamggm.rospocket;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class LocationMapViewTest {
 @Test public void mapRefreshInTheSameFrameDoesNotEraseAnUnexpiredWorldPose()throws Exception{
  LocationMapView view=new LocationMapView(RuntimeEnvironment.getApplication());view.map(new MapGrid(MapPacketTest.meta(),new byte[]{-1,0,50,100}));view.pose(1.05,2.05,.2,Long.MAX_VALUE);assertTrue(view.poseVisible());view.map(new MapGrid(MapPacketTest.meta().put("originX",.9),new byte[]{0,0,0,100}));assertTrue("Streaming map updates must not starve the pose overlay",view.poseVisible());
 }
 @Test public void frameChangeAndExpiredPoseNeverDisplayAnInventedOriginMarker()throws Exception{
  LocationMapView v=new LocationMapView(RuntimeEnvironment.getApplication());v.map(new MapGrid(MapPacketTest.meta(),new byte[]{0,0,0,0}));v.pose(1,2,0,0);assertFalse(v.poseVisible());v.pose(1,2,0,Long.MAX_VALUE);assertTrue(v.poseVisible());v.map(new MapGrid(MapPacketTest.meta().put("frameId","other_map"),new byte[]{0,0,0,0}));assertFalse(v.poseVisible());
 }
 @Test public void repeatedTfStampCannotExtendTheDisplayedPoseDeadline()throws Exception{
  RobotTf tree=new RobotTf();org.json.JSONArray rows=new org.json.JSONArray().put(new org.json.JSONObject().put("parent","map").put("child","base_link").put("writer","fixture").put("stamp",9L).put("static",false).put("translation",new org.json.JSONArray().put(1).put(2).put(0)).put("rotation",new org.json.JSONArray().put(0).put(0).put(0).put(1)));tree.accept(rows,1000);RobotTf.Pose first=tree.solve("map",java.util.Collections.singleton("base_link"),2000);assertTrue(first.live);assertEquals(1000L+2000000000L,first.deadline);tree.accept(rows,1000000);assertEquals(first.deadline,tree.solve("map",java.util.Collections.singleton("base_link"),2000000).deadline);assertFalse(tree.solve("map",java.util.Collections.singleton("base_link"),first.deadline+1).live);
 }
}
