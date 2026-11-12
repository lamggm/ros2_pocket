package com.lamggm.rospocket;
import android.graphics.*;
import org.json.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31,qualifiers="w1280dp-h850dp") @LooperMode(LooperMode.Mode.PAUSED)
public class GraphNamespaceSceneTest {
 static JSONObject sample() throws Exception {return new JSONObject().put("running",true).put("graph",new JSONObject().put("state","received")).put("nodes",new JSONArray().put("/robo/navigation/planner").put("/robo/navigation/controller").put("/robo/perception/camera")).put("topics",new JSONArray());}
 static JSONObject crowded() throws Exception {JSONArray nodes=new JSONArray();for(int i=0;i<25;i++)nodes.put("/ns"+(i<10?"0":"")+i+"/node");return sample().put("nodes",nodes);}
 @Test public void filteredNamespaceBeyondRenderCapHasStableAccessibleIdentity() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());JSONObject data=crowded();p.update(data);p.stage.layout(0,0,1100,620);assertEquals(25,p.vertices.size());assertEquals(24,p.namespaceCircles(p.visibleVertices()).size());p.namespace.setText("/ns24");String key="ns:/ns24";android.view.accessibility.AccessibilityNodeProvider provider=p.stage.getAccessibilityNodeProvider();android.view.accessibility.AccessibilityNodeInfo host=provider.createAccessibilityNodeInfo(android.view.View.NO_ID);assertNotNull(host);assertNotNull(p.stage.virtualIds.get(key));int id=p.stage.virtualIds.get(key);assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null));assertEquals(key,p.selected);assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null));p.namespace.setText("");assertEquals(Integer.valueOf(id),p.stage.virtualIds.get(key));p.namespace.setText("/ns24");data.getJSONArray("nodes").put("/ns24/joined");p.update(data);assertEquals(Integer.valueOf(id),p.stage.virtualIds.get(key));assertNotNull(provider.createAccessibilityNodeInfo(id));
 }
 @Test public void filteredNamespacePinSurvivesTheRenderCapAndRetiresOnlyOnRealRemoval() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());JSONObject data=crowded();p.update(data);p.namespace.setText("/ns24");String key="ns:/ns24";p.select(key);p.pinButton.performClick();assertTrue(p.pinnedNamespaces.contains(key));String original=GraphInteractionTest.named(p,"/ns24/node");assertTrue(p.physics.bodies.get(original).pinned);data.getJSONArray("nodes").put("/ns24/joined");p.update(data);assertTrue("A rendering cap must not retire a live group pin",p.pinnedNamespaces.contains(key));assertEquals(key,p.selected);assertTrue(p.physics.bodies.get(original).pinned);assertTrue(p.physics.bodies.get(GraphInteractionTest.named(p,"/ns24/joined")).pinned);p.namespace.setText("");assertTrue(p.physics.bodies.get(original).pinned);p.namespace.setText("/ns24");JSONObject removed=sample().put("nodes",new JSONArray().put("/ns00/node"));p.update(removed);assertFalse(p.pinnedNamespaces.contains(key));assertEquals("",p.selected);assertFalse(p.stage.virtualIds.containsKey(key));
 }
 @Test public void draggingNamespaceMovesItsFamilyWithoutPanningAndCancelReleasesHolds() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.update(sample());while(p.physics.awake)p.advancePhysics();p.physicsToggle.setChecked(false);p.stage.layout(0,0,1100,620);p.stage.fit(false);
  GraphNamespaces.Circle group=p.namespaceCircles(p.visibleVertices()).get("/robo/navigation");float x=(float)group.x*p.stage.scale+p.stage.tx,y=(float)(group.y-group.r)*p.stage.scale+p.stage.ty;
  assertEquals("Namespace rim must be a real touch target","ns:/robo/navigation",p.stage.hit(x,y));Map<String,RectF> before=new LinkedHashMap<>();for(String id:p.positions.keySet())before.put(id,new RectF(p.positions.get(id)));float tx=p.stage.tx,scale=p.stage.scale;
  GraphInteractionTest.gesture(p.stage,x,y,70,25);assertEquals(tx,p.stage.tx,0);
  for(String id:group.members)assertEquals(before.get(id).centerX()+70/scale,p.positions.get(id).centerX(),.02);
  for(String id:p.positions.keySet())if(!group.members.contains(id))assertEquals(before.get(id),p.positions.get(id));
  android.view.MotionEvent down=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_DOWN,x+70,y+25,0);p.stage.onTouchEvent(down);down.recycle();android.view.MotionEvent cancel=android.view.MotionEvent.obtain(0,20,android.view.MotionEvent.ACTION_CANCEL,x+70,y+25,0);p.stage.onTouchEvent(cancel);cancel.recycle();for(GraphPhysics.Body b:p.physics.bodies.values())assertFalse(b.held);
 }
 @Test public void namespacePinPreservesIndividualPinsAndSurvivesARealMetadataRefresh() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());org.json.JSONObject data=sample();p.update(data);while(p.physics.awake)p.advancePhysics();String planner=GraphInteractionTest.named(p,"/robo/navigation/planner");p.select(planner);p.pinButton.performClick();assertTrue(p.pinned.contains(planner));p.select("ns:/robo/navigation");assertTrue("A namespace needs its own group-pin action",p.pinButton.isEnabled());p.pinButton.performClick();
  for(String id:p.namespaceCircles(p.visibleVertices()).get("/robo/navigation").members)assertTrue(p.physics.bodies.get(id).pinned);
  data.getJSONArray("nodes").put("/robo/navigation/new_member");p.update(data);assertEquals("ns:/robo/navigation",p.selected);assertTrue(p.physics.bodies.get(GraphInteractionTest.named(p,"/robo/navigation/new_member")).pinned);
  p.pinButton.performClick();assertTrue(p.physics.bodies.get(planner).pinned);assertFalse(p.physics.bodies.get(GraphInteractionTest.named(p,"/robo/navigation/controller")).pinned);p.clear("stop");assertTrue(p.physics.namespaces.isEmpty());assertTrue(p.pinned.isEmpty());
 }
 @Test public void namespacesHaveAccessibleActionsAndRetireWhenTheirMembersDisappear() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.update(sample());p.stage.layout(0,0,1100,620);p.stage.fit(false);String key="ns:/robo/navigation";assertTrue("Namespace needs an accessible virtual target",p.stage.virtualIds.containsKey(key));int id=p.stage.virtualIds.get(key);android.view.accessibility.AccessibilityNodeProvider provider=p.stage.getAccessibilityNodeProvider();android.view.accessibility.AccessibilityNodeInfo info=provider.createAccessibilityNodeInfo(id);assertNotNull(info);assertTrue(info.getContentDescription().toString().contains("Namespace"));assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null));assertEquals(key,p.selected);assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null));
  JSONObject next=sample().put("nodes",new JSONArray().put("/robo/perception/camera"));p.update(next);assertFalse(p.stage.virtualIds.containsKey(key));assertEquals(-1,p.stage.accessibilityFocus);assertNull(provider.createAccessibilityNodeInfo(id));
 }
 @Test public void namespaceCircleIsSelectableWithoutInventingRuntimeVerticesOrPackages() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.update(sample());p.stage.layout(0,0,1100,620);while(p.physics.awake)p.advancePhysics();
  Map<String,GraphNamespaces.Circle> groups=p.namespaceCircles(p.visibleVertices());assertEquals(2,groups.size());assertEquals(2,groups.get("/robo/navigation").members.size());assertEquals(3,p.vertices.size());for(JSONObject v:p.vertices.values())assertEquals("node",v.optString("kind"));
  Map<String,RectF> before=new LinkedHashMap<>();for(String id:p.positions.keySet())before.put(id,new RectF(p.positions.get(id)));int steps=p.physics.steps;
  p.select("ns:/robo/navigation");assertEquals("ns:/robo/navigation",p.selected);assertTrue(p.selection.getText().toString().contains("Namespace"));assertTrue(p.details.getText().toString().contains("/robo/navigation"));assertTrue(p.details.getText().toString().contains("não"));assertEquals(before,p.positions);assertEquals(steps,p.physics.steps);assertFalse(p.physics.awake);
 }
}
