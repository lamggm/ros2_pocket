package com.lamggm.rospocket;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class ProfileTransferActivityTest {
 static MainActivity ready() {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
  ((EditText)a.findViewById(R.id.profile_name)).setText("Portátil");
  SensorPanel sensor=a.getWindow().getDecorView().findViewWithTag("sensor-panel");sensor.select("rt/bench/scan","sensor_msgs::msg::dds_::LaserScan_");
  a.findViewById(R.id.profile_save).performClick();return a;
 }
 static View control(MainActivity a,String name){int id=a.getResources().getIdentifier(name,"id",a.getPackageName());assertNotEquals("Stable portable-profile control: "+name,0,id);View v=a.findViewById(id);assertNotNull(v);return v;}
 static String status(MainActivity a){return ((TextView)control(a,"profile_transfer_status")).getText().toString();}
 static void paint(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 static void deliver(MainActivity a,int request,int code,Intent data)throws Exception{java.lang.reflect.Method m=Activity.class.getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class);m.setAccessible(true);m.invoke(a,request,code,data);}
 static org.robolectric.shadows.ShadowActivity.IntentForResult export(MainActivity a){
  control(a,"profile_export").performClick();AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing());d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();paint();return Shadows.shadowOf(a).getNextStartedActivityForResult();
 }
 static void await(MainActivity a,String prefix)throws Exception{
  long end=System.nanoTime()+5_000_000_000L;
  while(System.nanoTime()<end&&!status(a).startsWith(prefix)){paint();Thread.sleep(10);}
  assertTrue(status(a),status(a).startsWith(prefix));
 }
 @Test public void exportRequiresPrivacyAcknowledgementBeforeOpeningCreateDocument()throws Exception {
  MainActivity a=ready();control(a,"profile_export").performClick();AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);assertTrue(((TextView)d.findViewById(android.R.id.message)).getText().toString().contains("peer/IP"));assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());
  d.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();paint();assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());
  var picker=export(a);assertNotNull(picker);assertEquals(Intent.ACTION_CREATE_DOCUMENT,picker.intent.getAction());assertEquals("application/json",picker.intent.getType());assertTrue(picker.intent.hasCategory(Intent.CATEGORY_OPENABLE));deliver(a,picker.requestCode,Activity.RESULT_CANCELED,null);
 }
 @Test public void exportedSelectedProfileIsFrozenAndWrittenOffTheUiThread()throws Exception {
  MainActivity a=ready();var picker=export(a);assertNotNull(picker);
  ((EditText)a.findViewById(R.id.domain)).setText("88");a.findViewById(R.id.profile_save).performClick();
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();AtomicBoolean onMain=new AtomicBoolean();Uri uri=Uri.parse("content://profile-test/export");
  OutputStream output=new FilterOutputStream(bytes){@Override public void write(byte[] b,int o,int n)throws IOException{onMain.set(Looper.myLooper()==Looper.getMainLooper());out.write(b,o,n);}};
  Shadows.shadowOf(a.getContentResolver()).registerOutputStream(uri,output);deliver(a,picker.requestCode,Activity.RESULT_OK,new Intent().setData(uri));await(a,"Perfil exportado");
  JSONObject envelope=new JSONObject(bytes.toString("UTF-8"));assertEquals("ros-pocket-profile",envelope.getString("format"));assertEquals(2,envelope.getInt("schema"));JSONObject profile=envelope.getJSONObject("profile");assertEquals(0,profile.getInt("domain"));assertEquals("rt/bench/scan",profile.getJSONObject("visual").getJSONObject("scan").getString("wire"));assertFalse(onMain.get());
  assertEquals(3,envelope.length());assertFalse(profile.has("samples"));assertFalse(profile.has("sessionGeneration"));assertNull(new ProfileStore(new File(a.getFilesDir(),"bench-profiles-v1.json")).load().applied);
 }
 static org.robolectric.shadows.ShadowActivity.IntentForResult beginImport(MainActivity a){control(a,"profile_import").performClick();var picker=Shadows.shadowOf(a).getNextStartedActivityForResult();assertNotNull(picker);assertEquals(Intent.ACTION_OPEN_DOCUMENT,picker.intent.getAction());assertEquals("application/json",picker.intent.getType());return picker;}
 static void importBytes(MainActivity a,byte[] bytes)throws Exception{var picker=beginImport(a);Uri uri=Uri.parse("content://profile-test/import");Shadows.shadowOf(a.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(bytes));deliver(a,picker.requestCode,Activity.RESULT_OK,new Intent().setData(uri));}
 static BenchProfiles book(MainActivity a)throws Exception{return new ProfileStore(new File(a.getFilesDir(),"bench-profiles-v1.json")).load();}
 static byte[] catalogBytes(MainActivity a)throws Exception{return java.nio.file.Files.readAllBytes(new File(a.getFilesDir(),"bench-profiles-v1.json").toPath());}
 @Test public void validImportNeedsConfirmationAndAddsWithoutApplyingOrConnecting()throws Exception {
  MainActivity a=ready();a.findViewById(R.id.profile_apply).performClick();String applied=book(a).applied.id;a.findViewById(R.id.connect).performClick();
  BenchProfiles.Profile received=BenchProfiles.decode(PortableProfilesTest.schemaTwo(PortableProfilesTest.visual())).profiles.get(0).rename("Recebido");byte[] bytes=BenchProfiles.exportProfile(received);
  importBytes(a,bytes);await(a,"Perfil lido");AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertEquals(1,book(a).profiles.size());d.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();paint();assertEquals(1,book(a).profiles.size());
  importBytes(a,bytes);await(a,"Perfil lido");d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();paint();
  BenchProfiles out=book(a);assertEquals(2,out.profiles.size());BenchProfiles.Profile imported=out.profiles.get(1);assertNotEquals(received.id,imported.id);assertEquals("Recebido",imported.name);assertEquals("camera",imported.visual.mode);assertEquals(received.watches.get(3).wire,imported.watches.get(3).wire);assertEquals(applied,out.applied.id);assertEquals("0",((EditText)a.findViewById(R.id.domain)).getText().toString());assertEquals("Desconectar",((Button)a.findViewById(R.id.connect)).getText().toString());
 }
 @Test public void duplicateImportNameNeverOverwritesAndRequiresExplicitNewName()throws Exception {
  MainActivity a=ready();BenchProfiles.Profile old=book(a).profiles.get(0);byte[] before=catalogBytes(a);importBytes(a,BenchProfiles.exportProfile(old));await(a,"Perfil lido");
  AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();paint();assertTrue(d.isShowing());assertArrayEquals(before,catalogBytes(a));EditText name=d.findViewById(a.getResources().getIdentifier("profile_import_name","id",a.getPackageName()));assertNotNull(name.getError());
  name.setText("Outra bancada");d.getButton(DialogInterface.BUTTON_POSITIVE).performClick();paint();assertFalse(d.isShowing());assertEquals(2,book(a).profiles.size());assertEquals(old.id,book(a).profiles.get(0).id);assertNotEquals(old.id,book(a).profiles.get(1).id);
 }
 @Test public void malformedOversizeUtf8AndReadFailureKeepCatalogAndCloseTheProvider()throws Exception {
  MainActivity a=ready();byte[] before=catalogBytes(a);
  for(byte[] bytes:new byte[][]{"{broken".getBytes(StandardCharsets.UTF_8),new byte[BenchProfiles.MAX_BYTES+1],new byte[]{(byte)0xc3,0x28}}){importBytes(a,bytes);await(a,"Falha");assertArrayEquals(before,catalogBytes(a));}
  var picker=beginImport(a);AtomicBoolean closed=new AtomicBoolean(),onMain=new AtomicBoolean();Uri uri=Uri.parse("content://profile-test/read-fail");InputStream broken=new InputStream(){public int read()throws IOException{onMain.set(Looper.myLooper()==Looper.getMainLooper());throw new IOException("synthetic read failure");}public void close(){closed.set(true);}};
  Shadows.shadowOf(a.getContentResolver()).registerInputStream(uri,broken);deliver(a,picker.requestCode,Activity.RESULT_OK,new Intent().setData(uri));await(a,"Falha");assertTrue(closed.get());assertFalse(onMain.get());assertArrayEquals(before,catalogBytes(a));
 }
 @Test public void destroyedImportOwnerCannotShowConfirmationOrChangeANewActivity()throws Exception {
  var lifecycle=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=lifecycle.get();((EditText)a.findViewById(R.id.profile_name)).setText("Original");a.findViewById(R.id.profile_save).performClick();byte[] before=catalogBytes(a);
  byte[] bytes=BenchProfiles.exportProfile(book(a).profiles.get(0));java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);AtomicBoolean closed=new AtomicBoolean();
  InputStream slow=new FilterInputStream(new ByteArrayInputStream(bytes)){
   public int read(byte[] b,int off,int len)throws IOException{entered.countDown();try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("release timeout");}catch(InterruptedException ex){throw new IOException(ex);}return super.read(b,off,len);}
   public void close()throws IOException{closed.set(true);super.close();}
  };
  var picker=beginImport(a);Uri uri=Uri.parse("content://profile-test/slow");Shadows.shadowOf(a.getContentResolver()).registerInputStream(uri,slow);deliver(a,picker.requestCode,Activity.RESULT_OK,new Intent().setData(uri));
  try{
   assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS));lifecycle.destroy();MainActivity next=Robolectric.buildActivity(MainActivity.class).create().get();release.countDown();long until=System.nanoTime()+5_000_000_000L;
   while(System.nanoTime()<until&&!closed.get()){paint();Thread.sleep(10);}paint();assertTrue(closed.get());assertArrayEquals(before,catalogBytes(next));assertEquals(1,book(next).profiles.size());assertFalse(status(next).startsWith("Perfil lido"));
  }finally{release.countDown();}
 }
 @Test public void pickerCancellationAndFileUriImportPreserveTheExistingCatalog()throws Exception {
  MainActivity a=ready();byte[] before=catalogBytes(a);var picker=beginImport(a);deliver(a,picker.requestCode,Activity.RESULT_CANCELED,null);assertArrayEquals(before,catalogBytes(a));assertTrue(control(a,"profile_import").isEnabled());
  picker=beginImport(a);deliver(a,picker.requestCode,Activity.RESULT_OK,new Intent().setData(Uri.parse("file:///forbidden.json")));assertTrue(status(a).contains("inválido"));assertArrayEquals(before,catalogBytes(a));
 }
 @Test public void exportRejectsFileUriAndProviderFailureClosesTheStream()throws Exception {
  MainActivity a=ready();var picker=export(a);deliver(a,picker.requestCode,Activity.RESULT_OK,new Intent().setData(Uri.parse("file:///forbidden.json")));assertTrue(status(a),status(a).contains("inválido"));
  picker=export(a);AtomicBoolean closed=new AtomicBoolean();Uri uri=Uri.parse("content://profile-test/fail");OutputStream broken=new OutputStream(){public void write(int b)throws IOException{throw new IOException("synthetic provider failure");}public void close(){closed.set(true);}};
  Shadows.shadowOf(a.getContentResolver()).registerOutputStream(uri,broken);deliver(a,picker.requestCode,Activity.RESULT_OK,new Intent().setData(uri));await(a,"Falha");assertTrue(closed.get());assertTrue(control(a,"profile_export").isEnabled());
 }
}
