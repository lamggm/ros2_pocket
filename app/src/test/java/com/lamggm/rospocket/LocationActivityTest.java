package com.lamggm.rospocket;
import android.view.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class LocationActivityTest {
 final ConnectionActivityTest f=new ConnectionActivityTest();
 @Before public void setup()throws Exception{f.setup();f.controller.visible();f.paint();}
 @After public void cleanup()throws Exception{f.cleanup();}
 View tagged(String t){return f.a.getWindow().getDecorView().findViewWithTag(t);}
 static final class Maps extends ConnectionActivityTest.Fake {
  int mapStarts,tfStarts,sensorStarts,descriptionStarts,activeVisual;boolean send=true;long stamp=9000000000L;
  public byte[] startMap(int d,String a,String p,String w,boolean r,boolean t,String mode,String format){assertEquals(0,activeVisual);assertEquals("rt/map",w);mapStarts++;activeVisual=1;send=true;return bytes("{\"ok\":true}");}
  public byte[] startSensorConfigured(int d,String a,String p,String w,String type,String m,String fmt){assertEquals(0,activeVisual);sensorStarts++;activeVisual=2;send=true;return bytes("{\"ok\":true}");}
  public void stopSensor(){activeVisual=0;}
  public byte[] pollSensor(){try{if(!send)return MapPacketTest.packet(new org.json.JSONObject().put("running",true),new byte[0]);send=false;if(activeVisual==1)return MapPacketTest.packet(MapPacketTest.meta(),new byte[]{-1,0,50,100});return MapPacketTest.packet(new org.json.JSONObject().put("running",true).put("kind","image").put("width",1).put("height",1).put("step",1).put("encoding","mono8").put("ageMs",0),new byte[]{127});}catch(Exception e){throw new AssertionError(e);}}
  public byte[] startLocationTf(int d,String a,String p,String dynamic,String fixed,String mode,String format){tfStarts++;return bytes("{\"ok\":true}");}
  public byte[] startDescription(int d,String a,String p,String w,boolean r,boolean t,String m,String fmt){descriptionStarts++;return bytes("{\"ok\":false}");}
  public byte[] pollTf(){try{return bytes(new org.json.JSONObject().put("running",true).put("rows",new org.json.JSONArray().put(new org.json.JSONObject().put("parent","map").put("child","base_link").put("writer","synthetic-tf-writer").put("static",false).put("stamp",++stamp).put("translation",new org.json.JSONArray().put(1.05).put(2.05).put(0)).put("rotation",new org.json.JSONArray().put(0).put(0).put(0).put(1)))).toString());}catch(Exception e){throw new AssertionError(e);}}
 }
 void decoded()throws Exception{LocationPanel p=(LocationPanel)tagged("location-page");java.lang.reflect.Field d=LocationPanel.class.getDeclaredField("decoder");d.setAccessible(true);((java.util.concurrent.ExecutorService)d.get(p)).submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);f.paint();}
 @Test public void localizationIsASeparateNativeDestinationWithoutStartingAnotherConnection(){
  int id=f.a.getResources().getIdentifier("nav_location","id",f.a.getPackageName());assertNotEquals("Native navigation must expose Localização",0,id);View nav=f.a.findViewById(id);assertNotNull(nav);int starts=f.bridge.starts;nav.performClick();f.paint();assertNotNull(tagged("location-page"));assertTrue(tagged("location-page").isShown());assertFalse(f.a.findViewById(R.id.section_monitor).isShown());assertEquals(starts,f.bridge.starts);
 }
 @Test public void locationExposesExplicitReadOnlyMapAndRobotFrameSelection(){
  f.a.findViewById(f.a.getResources().getIdentifier("nav_location","id",f.a.getPackageName())).performClick();
  assertNotNull("Location needs an explicit Observe action, not mock map content",tagged("location-observe"));
  assertNotNull(tagged("location-topic"));assertNotNull(tagged("location-robot-frame"));assertNotNull(tagged("location-surface"));
 }
 @Test public void applyingProfileRestoresItsMapAndFrameChoicesWithoutRetainingTheOtherRobotsSelection()throws Exception{
  f.a.findViewById(R.id.nav_location).performClick();((android.widget.EditText)tagged("location-topic")).setText("/robot_a/map");((android.widget.EditText)tagged("location-robot-frame")).setText("robot_a/base");tagged("location-observe").performClick();
  f.a.findViewById(R.id.profile_new).performClick();((android.widget.EditText)f.a.findViewById(R.id.profile_name)).setText("A");f.a.findViewById(R.id.profile_save).performClick();f.paint();
  ((android.widget.EditText)tagged("location-topic")).setText("/robot_b/map");((android.widget.EditText)tagged("location-robot-frame")).setText("robot_b/base");tagged("location-observe").performClick();f.a.findViewById(R.id.connect).performClick();f.paint();
  ((android.widget.Spinner)f.a.findViewById(R.id.profile_select)).setSelection(1);f.paint();f.a.findViewById(R.id.profile_apply).performClick();f.paint();
  assertEquals("Applying A must restore A's map topic","/robot_a/map",((android.widget.EditText)tagged("location-topic")).getText().toString());assertEquals("robot_a/base",((android.widget.EditText)tagged("location-robot-frame")).getText().toString());
 }
 @Test public void realActivityPollingRoutesMapAndStandalonePoseThenSwitchesTheSameVisualSlotBackToCamera()throws Exception{
  Maps api=new Maps();f.set("bridge",api);f.tick();f.paint();int starts=api.starts,subs=api.subscriptions;
  f.a.findViewById(R.id.nav_location).performClick();tagged("location-observe").performClick();f.work("tickSensor",new Class<?>[]{int.class},1);decoded();LocationPanel panel=(LocationPanel)tagged("location-page");assertNotNull("Actual decoder result reaches the actual map surface",panel.map());
  f.work("tickRobotPose",new Class<?>[]{int.class},1);f.paint();assertTrue(ConnectionActivityTest.textOf(tagged("location-pose")),ConnectionActivityTest.textOf(tagged("location-pose")).contains("1.050"));assertTrue(panel.surface.poseVisible());assertEquals(1,api.mapStarts);assertEquals(1,api.tfStarts);assertEquals("No URDF reader is necessary for localization",0,api.descriptionStarts);
  SensorPanel sensor=(SensorPanel)f.get("sensorPanel");sensor.select("rt/camera","sensor_msgs::msg::dds_::Image_");f.a.findViewById(R.id.nav_monitor).performClick();assertNull(panel.map());f.work("tickSensor",new Class<?>[]{int.class},1);assertEquals(1,api.sensorStarts);assertEquals(2,api.activeVisual);
  f.a.findViewById(R.id.nav_location).performClick();f.work("tickSensor",new Class<?>[]{int.class},1);decoded();assertEquals(2,api.mapStarts);assertEquals(1,api.activeVisual);assertEquals(starts,api.starts);assertEquals("Map must not consume a telemetry watch",subs,api.subscriptions);
 }
 @Test public void aDecodeQueuedBeforeLeavingLocalizationCannotRepaintTheNewPageEpoch()throws Exception{
  Maps api=new Maps();f.set("bridge",api);f.tick();f.a.findViewById(R.id.nav_location).performClick();tagged("location-observe").performClick();LocationPanel panel=(LocationPanel)tagged("location-page");java.lang.reflect.Field d=LocationPanel.class.getDeclaredField("decoder");d.setAccessible(true);java.util.concurrent.ExecutorService executor=(java.util.concurrent.ExecutorService)d.get(panel);java.util.concurrent.CountDownLatch blocked=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.Future<?> hold=executor.submit(()->{blocked.countDown();try{assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}});
  try{assertTrue(blocked.await(5,java.util.concurrent.TimeUnit.SECONDS));f.work("tickSensor",new Class<?>[]{int.class},1);f.a.findViewById(R.id.nav_monitor).performClick();}finally{release.countDown();}hold.get(5,java.util.concurrent.TimeUnit.SECONDS);decoded();assertNull(panel.map());assertFalse(panel.surface.poseVisible());
 }
 @Test public void returningFromLocalizationCannotAdmitACadPoseQueuedBeforeTheMetadataOwnerChanged()throws Exception{
  RobotContext context=(RobotContext)f.get("robotContext");RobotTfContextTest.Api api=new RobotTfContextTest.Api();context.observe(api,96,"127.0.0.1","","lan","jazzy",RobotTfContextTest.topics(),()->true);RobotContext.PoseSnapshot old=context.pollTf(api,100);assertNotNull(old);assertTrue(context.current(old));f.a.findViewById(R.id.nav_location).performClick();f.a.findViewById(R.id.nav_monitor).performClick();assertFalse("Old CAD poses must be fenced even after a rapid localization roundtrip",context.current(old));
 }
 @Test public void explorerMapSelectionWithAnInvalidFrameCannotCrashOrCreateAReader()throws Exception{
  ((android.widget.EditText)tagged("location-robot-frame")).setText("");org.json.JSONObject topic=new org.json.JSONObject().put("wireName","rt/map").put("wireType",MapGrid.TYPE);f.call("readFromExplorer",new Class<?>[]{org.json.JSONObject.class},topic);assertTrue(ConnectionActivityTest.textOf(tagged("location-status")).contains("inválido"));assertNull(((LocationPanel)tagged("location-page")).desired());
 }
 @Test public void fittingMapUpdatesTheFollowButtonState(){f.a.findViewById(R.id.nav_location).performClick();tagged("location-follow").performClick();assertTrue(tagged("location-follow").isSelected());tagged("location-fit").performClick();assertFalse("Follow toggle must describe the actual viewport state",tagged("location-follow").isSelected());}
 @Test public void selectingTheMapFrameAsRobotCannotInventAnIdentityPoseAtTheOrigin()throws Exception{Maps api=new Maps();f.set("bridge",api);f.tick();f.a.findViewById(R.id.nav_location).performClick();((android.widget.EditText)tagged("location-robot-frame")).setText("map");tagged("location-observe").performClick();f.work("tickSensor",new Class<?>[]{int.class},1);decoded();f.work("tickRobotPose",new Class<?>[]{int.class},1);f.paint();assertFalse("Map frame identity is not an observed robot pose",((LocationPanel)tagged("location-page")).surface.poseVisible());}
}
