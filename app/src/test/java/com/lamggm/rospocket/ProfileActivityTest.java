package com.lamggm.rospocket;

import android.view.View;
import android.widget.*;
import java.io.File;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual Android Views; inherited bridge is explicitly a JNI double. */
public class ProfileActivityTest extends ConnectionActivityTest {
 private View v(String name){int id=a.getResources().getIdentifier(name,"id",a.getPackageName());assertNotEquals("Profile control must have a stable ID: "+name,0,id);View view=a.findViewById(id);assertNotNull("Profile control exists: "+name,view);return view;}
 private void name(String value){((EditText)v("profile_name")).setText(value);}
 private String notice(){return ((TextView)v("profile_status")).getText().toString();}
 private BenchProfiles book() throws Exception{return new ProfileStore(new File(a.getFilesDir(),"bench-profiles-v1.json")).load();}
 private void drain()throws Exception{((ScheduledExecutorService)field("DDS_WORKER").get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);paint();}
 @Test public void saveSelectAndApplyAreDistinctAndApplicationRequiresDisconnected() throws Exception {
  tick();paint();int starts=bridge.starts,subscriptions=bridge.subscriptions;
  name("Bancada 1");v("profile_save").performClick();
  BenchProfiles saved=book();assertEquals(1,saved.profiles.size());assertNull("Saving must not apply a configuration",saved.applied);
  assertEquals("rt/numeric",saved.profiles.get(0).watches.get(0).wire);
  ((Spinner)v("profile_select")).setSelection(1);paint();
  assertEquals(starts,bridge.starts);assertEquals(subscriptions,bridge.subscriptions);
  v("profile_apply").performClick();assertTrue(notice(),notice().contains("Desconecte"));assertNull(book().applied);
  a.findViewById(R.id.connect).performClick();drain();v("profile_apply").performClick();
  assertNotNull(book().applied);assertEquals("rt/numeric",get("selectedWire"));assertFalse((Boolean)get("connectWanted"));assertEquals(starts,bridge.starts);
  assertFalse(text(R.id.counts).contains("RECEBENDO"));assertNotEquals("42.5",text(R.id.message));
  a.findViewById(R.id.connect).performClick();drain();tick();paint();
  assertEquals("rt/numeric",get("selectedWire"));assertEquals(starts+1,bridge.starts);assertEquals(subscriptions+1,bridge.subscriptions);
 }
 @Test public void appliedConfigurationRestartsDisconnectedWithFourExactWatchesAndQos() throws Exception {
  WatchSlots slots=(WatchSlots)get("watchSlots");for(int i=1;i<4;i++)slots.request(i,"rt/bench/extra_"+i,"std_msgs::msg::dds_::Float64_",i==1,i==3);
  name("Quatro");v("profile_save").performClick();a.findViewById(R.id.connect).performClick();drain();v("profile_apply").performClick();
  MainActivity old=a;call("onStop",new Class<?>[]{});drain();a=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();
  try{
   assertFalse((Boolean)get("connectWanted"));assertEquals("Conectar",text(R.id.connect));assertEquals("rt/numeric",get("selectedWire"));WatchSlots restored=(WatchSlots)get("watchSlots");
   for(int i=1;i<4;i++){assertEquals("rt/bench/extra_"+i,restored.get(i).wire);assertEquals(i==1,restored.get(i).reliable);assertEquals(i==3,restored.get(i).transientLocal);assertTrue(restored.frame(i).pending);}
   assertFalse(text(R.id.counts).contains("RECEBENDO"));assertEquals("Nenhuma mensagem recebida.",text(R.id.message));
  }finally{a=old;}
 }
 @Test public void numericPlotChoicesSurviveApplyAndAwaitFreshMatchingData() throws Exception {
  org.json.JSONObject snapshot=new org.json.JSONObject(bridge.snapshot).put("receivedMonotonicNs",100L).put("sourceTimestampNs",1000L).put("sessionGeneration",1).put("readerGeneration",1);
  snapshot.put("numericFields",new org.json.JSONArray().put(new org.json.JSONObject().put("path","data").put("value",42.5).put("state","finite").put("unit",org.json.JSONObject.NULL)));
  bridge.snapshot=snapshot.toString();tick();paint();PlotView plot=a.getWindow().getDecorView().findViewWithTag("live-plot");
  ((CheckBox)plot.findViewWithTag("plot-field:/numeric\ndata")).performClick();
  name("Com gráfico");v("profile_save").performClick();assertEquals(1,book().profiles.get(0).plots.size());
  a.findViewById(R.id.connect).performClick();drain();v("profile_apply").performClick();
  assertTrue(((PlotController)get("plots")).history.snapshot().isEmpty());
  // Saving a pending configuration must not silently forget graph choices.
  v("profile_save").performClick();assertEquals(1,book().profiles.get(0).plots.size());
  a.findViewById(R.id.connect).performClick();drain();snapshot.put("sessionGeneration",2).put("count",1).put("receivedMonotonicNs",200L);bridge.snapshot=snapshot.toString();tick();paint();
  assertEquals(1,((PlotController)get("plots")).history.snapshot().size());
  assertEquals(1,((PlotController)get("plots")).history.points("/numeric\ndata").size());
  ((CheckBox)plot.findViewWithTag("plot-field:/numeric\ndata")).performClick();snapshot.put("count",2).put("receivedMonotonicNs",300L);bridge.snapshot=snapshot.toString();tick();paint();
  assertTrue("Unchecking must cancel a restored plot choice",((PlotController)get("plots")).history.snapshot().isEmpty());
 }
 @Test public void absentOrDivergentTopicStaysExplicitWithoutSelectingAnotherReader() throws Exception {
  name("Ausente");v("profile_save").performClick();a.findViewById(R.id.connect).performClick();drain();v("profile_apply").performClick();a.findViewById(R.id.connect).performClick();drain();tick();paint();
  assertTrue(text(R.id.counts),text(R.id.counts).contains("TÓPICO AUSENTE"));assertEquals("rt/numeric",get("selectedWire"));int subscriptions=bridge.subscriptions;
  org.json.JSONObject wrong=new org.json.JSONObject().put("wireName","rt/numeric").put("wireType","std_msgs::msg::dds_::String_");
  bridge.snapshot=new org.json.JSONObject(bridge.snapshot).put("topics",new org.json.JSONArray().put(wrong)).toString();set("lastUi",0L);tick();paint();
  assertTrue(text(R.id.counts),text(R.id.counts).contains("TIPO DIVERGENTE"));assertEquals(subscriptions,bridge.subscriptions);assertEquals("std_msgs::msg::dds_::Float64_",get("selectedType"));assertFalse(text(R.id.counts).contains("RECEBENDO"));
 }
 @Test public void disconnectRetainsAppliedWatchConfigurationForExplicitSaveAndReconnect() throws Exception {
  name("Persistente");v("profile_save").performClick();a.findViewById(R.id.connect).performClick();drain();v("profile_apply").performClick();a.findViewById(R.id.connect).performClick();drain();tick();paint();
  a.findViewById(R.id.connect).performClick();drain();assertEquals("rt/numeric",get("selectedWire"));
  v("profile_save").performClick();assertEquals("rt/numeric",book().profiles.get(0).watches.get(0).wire);
  assertFalse((Boolean)get("connectWanted"));assertFalse(text(R.id.counts).contains("RECEBENDO"));
 }
 @Test public void deletionNeedsConfirmationAndDoesNotAlterTheLiveSession() throws Exception {
  tick();paint();name("Excluir");v("profile_save").performClick();int starts=bridge.starts,stops=bridge.stops,subscriptions=bridge.subscriptions;
  v("profile_delete").performClick();android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertEquals(1,book().profiles.size());dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();assertEquals(1,book().profiles.size());
  v("profile_delete").performClick();dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();paint();assertTrue(notice(),book().profiles.isEmpty());assertEquals(starts,bridge.starts);assertEquals(stops,bridge.stops);assertEquals(subscriptions,bridge.subscriptions);assertTrue("Deletion cannot disconnect",(Boolean)get("connectWanted"));
 }
 @Test public void corruptCatalogBlocksProfileWritesAndAutoConnectWithoutChangingLegacyPreferences() throws Exception {
  byte[] corrupt="{broken".getBytes(java.nio.charset.StandardCharsets.UTF_8);File file=new File(a.getFilesDir(),"bench-profiles-v1.json");java.nio.file.Files.write(file.toPath(),corrupt);
  java.util.Map<String,?> before=new java.util.HashMap<>(a.getSharedPreferences("connection",0).getAll());MainActivity old=a;a=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();
  try{assertFalse((Boolean)get("connectWanted"));assertFalse(v("profile_save").isEnabled());assertTrue(notice().contains("preservado"));assertEquals(before,a.getSharedPreferences("connection",0).getAll());assertArrayEquals(corrupt,java.nio.file.Files.readAllBytes(file.toPath()));}finally{a=old;}
 }
 @Test public void delayedStartCannotSubscribeAppliedProfileBeforeAnExplicitReconnect() throws Exception {
  WatchSlots slots=(WatchSlots)get("watchSlots");for(int i=1;i<4;i++)slots.request(i,"rt/new_profile/extra_"+i,"std_msgs::msg::dds_::Float64_",false,false);
  name("Aplicar durante start");v("profile_save").performClick();for(int i=1;i<4;i++)slots.request(i,"","",false,false);
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);java.util.List<String> watched=java.util.Collections.synchronizedList(new java.util.ArrayList<>());
  Fake blocked=new Fake(){
   @Override public byte[] start(int d,String address,String peer){entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("start release timed out");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new AssertionError(ex);}return super.start(d,address,peer);}
   @Override public byte[] watch(int slot,String wire,String type,boolean r,boolean t){watched.add(wire);return bytes("{\"ok\":true}");}
  };
  bridge=blocked;set("bridge",blocked);ScheduledExecutorService executor=(ScheduledExecutorService)get("worker");Future<?> pending=executor.submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception ex){throw new RuntimeException(ex);}});
  try{assertTrue("Entered the real Activity start path",entered.await(5,TimeUnit.SECONDS));a.findViewById(R.id.connect).performClick();v("profile_apply").performClick();assertNotNull(book().applied);assertFalse((Boolean)get("connectWanted"));assertEquals("rt/new_profile/extra_1",slots.get(1).wire);}finally{release.countDown();}
  pending.get(5,TimeUnit.SECONDS);drain();
  assertTrue("A stale start must not acquire newly applied readers: "+watched,watched.isEmpty());assertEquals("No primary reader after disconnect",0,bridge.subscriptions);assertEquals("No stale startup poll",0,bridge.polls);assertFalse((Boolean)get("nativeRunning"));assertNull(get("multicast"));
 }
 @Test public void appliedSnapshotWinsOverAutomaticViewStateDuringFullActivityRestoration() throws Exception {
  ((EditText)a.findViewById(R.id.domain)).setText("69");((EditText)a.findViewById(R.id.peer)).setText("192.0.2.5");call("requestConnection",new Class<?>[]{boolean.class},false);
  ((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);((CheckBox)a.findViewById(R.id.qos_transient)).setChecked(true);
  name("Snapshot A");v("profile_save").performClick();a.findViewById(R.id.connect).performClick();drain();v("profile_apply").performClick();assertNotNull(book().applied);
  // These are unsaved edits B, not another explicit application.
  ((EditText)a.findViewById(R.id.domain)).setText("88");((EditText)a.findViewById(R.id.peer)).setText("192.0.2.8");((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(false);((CheckBox)a.findViewById(R.id.qos_transient)).setChecked(false);
  android.os.Bundle saved=new android.os.Bundle();org.robolectric.util.ReflectionHelpers.callInstanceMethod(a,"onSaveInstanceState",org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.os.Bundle.class,saved));assertNotNull("Real Window hierarchy must be present",saved.getBundle("android:viewHierarchyState"));
  MainActivity old=a;call("onStop",new Class<?>[]{});drain();org.robolectric.android.controller.ActivityController<MainActivity> controller=org.robolectric.Robolectric.buildActivity(MainActivity.class).create(saved);a=controller.get();a.bridge=new Fake();
  try{
   controller.start().restoreInstanceState(saved).resume();paint();
   assertFalse((Boolean)get("connectWanted"));assertEquals("69",text(R.id.domain));assertEquals("192.0.2.5",text(R.id.peer));
   assertTrue(((CheckBox)a.findViewById(R.id.qos_reliable)).isChecked());assertTrue(((CheckBox)a.findViewById(R.id.qos_transient)).isChecked());assertEquals(true,get("reliableValue"));assertEquals(true,get("transientValue"));
   assertEquals("rt/numeric",get("selectedWire"));assertFalse(text(R.id.counts).contains("RECEBENDO"));assertEquals("Nenhuma mensagem recebida.",text(R.id.message));assertEquals(0,((Fake)a.bridge).starts);
   name("Restaurado");v("profile_save").performClick();BenchProfiles.Profile restored=book().profiles.get(0);assertEquals(69,restored.domain);assertEquals("192.0.2.5",restored.peer);assertTrue(restored.watches.get(0).reliable);assertTrue(restored.watches.get(0).transientLocal);
  }finally{controller.pause().stop().destroy();a=old;}
 }
 @Test public void renamingPreservesSavedSettingsAndNewProfileDoesNotApply() throws Exception {
  name("Original");v("profile_save").performClick();String id=book().profiles.get(0).id;
  name("Renomeado");v("profile_rename").performClick();assertEquals("Renomeado",book().find(id).name);assertEquals("rt/numeric",book().find(id).watches.get(0).wire);
  v("profile_new").performClick();name("Segundo");v("profile_save").performClick();assertEquals(2,book().profiles.size());assertNull(book().applied);
 }
}
