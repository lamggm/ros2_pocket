package com.lamggm.rospocket;
import android.view.View;
import android.widget.*;
import org.junit.Test;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

public class WorkspaceActivityTest extends ConnectionActivityTest {
 @Test public void panelCardsSelectEditorWithoutChangingReaders() throws Exception {
  int subscriptions=bridge.subscriptions;
  for(int i=0;i<4;i++)assertNotNull("Missing dashboard card "+i,a.getWindow().getDecorView().findViewWithTag("watch-card:"+i));
  a.getWindow().getDecorView().findViewWithTag("watch-card:2").performClick();
  assertEquals(2,get("activeWatch"));assertEquals(subscriptions,bridge.subscriptions);
 }
 @Test public void explicitExplorerReadOffersSlotBeforeChangingReader() throws Exception {
  org.json.JSONObject topic=new org.json.JSONObject().put("name","/numeric").put("type","std_msgs/msg/Float64").put("wireName","rt/numeric").put("wireType","std_msgs::msg::dds_::Float64_").put("supported",true);
  bridge.snapshot=new org.json.JSONObject(bridge.snapshot).put("topics",new org.json.JSONArray().put(topic)).toString();tick();paint();
  int subscriptions=bridge.subscriptions;
  a.findViewById(R.id.topics).findViewWithTag("read:rt/numeric\nstd_msgs::msg::dds_::Float64_").performClick();
  assertEquals("Opening the chooser is not a read",subscriptions,bridge.subscriptions);
  android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);assertTrue(d.isShowing());assertEquals(4,d.getListView().getAdapter().getCount());
  d.getListView().performItemClick(null,2,2);paint();
  assertEquals("rt/numeric",((WatchSlots)get("watchSlots")).get(2).wire);
 }
 @Test public void chooserFromOldNetworkEpochCannotStartAReader() throws Exception {
  org.json.JSONObject topic=new org.json.JSONObject().put("name","/numeric").put("type","std_msgs/msg/Float64").put("wireName","rt/numeric").put("wireType","std_msgs::msg::dds_::Float64_").put("supported",true);
  bridge.snapshot=new org.json.JSONObject(bridge.snapshot).put("topics",new org.json.JSONArray().put(topic)).toString();tick();paint();
  a.findViewById(R.id.topics).findViewWithTag("read:rt/numeric\nstd_msgs::msg::dds_::Float64_").performClick();
  android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing());
  ((ConnectionPolicy)get("policy")).invalidate();d.getListView().performItemClick(null,2,2);paint();
  assertEquals("Network epoch retirement must reject an old chooser","",((WatchSlots)get("watchSlots")).get(2).wire);
 }
 @Test public void replacementConfirmationCannotOverwriteANewerSlotRequest() throws Exception {
  org.json.JSONObject topic=new org.json.JSONObject().put("name","/numeric").put("type","std_msgs/msg/Float64").put("wireName","rt/numeric").put("wireType","std_msgs::msg::dds_::Float64_").put("supported",true);
  bridge.snapshot=new org.json.JSONObject(bridge.snapshot).put("topics",new org.json.JSONArray().put(topic)).toString();tick();paint();
  Class<?>[] signature={int.class,String.class,String.class,boolean.class,boolean.class};
  call("requestExtra",signature,2,"rt/older","std_msgs::msg::dds_::Float64_",false,false);
  a.findViewById(R.id.topics).findViewWithTag("read:rt/numeric\nstd_msgs::msg::dds_::Float64_").performClick();
  android.app.AlertDialog chooser=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();chooser.getListView().performItemClick(null,2,2);paint();
  android.app.AlertDialog confirm=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(confirm.isShowing());
  call("requestExtra",signature,2,"rt/newer","std_msgs::msg::dds_::Float64_",false,false);
  confirm.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick();paint();
  assertEquals("Delayed confirmation must retain newer slot ownership","rt/newer",((WatchSlots)get("watchSlots")).get(2).wire);
 }
 @Test @Config(qualifiers="w1000dp-h700dp") public void tabletUsesFourSideDestinationsAndGlobalConnection() {
  LinearLayout nav=a.getWindow().getDecorView().findViewWithTag("primary-navigation");
  assertNotNull("Four-destination shell is missing",nav);assertEquals(4,nav.getChildCount());
  assertEquals(LinearLayout.VERTICAL,nav.getOrientation());
  assertNotSame(nav,a.findViewById(R.id.nav_connection).getParent());
  assertEquals("Painel",((Button)a.findViewById(R.id.nav_monitor)).getText().toString());
  assertEquals("Explorar",((Button)a.findViewById(R.id.nav_topics)).getText().toString());
  a.findViewById(R.id.nav_monitor).performClick();View panel=a.findViewById(R.id.section_monitor);
  a.findViewById(R.id.nav_connection).performClick();assertEquals(View.GONE,panel.getVisibility());
  a.findViewById(R.id.nav_monitor).performClick();assertSame(panel,a.findViewById(R.id.section_monitor));
 }
}
