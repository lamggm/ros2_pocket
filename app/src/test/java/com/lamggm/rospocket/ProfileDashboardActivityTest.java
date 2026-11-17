package com.lamggm.rospocket;

import android.view.View;
import android.widget.*;
import java.io.File;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Actual profile/editor controls. Transport is the explicitly synthetic inherited JNI double. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class ProfileDashboardActivityTest {
 final ConnectionActivityTest f=new ConnectionActivityTest();
 @Before public void setup()throws Exception{f.setup();f.a.findViewById(R.id.connect).performClick();f.paint();}
 @After public void cleanup()throws Exception{f.cleanup();}
 DashboardBoard board(){return f.a.getWindow().getDecorView().findViewWithTag("dashboard-board");}
 void click(int id){View v=f.a.findViewById(id);assertNotNull(v);v.performClick();f.paint();}
 void saveProfile(String name){click(R.id.profile_new);((EditText)f.a.findViewById(R.id.profile_name)).setText(name);click(R.id.profile_save);}
 void apply(int index){((Spinner)f.a.findViewById(R.id.profile_select)).setSelection(index);f.paint();click(R.id.profile_apply);assertFalse((Boolean)read("connectWanted"));}
 Object read(String name){try{return f.get(name);}catch(Exception e){throw new AssertionError(e);}}
 BenchProfiles book()throws Exception{return new ProfileStore(new File(f.a.getFilesDir(),"bench-profiles-v1.json")).load();}
 @Test public void visualizationLivesInAppliedProfileAndPortableTransferNotTelemetry()throws Exception{saveProfile("Gauge A");apply(1);board().edit();java.lang.reflect.Field draft=DashboardBoard.class.getDeclaredField("draft");draft.setAccessible(true);draft.set(board(),board().layout().display("watch0",new DashboardLayout.Display("gauge","data",-10,50,"V")));assertTrue(board().save());String id=book().applied.id;assertEquals("gauge",book().find(id).dashboard.get("watch0").display.mode);assertEquals("gauge",BenchProfiles.importProfile(BenchProfiles.exportProfile(book().applied)).dashboard.get("watch0").display.mode);saveProfile("Gauge B");apply(2);board().edit();draft.set(board(),board().layout().display("watch0",new DashboardLayout.Display("bar","data",0,100,"%")));assertTrue(board().save());apply(1);assertEquals("gauge",board().layout().get("watch0").display.mode);f.call("onStop",new Class<?>[]{});f.a=Robolectric.buildActivity(MainActivity.class).create().get();assertEquals("gauge",board().layout().get("watch0").display.mode);assertFalse((Boolean)f.get("connectWanted"));}
 @Test public void applyingAnotherProfileRestoresItsOwnConfirmedDashboard()throws Exception{
  saveProfile("Robot A");apply(1);board().edit();board().resize("plot",4,4);assertTrue(board().save());
  saveProfile("Robot B");apply(2);board().edit();board().resize("plot",2,2);assertTrue(board().save());
  apply(1);assertEquals("Applying A must restore A's layout, not B's global layout",4,board().layout().get("plot").height);
  apply(2);assertEquals(2,board().layout().get("plot").height);
 }
 @Test public void legacyProfileExportsTheExistingGlobalLayoutWithoutRewritingItsFileOnLoad()throws Exception{
  saveProfile("Legacy");apply(1);
  File file=new File(f.a.getFilesDir(),"bench-profiles-v1.json");org.json.JSONObject legacy=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));legacy.put("schema",4);legacy.getJSONArray("profiles").getJSONObject(0).remove("dashboard");legacy.getJSONObject("applied").remove("dashboard");legacy.getJSONArray("profiles").getJSONObject(0).remove("localization");legacy.getJSONObject("applied").remove("localization");LegacyCodecFixtures.profile(legacy.getJSONArray("profiles").getJSONObject(0));LegacyCodecFixtures.profile(legacy.getJSONObject("applied"));byte[] before=legacy.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);java.nio.file.Files.write(file.toPath(),before);
  new DashboardStore(f.a.getFilesDir()).save(DashboardLayout.defaults().resize("plot",4,4));
  f.call("onStop",new Class<?>[]{});f.a=Robolectric.buildActivity(MainActivity.class).create().get();
  BenchProfiles.Profile p=((ProfilePanel)f.get("profiles")).applied();
  assertNotNull("Legacy profile must capture the previous global layout for portable export",p.dashboard);
  assertEquals(4,BenchProfiles.importProfile(BenchProfiles.exportProfile(p)).dashboard.get("plot").height);
  assertArrayEquals("Loading must not rewrite a legacy catalog",before,java.nio.file.Files.readAllBytes(file.toPath()));
 }
 @Test public void savingDashboardUpdatesAppliedAndCatalogWithoutWritingAnonymousLayout()throws Exception{
  String global=new DashboardStore(f.a.getFilesDir()).load().encode();saveProfile("A");apply(1);String id=book().applied.id;saveProfile("B");
  int starts=f.bridge.starts,subs=f.bridge.subscriptions;board().edit();board().resize("robot",2,4);board().moveBefore("plot","watch0");assertTrue(board().save());
  assertEquals(4,book().find(id).dashboard.get("robot").height);assertEquals(book().find(id).dashboard.encode(),book().applied.dashboard.encode());assertEquals("plot",book().applied.dashboard.items.get(0).id);
  assertEquals(global,new DashboardStore(f.a.getFilesDir()).load().encode());assertEquals(starts,f.bridge.starts);assertEquals(subs,f.bridge.subscriptions);
  BenchProfiles.Profile imported=BenchProfiles.importProfile(BenchProfiles.exportProfile(book().applied));assertNotEquals(id,imported.id);assertEquals(book().applied.dashboard.encode(),imported.rename("Imported").dashboard.encode());
 }
 @Test public void applyingDiscardsDraftAndRestartRestoresOnlyConfirmedLayout()throws Exception{
  saveProfile("A");apply(1);board().edit();board().resize("plot",4,3);assertTrue(board().save());saveProfile("B");apply(2);board().edit();board().resize("plot",2,2);assertTrue(board().save());board().edit();board().resize("plot",4,4);apply(1);assertFalse(board().editing());assertEquals(3,board().layout().get("plot").height);
  f.call("onStop",new Class<?>[]{});f.a=Robolectric.buildActivity(MainActivity.class).create().get();assertFalse((Boolean)f.get("connectWanted"));assertEquals(3,board().layout().get("plot").height);assertEquals(2,book().profiles.get(1).dashboard.get("plot").height);
 }
 @Test public void deletingAppliedProfileCannotRedirectItsDashboardSaveToAnotherProfile()throws Exception{
  saveProfile("A");apply(1);String global=new DashboardStore(f.a.getFilesDir()).load().encode();click(R.id.profile_delete);android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();f.paint();assertNull(book().applied);
  board().edit();board().resize("plot",4,4);assertFalse(board().save());assertTrue(board().editing());assertTrue(book().profiles.isEmpty());assertEquals(global,new DashboardStore(f.a.getFilesDir()).load().encode());
 }
 @Test public void manualNamesRemainPerProfileAcrossTransferAndColdActivityRestart()throws Exception{
  String global=new DashboardStore(f.a.getFilesDir()).load().encode();saveProfile("Named A");apply(1);board().edit();java.lang.reflect.Field draft=DashboardBoard.class.getDeclaredField("draft");draft.setAccessible(true);draft.set(board(),board().layout().rename("watch0","Velocidade").rename("robot","Vista frontal"));assertTrue(board().save());assertEquals("Velocidade",BenchProfiles.importProfile(BenchProfiles.exportProfile(book().applied)).dashboard.get("watch0").name);
  saveProfile("Named B");apply(2);board().edit();draft.set(board(),board().layout().rename("watch0","Corrente"));assertTrue(board().save());apply(1);assertEquals("Velocidade",board().layout().get("watch0").name);assertEquals("Vista frontal",board().layout().get("robot").name);assertEquals(global,new DashboardStore(f.a.getFilesDir()).load().encode());f.call("onStop",new Class<?>[]{});f.a=Robolectric.buildActivity(MainActivity.class).create().get();assertEquals("Velocidade",board().layout().get("watch0").name);assertEquals("Corrente",book().profiles.get(1).dashboard.get("watch0").name);assertFalse((Boolean)f.get("connectWanted"));
 }
 @Test public void freePositionsFollowAppliedProfileTransferAndRestartWithoutNewReaders()throws Exception{saveProfile("Grid A");apply(1);int starts=f.bridge.starts,subs=f.bridge.subscriptions;board().edit();java.lang.reflect.Field draft=DashboardBoard.class.getDeclaredField("draft");draft.setAccessible(true);draft.set(board(),board().layout().project(8).position("watch0",7,9).rename("watch0","Velocidade"));assertTrue(board().save());assertEquals(9,book().applied.dashboard.get("watch0").row);assertEquals(7,BenchProfiles.importProfile(BenchProfiles.exportProfile(book().applied)).dashboard.get("watch0").column);assertEquals(starts,f.bridge.starts);assertEquals(subs,f.bridge.subscriptions);f.call("onStop",new Class<?>[]{});f.a=Robolectric.buildActivity(MainActivity.class).create().get();assertEquals(9,board().layout().get("watch0").row);assertEquals("Velocidade",board().layout().get("watch0").name);assertFalse((Boolean)f.get("connectWanted"));}
}
