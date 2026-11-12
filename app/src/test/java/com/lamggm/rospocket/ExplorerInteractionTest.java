package com.lamggm.rospocket;
import android.content.Context;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class ExplorerInteractionTest {
 @Test public void topicTapOnlyInspectsAndExplicitReadInvokesListener() throws Exception {
  Context c=RuntimeEnvironment.getApplication();int[] reads={0};ExplorerView v=new ExplorerView(c,t->reads[0]++);
  v.update(new JSONArray("[{\"name\":\"/demo/value\",\"type\":\"std_msgs/msg/Float64\",\"wireName\":\"rt/demo/value\",\"wireType\":\"std_msgs::msg::dds_::Float64_\",\"supported\":true}]"));
  java.util.ArrayList<View> matches=new java.util.ArrayList<>();v.findViewsWithText(matches,"Tópico /demo/value",View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);assertEquals(1,matches.size());matches.get(0).performClick();
  assertEquals("Inspecting metadata must not create a reader",0,reads[0]);
  android.widget.LinearLayout rows=v.findViewById(R.id.explorer_rows);assertTrue("Selected topic card must highlight immediately",rows.getChildAt(0).isSelected());
  assertTrue(((TextView)v.findViewById(R.id.explorer_details)).getText().toString().contains("/demo/value"));
  v.findViewWithTag("read:rt/demo/value\nstd_msgs::msg::dds_::Float64_").performClick();assertEquals(1,reads[0]);
 }
}
