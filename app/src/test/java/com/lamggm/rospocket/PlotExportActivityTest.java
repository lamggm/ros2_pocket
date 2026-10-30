package com.lamggm.rospocket;
import android.app.Activity;
import android.content.Intent;
import android.widget.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class PlotExportActivityTest {
 @Test public void actualExportButtonOpensAndroidCreateDocumentForCsv()throws Exception {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
  a.findViewById(R.id.nav_monitor).performClick();
  java.lang.reflect.Field f=MainActivity.class.getDeclaredField("plots");f.setAccessible(true);PlotController c=(PlotController)f.get(a);
  c.acceptNative(PlotControllerTest.sample(1,100),1,false);c.select("/value","data");c.acceptNative(PlotControllerTest.sample(2,200),1,false);
  Button export=a.findViewById(android.R.id.content).findViewWithTag("plot-export");assertNotNull("Monitor needs a user CSV export button",export);assertNotEquals("Physical export control needs a stable Android ID",android.view.View.NO_ID,export.getId());
  export.performClick();org.robolectric.shadows.ShadowActivity.IntentForResult intent=Shadows.shadowOf(a).getNextStartedActivityForResult();
  assertNotNull(intent);assertEquals(Intent.ACTION_CREATE_DOCUMENT,intent.intent.getAction());assertEquals("text/csv",intent.intent.getType());assertTrue(intent.intent.hasCategory(Intent.CATEGORY_OPENABLE));
 }

 @Test public void pickerResultWritesTheFrozenPausedCopyOffTheUiThread()throws Exception {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();java.lang.reflect.Field f=MainActivity.class.getDeclaredField("plots");f.setAccessible(true);PlotController c=(PlotController)f.get(a);
  c.acceptNative(PlotControllerTest.sample(1,100),1,false);c.select("/value","data");c.acceptNative(PlotControllerTest.sample(2,200),1,false);
  PlotView plot=a.findViewById(R.id.plot_panel);plot.refresh();a.findViewById(R.id.plot_pause).performClick();
  a.findViewById(android.R.id.content).findViewWithTag("plot-export").performClick();
  c.acceptNative(PlotControllerTest.sample(3,300),1,false);c.reset(2);plot.refresh();
  java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();java.util.concurrent.atomic.AtomicBoolean onMain=new java.util.concurrent.atomic.AtomicBoolean();
  java.io.OutputStream output=new java.io.FilterOutputStream(bytes){@Override public void write(byte[] b,int o,int n)throws java.io.IOException{onMain.set(android.os.Looper.myLooper()==android.os.Looper.getMainLooper());out.write(b,o,n);}};
  android.net.Uri uri=android.net.Uri.parse("content://csv-test/frozen");Shadows.shadowOf(a.getContentResolver()).registerOutputStream(uri,output);
  deliver(a,Activity.RESULT_OK,new Intent().setData(uri));
  long until=System.nanoTime()+5_000_000_000L;TextView status=a.findViewById(android.R.id.content).findViewWithTag("plot-export-status");
  while(System.nanoTime()<until&&!status.getText().toString().startsWith("CSV salvo")){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
  assertTrue("Provider must receive CSV",status.getText().toString().startsWith("CSV salvo"));assertFalse("No provider I/O on UI",onMain.get());
  String csv=bytes.toString("UTF-8");assertTrue(csv.contains("latest_snapshot,paused"));assertTrue(csv.contains(",0,200,1000,42.5,finite,true"));assertFalse(csv.contains(",100,200,"));
 }
 static void deliver(MainActivity a,int result,Intent data)throws Exception {
  java.lang.reflect.Method m=Activity.class.getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);m.setAccessible(true);m.invoke(a,5005,result,data);
 }

 static MainActivity ready()throws Exception {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();java.lang.reflect.Field f=MainActivity.class.getDeclaredField("plots");f.setAccessible(true);PlotController c=(PlotController)f.get(a);
  c.acceptNative(PlotControllerTest.sample(1,100),1,false);c.select("/value","data");c.acceptNative(PlotControllerTest.sample(2,200),1,false);return a;
 }
 static Button button(MainActivity a){return a.findViewById(android.R.id.content).findViewWithTag("plot-export");}
 static TextView status(MainActivity a){return a.findViewById(android.R.id.content).findViewWithTag("plot-export-status");}
 @Test public void emptyExportDoesNotOpenPickerAndCancellationAllowsAnotherRequest()throws Exception {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();button(a).performClick();assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());assertTrue(button(a).isEnabled());
  a=ready();button(a).performClick();assertFalse(button(a).isEnabled());deliver(a,Activity.RESULT_CANCELED,null);assertTrue(button(a).isEnabled());assertEquals("Exportação cancelada.",status(a).getText().toString());button(a).performClick();assertFalse(button(a).isEnabled());deliver(a,Activity.RESULT_CANCELED,null);
 }
 @Test public void missingSnapshotAndFileUriNeverWriteArbitraryFiles()throws Exception {
  MainActivity a=ready();deliver(a,Activity.RESULT_OK,new Intent().setData(android.net.Uri.parse("content://csv-test/new-activity")));assertTrue(status(a).getText().toString().contains("recriação"));
  button(a).performClick();deliver(a,Activity.RESULT_OK,new Intent().setData(android.net.Uri.parse("file:///data/forbidden.csv")));assertTrue(status(a).getText().toString().contains("Destino inválido"));assertTrue(button(a).isEnabled());
 }
 @Test public void writeFailureClosesProviderAndKeepsUiUsable()throws Exception {
  MainActivity a=ready();button(a).performClick();java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
  java.io.OutputStream broken=new java.io.OutputStream(){public void write(int b)throws java.io.IOException{throw new java.io.IOException("synthetic disk full");}public void close(){closed.set(true);}};
  android.net.Uri uri=android.net.Uri.parse("content://csv-test/error");Shadows.shadowOf(a.getContentResolver()).registerOutputStream(uri,broken);deliver(a,Activity.RESULT_OK,new Intent().setData(uri));
  long until=System.nanoTime()+5_000_000_000L;while(System.nanoTime()<until&&!button(a).isEnabled()){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10);}
  assertTrue(closed.get());assertTrue(button(a).isEnabled());assertTrue(status(a).getText().toString().startsWith("Falha ao salvar"));
 }
 @Test public void destroyedActivityDoesNotAdmitPickerResultOrMutateTheNewScreen()throws Exception {
  org.robolectric.android.controller.ActivityController<MainActivity> lifecycle=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=lifecycle.get();
  java.lang.reflect.Field f=MainActivity.class.getDeclaredField("plots");f.setAccessible(true);PlotController c=(PlotController)f.get(a);c.acceptNative(PlotControllerTest.sample(1,100),1,false);c.select("/value","data");c.acceptNative(PlotControllerTest.sample(2,200),1,false);button(a).performClick();
  lifecycle.destroy();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();android.net.Uri uri=android.net.Uri.parse("content://csv-test/closed");Shadows.shadowOf(a.getContentResolver()).registerOutputStream(uri,out);deliver(a,Activity.RESULT_OK,new Intent().setData(uri));Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(0,out.size());
 }
}
