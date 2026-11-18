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
 static JSONObject missionSample() throws Exception {
  return sample().put("nodes",new JSONArray().put("/gb002/mission/coordinator")
    .put("/gb002/mission/coverage/planner").put("/gb002/mission_extra/worker").put("/root_node"))
    .put("topics",new JSONArray().put(RosGraphModelTest.topic("/gb002/mission/coverage/status","std_msgs/msg/String",
    new JSONArray().put(RosGraphModelTest.endpoint("pub","test:coverage",true,"/gb002/mission/coverage/planner"))
      .put(RosGraphModelTest.endpoint("sub","test:mission",false,"/gb002/mission/coordinator")))));
 }
 @Test public void missionBubbleEnclosesCoverageAndKeepsExactNamesAndConnections() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());
  JSONObject snapshot=missionSample();
  p.update(snapshot);
  String coordinator=GraphInteractionTest.named(p,"/gb002/mission/coordinator");
  String planner=GraphInteractionTest.named(p,"/gb002/mission/coverage/planner");
  String status=GraphInteractionTest.named(p,"/gb002/mission/coverage/status");
  String other=GraphInteractionTest.named(p,"/gb002/mission_extra/worker");
  while(p.physics.awake)p.advancePhysics();
  Map<String,GraphNamespaces.Circle> groups=p.namespaceCircles(p.visibleVertices());
  GraphNamespaces.Circle mission=groups.get("/gb002/mission"),coverage=groups.get("/gb002/mission/coverage");
  assertEquals(new LinkedHashSet<>(Arrays.asList(coordinator,planner,status)),mission.members);
  assertEquals(new LinkedHashSet<>(Arrays.asList(planner,status)),coverage.members);
  assertTrue("The whole coverage rim belongs inside mission",Math.hypot(coverage.x-mission.x,coverage.y-mission.y)+coverage.r<mission.r);
  RectF direct=p.positions.get(coordinator);
  assertTrue("A direct mission node must not appear to belong to coverage",Math.hypot(direct.centerX()-coverage.x,direct.centerY()-coverage.y)>=coverage.r+40);
  GraphNamespaces.Circle sibling=groups.get("/gb002/mission_extra");
  assertTrue("A similar prefix is not a child and its envelope must stay separate",Math.hypot(sibling.x-mission.x,sibling.y-mission.y)>=sibling.r+mission.r+39);
  assertEquals("/gb002/mission/coverage",p.namespaceOfVertex(planner));
  assertEquals(5,p.vertices.size());assertEquals(2,p.edges.size());
  p.select("ns:/gb002/mission");assertTrue(p.focused.containsAll(mission.members));assertFalse(p.focused.contains(other));
  assertEquals(p.edges.keySet(),p.focusedEdges);assertTrue(p.details.getText().toString().contains("/gb002/mission/coverage/status"));
  p.pinButton.performClick();assertTrue(p.physics.bodies.get(coordinator).pinned);assertTrue(p.physics.bodies.get(planner).pinned);assertFalse(p.physics.bodies.get(other).pinned);
  snapshot.getJSONArray("nodes").put("/gb002/mission/coverage/new_worker");p.update(snapshot);
  assertTrue(p.physics.bodies.get(GraphInteractionTest.named(p,"/gb002/mission/coverage/new_worker")).pinned);
  p.pinButton.performClick();assertFalse(p.physics.bodies.get(planner).pinned);
 }
 static JSONObject navigationSample(boolean crowded) throws Exception {
  JSONObject data=missionSample();JSONArray names=data.getJSONArray("nodes");
  if(crowded)for(int i=0;i<24;i++)names.put(String.format(Locale.ROOT,"/a%02d/node",i));
  names.put("/gb002/navigation/sensors/scan_adapter").put("/gb002/navigation/path_follower/follower")
    .put("/gb002/navigation/teleop/joy_selector/selector").put("/gb002/navigation/teleop/api_teleop/adapter")
    .put("/gb002/navigation/teleop/joy_to_twist/converter").put("/gb002/navigation_extra/worker");
  data.getJSONArray("topics").put(RosGraphModelTest.topic("/gb002/navigation/sensors/scan","sensor_msgs/msg/LaserScan",
    new JSONArray().put(RosGraphModelTest.endpoint("scan-pub","test:sensors",true,"/gb002/navigation/sensors/scan_adapter"))
      .put(RosGraphModelTest.endpoint("scan-sub","test:path",false,"/gb002/navigation/path_follower/follower"))));
  return data;
 }
 @Test public void navigationWithoutDirectMembersContainsEveryBranchInTheUnfilteredView() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.update(navigationSample(false));while(p.physics.awake)p.advancePhysics();
  String nav="/gb002/navigation",teleop=nav+"/teleop"; Map<String,GraphNamespaces.Circle> groups=p.namespaceCircles(p.visibleVertices());
  GraphNamespaces.Circle parent=groups.get(nav);assertNotNull("Navigation needs its own bubble even without direct vertices",parent);
  for(String child:Arrays.asList(nav+"/sensors",nav+"/path_follower",teleop,teleop+"/joy_selector",teleop+"/api_teleop",teleop+"/joy_to_twist")){
   GraphNamespaces.Circle c=groups.get(child),owner=child.startsWith(teleop+"/")?groups.get(teleop):parent;
   assertNotNull(child,c);assertTrue(child,Math.hypot(c.x-owner.x,c.y-owner.y)+c.r<owner.r);
  }
  assertEquals(6,parent.members.size());assertEquals(3,groups.get(teleop).members.size());
  assertFalse(parent.members.contains(GraphInteractionTest.named(p,"/gb002/navigation_extra/worker")));
  assertEquals(12,p.vertices.size());assertEquals(4,p.edges.size());
  p.namespace.setText(nav);assertEquals("Filter by whole path segments, not text prefix",parent.members,p.visibleVertices());
  assertEquals(4,p.edges.size());
 }
 @Test public void inferredParentGetsAnAccessibleSlotAheadOfLeafOnlyGroupsAndKeepsItsPin() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());JSONObject snapshot=navigationSample(true);p.update(snapshot);p.stage.layout(0,0,1100,620);
  String nav="/gb002/navigation",key="ns:"+nav;
  Map<String,GraphNamespaces.Circle> groups=p.namespaceCircles(p.visibleVertices());assertEquals(GraphNamespaces.MAX_GROUPS,groups.size());
  assertNotNull("The overview cap must not starve a parent branch",groups.get(nav));assertEquals(6,groups.get(nav).members.size());
  assertNotNull(p.stage.virtualIds.get(key));int id=p.stage.virtualIds.get(key);android.view.accessibility.AccessibilityNodeProvider provider=p.stage.getAccessibilityNodeProvider();
  assertNotNull(provider.createAccessibilityNodeInfo(id));assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null));
  assertEquals(key,p.selected);p.pinButton.performClick();assertTrue(p.pinnedNamespaces.contains(key));
  snapshot.getJSONArray("nodes").put(nav+"/other_module/joined");p.update(snapshot);
  assertEquals(Integer.valueOf(id),p.stage.virtualIds.get(key));assertEquals(key,p.selected);assertTrue(p.physics.bodies.get(GraphInteractionTest.named(p,nav+"/other_module/joined")).pinned);
  assertFalse(p.physics.bodies.get(GraphInteractionTest.named(p,"/gb002/navigation_extra/worker")).pinned);
  p.update(sample());assertFalse(p.stage.virtualIds.containsKey(key));assertFalse(p.pinnedNamespaces.contains(key));
 }
 @Test public void filteredNamespaceBeyondRenderCapHasStableAccessibleIdentity() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());JSONObject data=crowded();p.update(data);p.stage.layout(0,0,1100,620);assertEquals(25,p.vertices.size());assertEquals(24,p.namespaceCircles(p.visibleVertices()).size());p.namespace.setText("/ns24");String key="ns:/ns24";android.view.accessibility.AccessibilityNodeProvider provider=p.stage.getAccessibilityNodeProvider();android.view.accessibility.AccessibilityNodeInfo host=provider.createAccessibilityNodeInfo(android.view.View.NO_ID);assertNotNull(host);assertNotNull(p.stage.virtualIds.get(key));int id=p.stage.virtualIds.get(key);assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null));assertEquals(key,p.selected);assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null));p.namespace.setText("");assertEquals(Integer.valueOf(id),p.stage.virtualIds.get(key));p.namespace.setText("/ns24");data.getJSONArray("nodes").put("/ns24/joined");p.update(data);assertEquals(Integer.valueOf(id),p.stage.virtualIds.get(key));assertNotNull(provider.createAccessibilityNodeInfo(id));
 }
 static RosGraphPanel zoomedMission() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());
  p.update(missionSample().put("topics",new JSONArray()));p.physicsToggle.setChecked(false);
  p.stage.layout(0,0,1100,620);p.stage.scale=.20f;p.stage.tx=150;p.stage.ty=230;
  for(String id:p.positions.keySet()){
   float x=p.label(id).endsWith("/planner")?250:p.label(id).endsWith("/coordinator")?620:p.label(id).endsWith("/worker")?2500:3500;
   p.positions.get(id).offsetTo(x-130,-34);p.physics.move(id,x,0);
  }
  return p;
 }
 static void tap(RosGraphPanel.Stage stage,float x,float y){
  long t=android.os.SystemClock.uptimeMillis();for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}){
   android.view.MotionEvent e=android.view.MotionEvent.obtain(t,t,action,x,y,0);stage.onTouchEvent(e);e.recycle();
  }
 }
 @Test public void exactChildRimWinsTouchAndDragWhenParentRimIsWithinTouchSlop() throws Exception {
  RosGraphPanel p=zoomedMission();String name="/gb002/mission/coverage",parent="/gb002/mission";
  Map<String,GraphNamespaces.Circle> groups=p.namespaceCircles(p.visibleVertices());
  GraphNamespaces.Circle c=groups.get(name),m=groups.get(parent);
  float x=(float)(c.x-c.r)*p.stage.scale+p.stage.tx,y=(float)c.y*p.stage.scale+p.stage.ty;
  assertTrue(Math.abs((c.x-c.r)-(m.x-m.r))*p.stage.scale<p.dp(12));
  tap(p.stage,x,y);assertEquals("ns:"+name,p.selected);
  String planner=GraphInteractionTest.named(p,name+"/planner"),coordinator=GraphInteractionTest.named(p,parent+"/coordinator");
  RectF direct=new RectF(p.positions.get(coordinator));float before=p.positions.get(planner).centerX(),pan=p.stage.tx;
  GraphInteractionTest.gesture(p.stage,x,y,-35,35);
  assertEquals(direct,p.positions.get(coordinator));assertEquals(before-35/p.stage.scale,p.positions.get(planner).centerX(),.01);assertEquals(pan,p.stage.tx,0);
 }
 @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) public void namespaceTitleHitUsesItsDrawnFallbackPosition() throws Exception {
  RosGraphPanel p=zoomedMission();p.stage.ty=29;String key="ns:/gb002/mission/coverage";
  Bitmap bm=Bitmap.createBitmap(1100,620,Bitmap.Config.ARGB_8888);p.stage.draw(new Canvas(bm));
  RectF drawn=p.stage.labelRects.get(key);assertNotNull(drawn);
  assertTrue("Title must use a fallback below the center",drawn.centerY()>0);
  tap(p.stage,(drawn.right-2)*p.stage.scale+p.stage.tx,(drawn.bottom-2)*p.stage.scale+p.stage.ty);
  assertEquals(key,p.selected);bm.recycle();
 }
 @Test public void childNamespaceAndNodesStayFixedAndExplainTheirInheritedPin() throws Exception {
  RosGraphPanel p=zoomedMission();String parent="ns:/gb002/mission",child="ns:/gb002/mission/coverage";
  String planner=GraphInteractionTest.named(p,"/gb002/mission/coverage/planner");
  p.select(child);p.pinButton.performClick();p.select(parent);p.pinButton.performClick();
  p.select(child);assertTrue(p.pinButton.isEnabled());p.pinButton.performClick();
  assertFalse(p.pinButton.isEnabled());assertTrue(p.pinButton.getText().toString().contains("Fixado pelo namespace"));
  assertTrue(p.physics.bodies.get(planner).pinned);
  RectF before=new RectF(p.positions.get(planner)),screen=p.stage.screenRect(planner);float pan=p.stage.tx;
  GraphInteractionTest.gesture(p.stage,screen.centerX(),screen.centerY(),35,25);
  assertEquals(before,p.positions.get(planner));assertEquals(pan,p.stage.tx,0);
  p.select(parent);p.pinButton.performClick();assertFalse(p.physics.bodies.get(planner).pinned);
 }
 @Test public void namespaceShownByOnlyFocusRemainsSelectableWhenBeyondOverviewCap() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.update(crowded());p.stage.layout(0,0,1100,620);
  String key="ns:/ns24",node=GraphInteractionTest.named(p,"/ns24/node");
  assertFalse(p.namespaceCircles(p.visibleVertices()).containsKey("/ns24"));
  p.select(node);p.onlyFocus.setChecked(true);
  int id=p.stage.virtualIds.get(key);android.view.accessibility.AccessibilityNodeProvider provider=p.stage.getAccessibilityNodeProvider();
  assertNotNull(provider.createAccessibilityNodeInfo(id));assertTrue(provider.performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null));
  assertEquals(key,p.selected);assertEquals(Collections.singleton(node),p.visibleVertices());assertNotNull(provider.createAccessibilityNodeInfo(id));
  p.onlyFocus.setChecked(false);assertTrue(p.namespaceCircles(p.visibleVertices()).containsKey("/ns24"));
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
  Map<String,GraphNamespaces.Circle> groups=p.namespaceCircles(p.visibleVertices());assertEquals(new LinkedHashSet<>(Arrays.asList("/robo","/robo/navigation","/robo/perception")),groups.keySet());assertEquals(2,groups.get("/robo/navigation").members.size());assertEquals(3,p.vertices.size());for(JSONObject v:p.vertices.values())assertEquals("node",v.optString("kind"));
  Map<String,RectF> before=new LinkedHashMap<>();for(String id:p.positions.keySet())before.put(id,new RectF(p.positions.get(id)));int steps=p.physics.steps;
  p.select("ns:/robo/navigation");assertEquals("ns:/robo/navigation",p.selected);assertTrue(p.selection.getText().toString().contains("Namespace"));assertTrue(p.details.getText().toString().contains("/robo/navigation"));assertTrue(p.details.getText().toString().contains("não"));assertEquals(before,p.positions);assertEquals(steps,p.physics.steps);assertFalse(p.physics.awake);
 }
}
