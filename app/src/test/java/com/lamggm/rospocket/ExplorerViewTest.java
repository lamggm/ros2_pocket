package com.lamggm.rospocket;

import android.content.Context;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Host Android views and real framework JSON; no DDS data reception is simulated. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class ExplorerViewTest {
 private Context context; private ExplorerView view; private int reads;
 private static JSONArray topics() throws Exception {
  return new JSONArray("[{\"name\":\"/robot/a\",\"type\":\"std_msgs/msg/String\",\"wireName\":\"rt/robot/a\",\"wireType\":\"std_msgs::msg::dds_::String_\",\"supported\":true,\"publishers\":2,\"subscribers\":1},{\"name\":\"/robotics/b\",\"type\":\"custom/msg/B\",\"wireName\":\"rt/robotics/b\",\"wireType\":\"custom::msg::dds_::B_\",\"supported\":false}]");
 }
 @Before public void setup() {
  context=RuntimeEnvironment.getApplication();
  context.getSharedPreferences("explorer",0).edit().clear().commit();
  view=new ExplorerView(context,topic->reads++);
 }
 @Test public void favoriteLimitAndEndpointDetailLimitAreExplicit() throws Exception {
  java.util.Set<String> full=new java.util.HashSet<>();for(int i=0;i<256;i++)full.add("saved/"+i);
  context.getSharedPreferences("explorer",0).edit().putStringSet("favorites",full).commit();
  view=new ExplorerView(context,t->reads++);JSONArray data=topics();JSONObject t=data.getJSONObject(0);
  JSONArray endpoints=new JSONArray();for(int i=0;i<130;i++)endpoints.put(new JSONObject().put("id","gid-"+i));t.put("endpoints",endpoints);view.update(data);
  ((CheckBox)view.findViewWithTag("favorite:rt/robot/a\nstd_msgs::msg::dds_::String_")).performClick();
  assertEquals(256,context.getSharedPreferences("explorer",0).getStringSet("favorites",java.util.Collections.emptySet()).size());
  ((Button)view.findViewWithTag("details:rt/robot/a\nstd_msgs::msg::dds_::String_")).performClick();
  String text=((TextView)view.findViewById(R.id.explorer_details)).getText().toString();assertTrue(text.contains("128 de 130"));assertFalse(text.contains("gid-129"));
 }
 @Test public void renderingIsBoundedAndUnchangedSnapshotsKeepControls() throws Exception {
  JSONArray many=new JSONArray();for(int i=0;i<205;i++){JSONObject t=new JSONObject(topics().getJSONObject(0).toString());t.put("name","/topic/"+i);t.put("wireName","rt/topic/"+i);many.put(t);}
  view.update(many);LinearLayout rows=view.findViewById(R.id.explorer_rows);
  assertEquals(205,view.visibleCount());assertTrue("unbounded topic views",rows.getChildCount()<=800);
  android.view.View first=rows.getChildAt(0);view.update(new JSONArray(many.toString()));assertSame("poll recreates unchanged controls",first,rows.getChildAt(0));
  ((EditText)view.findViewById(R.id.explorer_search)).setText("/topic/204");assertEquals(1,view.visibleCount());
 }
 @Test public void onlyExplicitReadOfSupportedTopicInvokesListener() throws Exception {
  view.update(topics());
  Button read=view.findViewWithTag("read:rt/robot/a\nstd_msgs::msg::dds_::String_");
  assertNotNull("read control missing",read);assertTrue(read.isEnabled());read.performClick();assertEquals(1,reads);
  Button unsupported=view.findViewWithTag("read:rt/robotics/b\ncustom::msg::dds_::B_");
  assertNotNull(unsupported);assertFalse(unsupported.isEnabled());assertEquals(1,reads);
 }
 @Test public void activityEmbedsExplorerAndDisconnectClearsItsSnapshot() throws Exception {
  context.getSharedPreferences("connection",0).edit().putBoolean("wanted",true).commit();
  org.robolectric.android.controller.ActivityController<MainActivity> controller=org.robolectric.Robolectric.buildActivity(MainActivity.class).create();
  MainActivity activity=controller.get();
  assertTrue("Activity still uses the old topic list",activity.findViewById(R.id.topics) instanceof ExplorerView);
  ExplorerView explorer=activity.findViewById(R.id.topics);explorer.update(topics());assertEquals(2,explorer.visibleCount());
  activity.findViewById(R.id.connect).performClick();assertEquals(0,explorer.visibleCount());
  controller.destroy();context.getSharedPreferences("connection",0).edit().clear().commit();
 }
 @Test public void unsupportedDetailsShowRealIdentityAndRefreshOnRemovalWithoutReading() throws Exception {
  JSONArray data=topics();JSONObject topic=data.getJSONObject(1);
  topic.put("endpoints",new JSONArray("[{\"id\":\"endpoint-real-gid\",\"participant\":\"participant-not-node\",\"writer\":true,\"nodes\":[],\"qos\":{\"reliability\":\"best_effort\",\"durability\":\"volatile\",\"deadlineNs\":-1},\"compatibility\":{\"state\":\"incompatible\",\"reasons\":[\"reliability\",\"durability\"]}}]"));
  view.update(data);Button details=view.findViewWithTag("details:rt/robotics/b\ncustom::msg::dds_::B_");
  assertNotNull("endpoint detail control missing",details);details.performClick();
  String text=((TextView)view.findViewById(R.id.explorer_details)).getText().toString();
  assertTrue(text.contains("endpoint-real-gid"));assertTrue(text.contains("desconhecido"));
  assertTrue("Compatibility is slot-zero only, not the selected watch",text.contains("perfil do Watch 1"));
  assertTrue(text.contains("best_effort"));assertTrue(text.contains("incompatible"));assertTrue("missing unknown-duration legend",text.contains("-2 = desconhecido"));
  assertTrue(text.contains("reliability"));assertTrue(text.contains("durability"));assertTrue(text.contains("padrão best_effort / volatile até assinar"));assertEquals(0,reads);
  topic.getJSONArray("endpoints").getJSONObject(0).put("nodes",new JSONArray().put("/robot/camera"));
  view.update(data);assertTrue(((TextView)view.findViewById(R.id.explorer_details)).getText().toString().contains("/robot/camera"));
  ((EditText)view.findViewById(R.id.explorer_search)).setText("CAMERA");assertEquals(1,view.visibleCount());
  view.update(new JSONArray());text=((TextView)view.findViewById(R.id.explorer_details)).getText().toString();
  assertTrue(text.contains("não está anunciado"));assertFalse(text.contains("endpoint-real-gid"));assertEquals(0,reads);
 }
 @Test public void favoritesPersistByWireNameAndTypeWithoutReading() throws Exception {
  view.update(topics());
  CheckBox star=view.findViewWithTag("favorite:rt/robot/a\nstd_msgs::msg::dds_::String_");
  assertNotNull("favorite control missing",star);star.performClick();assertEquals(0,reads);
  view=new ExplorerView(context,topic->reads++);view.update(topics());
  ((CheckBox)view.findViewById(R.id.explorer_favorites)).performClick();
  assertEquals(1,view.visibleCount());assertEquals(0,reads);
  view.update(new JSONArray());assertEquals(0,view.visibleCount());
  view.update(topics());assertEquals(1,view.visibleCount());
  JSONObject changed=topics().getJSONObject(0);changed.put("wireType","different");
  view.update(new JSONArray().put(changed));assertEquals(0,view.visibleCount());
 }
 @Test public void searchMatchesOnlyAdvertisedNamesTypesAndAssociatedNodes() throws Exception {
  JSONArray data=topics();data.getJSONObject(0).put("endpoints",new JSONArray("[{\"id\":\"metadata-gid\",\"nodes\":[],\"qos\":{\"reliability\":\"best_effort\"},\"compatibility\":{\"reasons\":[\"mismatch-metadata\"]}}]"));
  view.update(data);EditText search=view.findViewById(R.id.explorer_search);
  for(String metadata:new String[]{"nodes","reliability","metadata-gid","best_effort","mismatch-metadata","null"}){search.setText(metadata);assertEquals("metadata-only match: "+metadata,0,view.visibleCount());}
  data.getJSONObject(0).getJSONArray("endpoints").getJSONObject(0).put("nodes",new JSONArray().put("/robot/CAMERA"));
  view.update(data);search.setText("camera");assertEquals(1,view.visibleCount());
  search.setText("STRING");assertEquals(1,view.visibleCount());search.setText("/robot/a");assertEquals(1,view.visibleCount());assertEquals(0,reads);
 }
 @Test public void filtersAreLocalAndNamespaceUsesPathBoundary() throws Exception {
  view.update(topics()); assertEquals(2,view.visibleCount());
  ((EditText)view.findViewById(R.id.explorer_namespace)).setText("/robot");
  assertEquals(1,view.visibleCount());
  ((EditText)view.findViewById(R.id.explorer_search)).setText("STRING");
  assertEquals(1,view.visibleCount());
  ((EditText)view.findViewById(R.id.explorer_type)).setText("custom/msg");
  assertEquals(0,view.visibleCount()); assertEquals(0,reads);
 }
}
