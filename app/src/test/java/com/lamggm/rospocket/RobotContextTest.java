package com.lamggm.rospocket;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.json.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotContextTest {
 @Test public void explicitSourceSelectionDisambiguatesRobotsWithoutMixingThem()throws Exception{RobotContext c=new RobotContext();RobotDashboardTest.Fake d=new RobotDashboardTest.Fake();JSONArray topics=RobotDashboardTest.topics("/a/robot_description","/b/robot_description");assertNull(c.observe(d,78,"127.0.0.1","","lan","jazzy",topics,()->true).model);assertEquals(0,d.descriptionStarts);c.select("rt/b/robot_description");RobotContext.Snapshot selected=c.observe(d,78,"127.0.0.1","","lan","jazzy",topics,()->true);assertNotNull(selected.model);assertTrue(selected.source.startsWith("/b/robot_description"));assertEquals(1,d.descriptionStarts);c.select("");assertFalse(c.current(selected));}
 @Test public void stopDuringBlockingAcquisitionCannotStartPollingOrRestoreTheModel()throws Exception{RobotContext c=new RobotContext();AtomicBoolean live=new AtomicBoolean(true);int[] descriptionPolls={0};RobotDashboardTest.Fake d=new RobotDashboardTest.Fake(){@Override public byte[] startDescription(int a,String b,String p,String w,boolean r,boolean l,String m,String f){live.set(false);return bytes("{\"ok\":true}");}@Override public byte[] pollDescription(){descriptionPolls[0]++;return super.pollDescription();}};RobotContext.Snapshot s=c.observe(d,78,"127.0.0.1","","lan","jazzy",RobotDashboardTest.topics("/a/robot_description"),live::get);assertNull(s.model);assertEquals(0,descriptionPolls[0]);assertEquals(2,d.descriptionStops);}
 @Test public void multipleWritersOnOneDescriptionAreExplicitlyAmbiguous()throws Exception{RobotContext c=new RobotContext();RobotDashboardTest.Fake d=new RobotDashboardTest.Fake();JSONArray topics=RobotDashboardTest.topics("/a/robot_description");topics.getJSONObject(0).put("publishers",2);RobotContext.Snapshot s=c.observe(d,78,"127.0.0.1","","lan","jazzy",topics,()->true);assertNull(s.model);assertTrue(s.status.contains("ambígua"));assertEquals(0,d.descriptionStarts);}
}
