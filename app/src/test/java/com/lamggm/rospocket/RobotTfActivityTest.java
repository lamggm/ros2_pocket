package com.lamggm.rospocket;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import android.widget.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class RobotTfActivityTest {
 final RobotDashboardTest f=new RobotDashboardTest();RobotTfContextTest.Api api;
 @Before public void setup()throws Exception{f.setup();api=new RobotTfContextTest.Api();f.d=api;f.f.set("bridge",api);}
 @After public void cleanup()throws Exception{f.cleanup();}
 @Test public void actualPoseTickerUpdatesUiAndModeWithoutStealingWatchOrRebuildingDescription()throws Exception{f.tick(RobotTfContextTest.topics());int generation=(Integer)f.f.get("generation");f.f.call("tickRobotPose",new Class<?>[]{int.class},generation);f.f.paint();assertTrue(api.polls>0);assertEquals(1,api.starts);RobotPanel panel=(RobotPanel)f.f.get("robotPanel");java.lang.reflect.Field assets=RobotPanel.class.getDeclaredField("assets");assets.setAccessible(true);assets.set(panel,RobotAssetBundle.decode(RobotFullBundleTest.bundle("full")));Button tf=f.f.a.getWindow().getDecorView().findViewWithTag("robot-tf-mode");assertTrue(tf.performClick());TextView status=f.f.a.getWindow().getDecorView().findViewWithTag("robot-pose-status");assertTrue(status.getText().toString(),status.getText().toString().contains("TF observado"));assertEquals(1,api.descriptionStarts);assertEquals(1,api.subscriptions);f.f.call("disconnect",new Class<?>[]{});assertFalse("Disconnect must disable TF mode",tf.isEnabled());assertFalse(status.getText().toString().contains("TF observado"));int before=api.polls;f.f.call("tickRobotPose",new Class<?>[]{int.class},generation);f.f.paint();assertEquals(before,api.polls);}
}
