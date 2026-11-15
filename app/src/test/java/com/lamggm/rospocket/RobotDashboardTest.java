package com.lamggm.rospocket;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.json.*;
import android.widget.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class RobotDashboardTest {
 final ConnectionActivityTest f=new ConnectionActivityTest();Fake d;
 static JSONArray topics(String... names)throws Exception{JSONArray a=new JSONArray();for(String name:names)a.put(new JSONObject().put("name",name).put("wireName","rt"+name).put("wireType","std_msgs::msg::dds_::String_").put("publishers",1).put("endpoints",new JSONArray().put(new JSONObject().put("writer",true).put("id",name+"_gid").put("qos",new JSONObject().put("reliability","reliable").put("durability","transient_local")))));return a;}
 static class Fake extends ConnectionActivityTest.Fake {
  int descriptionStarts,descriptionStops;long sequence=1;String xml=RobotDescriptionTest.SMALL;boolean retained;
  public byte[] startDescription(int domain,String address,String peer,String topic,boolean reliable,boolean transientLocal,String mode,String format){descriptionStarts++;retained=reliable&&transientLocal;return bytes("{\"ok\":true}");}
  public byte[] pollDescription(){try{return bytes(new JSONObject().put("running",true).put("sequence",sequence).put("matched",1).put("xml",xml).put("error","").toString());}catch(Exception e){throw new AssertionError(e);}}
  public void stopDescription(){descriptionStops++;}
 }
 @Before public void setup()throws Exception{f.setup();d=new Fake();f.set("bridge",d);}
 @After public void cleanup()throws Exception{f.cleanup();}
 void tick(JSONArray topics)throws Exception{d.snapshot=new JSONObject(d.snapshot).put("topics",topics).toString();f.set("lastUi",0L);f.tick();f.paint();}
 @Test public void actualActivityReplacesPanelTitleAndDrawsDedicatedDescriptionWithoutReplacingWatches()throws Exception{
  tick(topics("/robot/robot_description"));TextView title=f.a.getWindow().getDecorView().findViewWithTag("dashboard-title");assertNotNull("Dedicated dashboard title must exist",title);assertEquals("robo_real",title.getText().toString());assertEquals(1,d.descriptionStarts);assertTrue(d.retained);assertEquals(1,d.subscriptions);android.view.View model=f.a.getWindow().getDecorView().findViewWithTag("robot-model");assertNotNull(model);tick(topics("/robot/robot_description"));assertEquals(1,d.descriptionStarts);UiTheme.save(f.a,"light");f.a.applyAppearance();assertSame(model,f.a.getWindow().getDecorView().findViewWithTag("robot-model"));assertEquals(1,d.descriptionStarts);
  d.sequence++;d.xml="<robot name='broken'>";tick(topics("/robot/robot_description"));assertEquals("Seu painel",title.getText().toString());assertTrue(ConnectionActivityTest.textOf(f.a.findViewById(R.id.section_monitor)).contains("inválido"));
 }
 @Test public void identicalDescriptionRepublicationPreservesZoomAndPreparedGeometry()throws Exception{tick(topics("/robot/robot_description"));RobotModelView view=f.a.getWindow().getDecorView().findViewWithTag("robot-model");java.lang.reflect.Field zoom=RobotModelView.class.getDeclaredField("zoom"),model=RobotModelView.class.getDeclaredField("model");zoom.setAccessible(true);model.setAccessible(true);Object prepared=model.get(view);zoom.setFloat(view,1.8f);d.sequence++;tick(topics("/robot/robot_description"));assertEquals("Republished identical XML must not erase pinch",1.8f,zoom.getFloat(view),1e-6);assertSame("Geometry preparation must be reused for identical bytes",prepared,model.get(view));}
 @Test public void changedDescriptionStillRebuildsAndResetsZoom()throws Exception{tick(topics("/robot/robot_description"));RobotModelView view=f.a.getWindow().getDecorView().findViewWithTag("robot-model");java.lang.reflect.Field zoom=RobotModelView.class.getDeclaredField("zoom"),model=RobotModelView.class.getDeclaredField("model");zoom.setAccessible(true);model.setAccessible(true);Object previous=model.get(view);zoom.setFloat(view,1.8f);d.sequence++;d.xml=d.xml.replace("robo_real","changed_robot");tick(topics("/robot/robot_description"));assertEquals(1,zoom.getFloat(view),1e-6);assertNotSame(previous,model.get(view));assertEquals("changed_robot",((TextView)f.a.getWindow().getDecorView().findViewWithTag("dashboard-title")).getText().toString());}
 @Test public void explicitDisconnectImmediatelyClearsModelTitleAndStopsAnimation()throws Exception{tick(topics("/robot/robot_description"));f.call("disconnect",new Class<?>[]{});TextView title=f.a.getWindow().getDecorView().findViewWithTag("dashboard-title");assertEquals("Seu painel",title.getText().toString());RobotModelView view=f.a.getWindow().getDecorView().findViewWithTag("robot-model");assertFalse(view.frameScheduled);}
 @Test public void rendererBudgetFailureRemainsExplicitOnRepeatedSnapshots()throws Exception{d.xml="<robot name='large'><link name='base'>"+"<visual><geometry><sphere radius='1'/></geometry></visual>".repeat(80)+"</link></robot>";tick(topics("/robot/robot_description"));TextView status=f.a.getWindow().getDecorView().findViewWithTag("robot-status");assertTrue(status.getText().toString().contains("8192"));tick(topics("/robot/robot_description"));assertTrue("Repeated metadata must not erase the render failure",status.getText().toString().contains("8192"));}
 @Test public void oldSourceChooserCannotRestoreASelectionAfterDisconnect()throws Exception{tick(topics("/first/robot_description","/second/robot_description"));((Button)f.a.getWindow().getDecorView().findViewWithTag("robot-source")).performClick();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());f.call("disconnect",new Class<?>[]{});ListView list=dialog.getListView();list.performItemClick(list.getAdapter().getView(1,null,list),1,1);f.paint();TextView status=f.a.getWindow().getDecorView().findViewWithTag("robot-status");assertTrue(status.getText().toString().startsWith("Desconectado"));}
 @Test public void multipleDescriptionTopicsAreNotArbitrarilyMixed()throws Exception{tick(topics("/first/robot_description","/second/robot_description"));assertEquals(0,d.descriptionStarts);assertTrue(ConnectionActivityTest.textOf(f.a.findViewById(R.id.section_monitor)).contains("Selecione"));}
}
