package com.lamggm.rospocket;
import java.nio.file.*;import java.util.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
/** Optional real ROS/DDS captures, NOT synthetic transport. Android decoder still runs on host. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class LocationDdsReplayTest {
 @Test public void realRosPacketsDecodeThroughAndroidMapAndPoseModels()throws Exception{
  String root=System.getenv("POCKET_LOCATION_DDS_RECORDS");Assume.assumeTrue("Requires separately generated real ROS map/TF records",root!=null);int count=0;
  for(String rmw:new String[]{"rmw_fastrtps_cpp","rmw_cyclonedds_cpp"}){Path folder=Paths.get(root,rmw);SensorPacket.Decoded value=new SensorPacket(Files.readAllBytes(folder.resolve("map.packet"))).decode();assertNotNull(value.map);assertEquals(3,value.map.width);assertEquals(2,value.map.height);assertEquals("map",value.map.frame);assertEquals(.4,value.map.yaw,1e-6);assertEquals(0xff63717d,value.image.getPixel(0,0));assertEquals(0xff17262e,value.image.getPixel(2,1));
   JSONObject raw=new JSONObject(new String(Files.readAllBytes(folder.resolve("tf.json")),java.nio.charset.StandardCharsets.UTF_8));assertTrue(raw.getBoolean("running"));RobotTf tree=new RobotTf();long now=System.nanoTime();tree.accept(raw.getJSONArray("rows"),now);RobotTf.Pose pose=tree.solve(value.map.frame,Collections.singleton("base_link"),now);assertTrue(pose.status,pose.live);assertEquals(1.7,pose.transforms.get("base_link")[3],1e-9);assertEquals(-1.8,pose.transforms.get("base_link")[7],1e-9);count++;
  }assertEquals(2,count);
 }
}
