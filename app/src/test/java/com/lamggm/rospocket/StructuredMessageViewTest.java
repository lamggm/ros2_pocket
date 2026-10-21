package com.lamggm.rospocket;

import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Actual Android views/framework JSON; DDS reception is separately tested in private containers. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class StructuredMessageViewTest {
 private ConnectionActivityTest h;
 @Before public void setup() throws Exception {h=new ConnectionActivityTest();h.setup();}
 @After public void cleanup() throws Exception {h.cleanup();}
 private JSONObject payload() throws Exception {
  return new JSONObject("{\"schemaVersion\":1,\"type\":\"nav_msgs/msg/Odometry\",\"root\":{\"path\":\"\",\"kind\":\"object\",\"children\":[{\"path\":\"header.frame_id\",\"kind\":\"string\",\"display\":\"map_ação_世界_🤖\",\"value\":\"map_ação_世界_🤖\"},{\"path\":\"twist\",\"kind\":\"object\",\"children\":[{\"path\":\"twist.linear.x\",\"kind\":\"number\",\"display\":\"1.25\",\"value\":1.25,\"numberState\":\"finite\"},{\"path\":\"twist.linear.y\",\"kind\":\"number\",\"display\":\"NaN\",\"value\":null,\"numberState\":\"nan\"}]}]},\"numericPaths\":[\"twist.linear.x\",\"twist.linear.y\"]}");
 }
 private void show(JSONObject data) throws Exception {
  h.bridge.snapshot=new JSONObject(h.bridge.snapshot).put("message",data).toString();
  h.set("lastUi",0L);h.tick();h.paint();
 }
 @Test public void malformedEmptyChildrenStillConsumeTraversalBudget() throws Exception {
  JSONObject data=payload();JSONArray many=new JSONArray();for(int i=0;i<1000;i++)many.put(JSONObject.NULL);
  data.getJSONObject("root").put("children",many);show(data);
  assertNotNull("null children bypassed the traversal budget",h.a.getWindow().getDecorView().findViewWithTag("structuredLimit"));
 }
 @Test public void depthAndUtf16TruncationRemainBounded() throws Exception {
  JSONObject data=payload();JSONObject leaf=new JSONObject().put("path","frame").put("kind","string").put("display","🤖".repeat(600));
  data.getJSONObject("root").put("children",new JSONArray().put(leaf));show(data);
  String text=((TextView)h.a.getWindow().getDecorView().findViewWithTag("field:frame")).getText().toString();
  assertTrue(text.endsWith("🤖…"));assertFalse(text.contains("�"));
  JSONObject nested=leaf;for(int i=0;i<12;i++)nested=new JSONObject().put("path","nested"+i).put("kind","object").put("children",new JSONArray().put(nested));
  data.put("root",nested);show(data);
  assertNull(h.a.getWindow().getDecorView().findViewWithTag("field:frame"));
  assertNotNull(h.a.getWindow().getDecorView().findViewWithTag("structuredLimit"));
 }
 @Test public void renderingIsBoundedAndExplicitlyReportsTruncation() throws Exception {
  JSONObject data=payload();JSONArray many=new JSONArray(),paths=new JSONArray();
  for(int i=0;i<300;i++){many.put(new JSONObject().put("path","values["+i+"]").put("kind","number").put("display","9".repeat(9000)).put("value",1));paths.put("values["+i+"]");}
  data.getJSONObject("root").put("children",many);data.put("numericPaths",paths);show(data);
  LinearLayout view=h.a.getWindow().getDecorView().findViewWithTag("structuredMessage");
  LinearLayout rows=(LinearLayout)view.getChildAt(2);
  assertTrue("unbounded native field views",rows.getChildCount()<=192);
  assertTrue(((Button)rows.getChildAt(0)).getText().length()<=700);
  TextView notice=h.a.getWindow().getDecorView().findViewWithTag("structuredLimit");
  assertNotNull(notice);assertTrue(notice.getText().toString().contains("limite"));
 }
 @Test public void selectionIsScopedToTypeAndDeclaredNumericPaths() throws Exception {
  JSONObject data=payload();show(data);((Button)h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.x")).performClick();
  data.put("type","geometry_msgs/msg/Twist");show(data);
  assertFalse(((TextView)h.a.getWindow().getDecorView().findViewWithTag("selectedNumericPath")).getText().toString().contains("twist.linear.x"));
  data.put("numericPaths",new JSONArray().put("twist.linear.y"));show(data);
  assertNull("undeclared number must not be selectable",h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.x"));
  ((Button)h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.y")).performClick();
  data.put("schemaVersion",2);show(data);
  assertNull("unknown schema displayed as supported",h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.y"));
 }
 @Test public void oldTreeIsClearedOnSubscriptionFailureAndStop() throws Exception {
  show(payload());assertNotNull(h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.x"));
  h.bridge.snapshot=new JSONObject(h.bridge.snapshot).put("error","reader failed").toString();show(payload());
  assertNull("failed reader kept old structured telemetry",h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.x"));
  h.bridge.snapshot=new JSONObject(h.bridge.snapshot).put("error","").toString();show(payload());
  h.call("onStop",new Class<?>[]{});
  assertNull("background kept old tree",h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.x"));
 }
 @Test public void inactiveSubscriptionClearsTreeAndSelection() throws Exception {
  show(payload());((Button)h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.x")).performClick();
  h.bridge.snapshot=new JSONObject(h.bridge.snapshot).put("subscription","").put("readerActive",false).toString();show(payload());
  assertNull(h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.x"));
  assertFalse(((TextView)h.a.getWindow().getDecorView().findViewWithTag("selectedNumericPath")).getText().toString().contains("twist.linear.x"));
 }
 @Test public void staleTreeIsExplicitAndCannotLookLive() throws Exception {
  show(payload());h.bridge.snapshot=new JSONObject(h.bridge.snapshot).put("age",4.0).put("fresh",false).put("subscriptionState","stale").toString();show(payload());
  TextView freshness=h.a.getWindow().getDecorView().findViewWithTag("structuredFreshness");
  assertNotNull(freshness);assertTrue(freshness.getText().toString().contains("ÚLTIMO VALOR"));
  assertTrue(h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.x").getAlpha()<1f);
 }
 @Test public void activityRendersUtf8TreeAndSelectsStableNumericPathWithoutReaderMutation() throws Exception {
  JSONObject decoded=NativeBridge.Api.json(payload().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  show(decoded);
  Button x=h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.x");
  assertNotNull("native numeric field must have an accessible selection control",x);
  assertTrue(x.getText().toString().contains("1.25"));x.performClick();
  TextView selected=h.a.getWindow().getDecorView().findViewWithTag("selectedNumericPath");
  assertTrue(selected.getText().toString().contains("twist.linear.x"));
  TextView frame=h.a.getWindow().getDecorView().findViewWithTag("field:header.frame_id");
  assertTrue(frame.getText().toString().contains("map_ação_世界_🤖"));
  int subscriptions=h.bridge.subscriptions;
  decoded.getJSONObject("root").getJSONArray("children").getJSONObject(1).getJSONArray("children").getJSONObject(0).put("display","2.5").put("value",2.5);
  show(decoded);
  assertTrue(((TextView)h.a.getWindow().getDecorView().findViewWithTag("selectedNumericPath")).getText().toString().contains("twist.linear.x"));
  assertTrue(((Button)h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.x")).getText().toString().contains("2.5"));
  assertTrue(((Button)h.a.getWindow().getDecorView().findViewWithTag("numeric:twist.linear.y")).getText().toString().contains("NaN"));
  assertEquals(subscriptions,h.bridge.subscriptions);
 }
}
