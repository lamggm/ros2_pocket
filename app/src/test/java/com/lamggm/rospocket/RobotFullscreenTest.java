package com.lamggm.rospocket;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import android.app.Dialog;import android.view.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class RobotFullscreenTest {
 RobotDashboardTest f=new RobotDashboardTest();
 @Before public void setup()throws Exception{f.setup();f.tick(RobotDashboardTest.topics("/robot/robot_description"));}
 @After public void cleanup()throws Exception{f.cleanup();}
 @Test public void sameModelMovesToFullscreenAndBackWithoutReconnectingOrResettingCamera()throws Exception{
  View root=f.f.a.getWindow().getDecorView();RobotModelView model=root.findViewWithTag("robot-model");ViewGroup parent=(ViewGroup)model.getParent();model.yaw=1.2;model.pitch=.8;
  View open=root.findViewWithTag("robot-fullscreen");assertNotNull("Model needs fullscreen action",open);int starts=f.d.descriptionStarts;assertTrue(open.performClick());Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());assertSame(model,dialog.findViewById(android.R.id.content).findViewWithTag("robot-model"));assertEquals(1.2,model.yaw,0);assertEquals(.8,model.pitch,0);
  assertTrue(dialog.findViewById(android.R.id.content).findViewWithTag("robot-exit-fullscreen").performClick());f.f.paint();assertFalse(dialog.isShowing());assertSame(parent,model.getParent());assertEquals(1.2,model.yaw,0);assertEquals(starts,f.d.descriptionStarts);
 }
 @Test public void disconnectClosesFullscreenAndCannotLeaveStaleRobotVisible()throws Exception{
  View open=f.f.a.getWindow().getDecorView().findViewWithTag("robot-fullscreen");assertNotNull(open);open.performClick();Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());f.f.call("disconnect",new Class<?>[]{});f.f.paint();assertFalse(dialog.isShowing());RobotModelView model=f.f.a.getWindow().getDecorView().findViewWithTag("robot-model");assertNotNull(model);assertFalse(model.frameScheduled);
 }
}
