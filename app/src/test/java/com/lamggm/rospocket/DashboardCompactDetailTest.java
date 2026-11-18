package com.lamggm.rospocket;
import android.app.Dialog;
import android.view.View;
import android.view.Gravity;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class DashboardCompactDetailTest {
 private final ConnectionActivityTest f=new ConnectionActivityTest();
 @Before public void start()throws Exception{f.setup();}
 @After public void stop()throws Exception{f.cleanup();}
 @Test public void roomyCardKeepsPrimaryControlsAndStillOffersEnlargement()throws Exception{
  f.controller.visible();f.a.findViewById(R.id.nav_monitor).performClick();f.paint();
  DashboardBoard b=f.a.getWindow().getDecorView().findViewWithTag("dashboard-board");
  b.bind(b.layout().resize("plot",4,4),null);int width=1200;
  b.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));b.layout(0,0,width,b.getMeasuredHeight());
  assertTrue(b.findViewWithTag("plot-units").isShown());assertTrue(b.findViewWithTag("preview-open:plot").isShown());
 }
 @Test public void smallCardsOpenCenteredDetailsAndReturnTheSameLiveView()throws Exception{
  f.controller.visible();f.a.findViewById(R.id.nav_monitor).performClick();f.paint();
  DashboardBoard b=f.a.findViewById(android.R.id.content).findViewWithTag("dashboard-board");
  String[] ids={"plot","sensors","camera","robot"}, panels={"live-plot","sensor-panel","camera-panel","design-surface"}, controls={"plot-units","sensor-resize","sensor-resize","robot-options"};
  DashboardLayout layout=b.layout().rename("plot","Velocidades X/Y");for(String id:ids)layout=layout.resize(id,2,2);b.bind(layout,null);
  b.measure(View.MeasureSpec.makeMeasureSpec(520,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));b.layout(0,0,520,b.getMeasuredHeight());
  assertNull("Visual title must not occupy a row outside the card",b.findViewWithTag("card-title:plot"));
  for(int i=0;i<ids.length;i++){
   View panel=b.findViewWithTag(panels[i]);assertNotNull(panel);View control=panel.findViewWithTag(controls[i]);assertFalse(control.isShown());
   View preview=b.findViewWithTag("preview-open:"+ids[i]);assertTrue("Visible preview "+ids[i],preview.isShown());assertEquals(preview.getHeight(),panel.getHeight());assertTrue(preview.getHeight()>0);
   preview.performClick();f.paint();Dialog d=ShadowDialog.getLatestDialog();assertTrue("Detail dialog for "+ids[i],d.isShowing());assertEquals(Gravity.CENTER,d.getWindow().getAttributes().gravity);assertSame(panel,d.getWindow().getDecorView().findViewWithTag(panels[i]));assertTrue("Detailed controls for "+ids[i],control.isShown());
   if(i==0)assertEquals("Velocidades X/Y",((TextView)d.getWindow().getDecorView().findViewWithTag("preview-title")).getText().toString());
   d.getWindow().getDecorView().findViewWithTag("preview-close").performClick();f.paint();assertFalse(d.isShowing());assertSame(panel,b.findViewWithTag(panels[i]));assertFalse(control.isShown());
  }
 }
}
