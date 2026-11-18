package com.lamggm.rospocket;
import android.app.*;import android.view.*;import android.widget.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class CardNameActivityTest {
 final ConnectionActivityTest f=new ConnectionActivityTest();@Before public void setup()throws Exception{f.setup();f.bridge.snapshot=new org.json.JSONObject(f.bridge.snapshot).put("numericFields",new org.json.JSONArray().put(new org.json.JSONObject().put("path","data").put("value",42.5).put("state","finite"))).toString();f.tick();f.paint();}@After public void cleanup()throws Exception{f.cleanup();}
 DashboardBoard b(){return f.a.getWindow().getDecorView().findViewWithTag("dashboard-board");}
 AlertDialog open(String id){b().edit();b().findViewWithTag("layout-options:"+id).performClick();AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();ListView list=d.getListView();int at=-1;for(int i=0;i<list.getCount();i++)if(list.getItemAtPosition(i).toString().equals("Nome do card…"))at=i;assertTrue("Card menu must offer a manual name",at>=0);list.performItemClick(list.getAdapter().getView(at,null,list),at,at);f.paint();return ShadowAlertDialog.getLatestAlertDialog();}
 EditText field(AlertDialog d){return d.findViewById(android.R.id.content).findViewWithTag("card-name");}
 @Test public void manualNameSavesThroughActualViewsWithoutLosingTopicOrSample()throws Exception{AlertDialog d=open("watch0");field(d).setText("Velocidade");d.getButton(-1).performClick();f.paint();assertEquals("Velocidade",b().layout().get("watch0").name);assertTrue(b().save());MetricCard card=b().findViewWithTag("watch-card:0");assertTrue(card.getText().toString(),card.getText().toString().contains("Velocidade"));assertTrue(card.getContentDescription().toString().contains((String)f.get("selectedWire")));assertTrue("Renaming must preserve the current sample",card.getText().toString().contains("42.5"));assertEquals("Velocidade",new DashboardStore(f.a.getFilesDir()).load().get("watch0").name);}
 @Test public void invalidNameCancelAndOldDialogCannotRenameANewOwner()throws Exception{AlertDialog d=open("watch0");field(d).setText("x".repeat(65));d.getButton(-1).performClick();f.paint();assertTrue(d.isShowing());assertEquals("",b().layout().get("watch0").name);field(d).setText("Não salvar");d.getButton(-2).performClick();f.paint();assertEquals("",b().layout().get("watch0").name);d=open("watch0");field(d).setText("Atrasado");b().bind(DashboardLayout.defaults().rename("watch0","Novo perfil"),null);b().edit();d.getButton(-1).performClick();f.paint();assertEquals("Novo perfil",b().layout().get("watch0").name);}
 @Test public void renamingKeepsTheTypedGaugeValueAndAccessibleInstrumentDetails()throws Exception{b().bind(DashboardLayout.defaults().display("watch0",new DashboardLayout.Display("gauge","data",0,100,"V")),null);f.call("refreshDashboard",new Class<?>[]{});MetricCard card=b().findViewWithTag("watch-card:0");assertTrue(card.getContentDescription().toString().contains("42.500"));AlertDialog d=open("watch0");field(d).setText("Velocidade");d.getButton(-1).performClick();f.paint();String label=card.getContentDescription().toString();assertTrue(label,label.contains("Velocidade")&&label.contains("gauge")&&label.contains("42.500"));}
 @Test public void plotTitleIsEditableAndSavedByTheExistingDashboardPencil()throws Exception{
  f.controller.visible();f.a.findViewById(R.id.nav_monitor).performClick();AlertDialog d=open("plot");field(d).setText("Velocidades X/Y");d.getButton(-1).performClick();f.paint();assertTrue(b().save());
  assertEquals("Velocidades X/Y",new DashboardStore(f.a.getFilesDir()).load().get("plot").name);
  assertTrue(b().findViewById(R.id.plot_chart).getContentDescription().toString().startsWith("Velocidades X/Y"));
 }
 @Test public void visualCardAliasAppearsInDetailsAndClearingRestoresDefault()throws Exception{
  f.controller.visible();f.a.findViewById(R.id.nav_monitor).performClick();
  for(String name:new String[]{"Vista frontal",""}){
   AlertDialog d=open("robot");field(d).setText(name);d.getButton(-1).performClick();f.paint();assertTrue(b().save());
   View preview=b().findViewWithTag("preview-open:robot");String label=name.isEmpty()?"Modelo 3d":name;assertEquals("Ampliar · "+label,preview.getContentDescription());assertNull(b().findViewWithTag("card-title:robot"));
   preview.performClick();f.paint();Dialog detail=ShadowDialog.getLatestDialog();TextView title=detail.getWindow().getDecorView().findViewWithTag("preview-title");assertEquals(label,title.getText().toString());detail.dismiss();f.paint();
  }
 }
}
