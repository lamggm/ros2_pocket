package com.lamggm.rospocket;

import android.content.Context;
import android.widget.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class RosoutConsoleViewTest {
 @Test public void negativeRosTimeIsRenderedAsItsActualSignedValue() throws Exception {
  RosoutConsoleView view=new RosoutConsoleView(RuntimeEnvironment.getApplication(),()->{});
  JSONObject entry=new JSONObject().put("level",20).put("logger","clock").put("message","simulated time").put("sourceSec",-1).put("sourceNanosec",500000000).put("intakeAgeMs",7);
  view.update(new JSONObject().put("active",true).put("count",1).put("entries",new org.json.JSONArray().put(entry)));
  String rendered=((TextView)view.findViewById(R.id.rosout_entries)).getText().toString();
  assertTrue("ROS Time seconds and nonnegative nanos must be added, not concatenated: "+rendered,rendered.contains("-0.500000000"));
  assertFalse(rendered.contains("-1.500000000"));
  ((EditText)view.findViewById(R.id.rosout_timestamp)).setText("-0.500");
  assertTrue(((TextView)view.findViewById(R.id.rosout_entries)).getText().toString().contains("simulated time"));
 }
 @Test public void filtersAreLocalAndOfferedQosIsSeparateFromReaderRequest() throws Exception {
  Context context=RuntimeEnvironment.getApplication();final int[] clears={0};RosoutConsoleView view=new RosoutConsoleView(context,()->clears[0]++);
  view.update(new JSONObject("{\"active\":true,\"count\":3,\"bytes\":500,\"offeredReliability\":[\"reliable\"],\"offeredDurability\":[\"transient_local\"],\"entries\":[{\"level\":20,\"logger\":\"camera.driver\",\"message\":\"started\",\"sourceSec\":1700000000,\"sourceNanosec\":1,\"intakeAgeMs\":1},{\"level\":40,\"logger\":\"camera.driver\",\"message\":\"sensor failed\",\"sourceSec\":1700000001,\"sourceNanosec\":2,\"intakeAgeMs\":2},{\"level\":40,\"logger\":\"planner\",\"message\":\"planner failed\",\"sourceSec\":1700000002,\"sourceNanosec\":3,\"intakeAgeMs\":3}]}"));
  TextView status=view.findViewById(R.id.rosout_status);
  assertTrue("writer offered QoS must be reported independently",status.getText().toString().contains("reliable")&&status.getText().toString().contains("transient_local"));
  ((Spinner)view.findViewById(R.id.rosout_severity)).setSelection(4);
  ((EditText)view.findViewById(R.id.rosout_logger)).setText("camera");
  ((EditText)view.findViewById(R.id.rosout_timestamp)).setText("1700000001");
  ((EditText)view.findViewById(R.id.rosout_search)).setText("FAILED");
  String rendered=((TextView)view.findViewById(R.id.rosout_entries)).getText().toString();
  assertTrue(rendered.contains("sensor failed"));assertFalse(rendered.contains("planner failed"));assertFalse(rendered.contains("started"));
  ((Button)view.findViewById(R.id.rosout_clear)).performClick();assertEquals(1,clears[0]);
 }
}
