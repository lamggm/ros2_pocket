package com.lamggm.rospocket;

import android.view.View;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real host Android Activity, inherited explicit JNI double. */
public class RosGraphActivityTest extends ConnectionActivityTest {
 @Test public void wrongGraphFormatAndRetiredSessionCannotLeaveTopologyVisible() throws Exception {
  org.json.JSONObject s=new org.json.JSONObject(bridge.snapshot),f=RosGraphModelTest.fixture();s.put("nodes",f.getJSONArray("nodes")).put("topics",f.getJSONArray("topics")).put("graph",f.getJSONObject("graph"));bridge.snapshot=s.toString();tick();paint();
  RosGraphPanel p=a.getWindow().getDecorView().findViewWithTag("ros-graph-panel");assertFalse(p.vertices.isEmpty());
  s.getJSONObject("graph").put("format","humble");bridge.snapshot=s.toString();set("lastUi",0L);tick();paint();
  assertTrue("A schema mismatch must invalidate the interactive graph too",p.vertices.isEmpty());
  s.getJSONObject("graph").put("format","jazzy");bridge.snapshot=s.toString();set("lastUi",0L);tick();call("disconnect",new Class<?>[]{});paint();assertTrue(p.vertices.isEmpty());
 }
 @Test public void selectingANodeHighlightsItsConversationWithoutSubscribing() throws Exception {
  org.json.JSONObject s=new org.json.JSONObject(bridge.snapshot),f=RosGraphModelTest.fixture();
  s.put("nodes",f.getJSONArray("nodes")).put("topics",f.getJSONArray("topics")).put("graph",f.getJSONObject("graph"));
  bridge.snapshot=s.toString();tick();paint();
  View stage=a.getWindow().getDecorView().findViewWithTag("ros-graph-stage");assertNotNull("A touch graph canvas must consume the actual snapshot",stage);
  stage.layout(0,0,1000,500);
  android.view.accessibility.AccessibilityNodeProvider provider=stage.getAccessibilityNodeProvider();assertNotNull(provider);
  int subs=bridge.subscriptions;boolean selected=false;
  for(int i=1;i<=5;i++){
   android.view.accessibility.AccessibilityNodeInfo n=provider.createAccessibilityNodeInfo(i);
   if(n!=null&&n.getContentDescription().toString().equals("Nó /talker"))selected=provider.performAction(i,android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);
  }
  assertTrue("Accessible activation uses the same selection as touch",selected);
  android.widget.TextView details=a.getWindow().getDecorView().findViewWithTag("ros-graph-details");
  String detail=details.getText().toString();assertTrue(detail.contains("Publica"));assertTrue(detail.contains("/value"));assertTrue(detail.contains("/listener"));assertFalse(detail.contains("/unrelated"));
  assertEquals(subs,bridge.subscriptions);
 }
 @Test public void graphSectionIsARealReadOnlyDestination() throws Exception {
  int id=a.getResources().getIdentifier("nav_graph","id",a.getPackageName());
  assertNotEquals("A dedicated touch graph destination must exist",0,id);
  View nav=a.findViewById(id);assertNotNull(nav);
  tick();paint();int starts=bridge.starts,subs=bridge.subscriptions;
  nav.performClick();paint();
  int section=a.getResources().getIdentifier("section_graph","id",a.getPackageName());
  assertEquals(View.VISIBLE,a.findViewById(section).getVisibility());
  assertEquals(View.GONE,a.findViewById(R.id.section_monitor).getVisibility());
  assertEquals(starts,bridge.starts);assertEquals(subs,bridge.subscriptions);
 }
}
