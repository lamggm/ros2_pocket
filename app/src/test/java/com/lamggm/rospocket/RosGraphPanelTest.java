package com.lamggm.rospocket;

import android.graphics.*;
import android.view.*;
import android.content.Context;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31,qualifiers="night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RosGraphPanelTest {
 RosGraphPanel p;
 @Before public void setup()throws Exception{p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.physicsToggle.setChecked(false);p.update(RosGraphModelTest.fixture());p.stage.layout(0,0,1100,480);p.stage.fit(false);}
 String named(String label){for(String id:p.vertices.keySet())if(p.label(id).equals(label))return id;throw new AssertionError(label);}
 void tap(float x,float y){long t=android.os.SystemClock.uptimeMillis();MotionEvent d=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,x,y,0),u=MotionEvent.obtain(t,t+50,MotionEvent.ACTION_UP,x,y,0);p.stage.onTouchEvent(d);p.stage.onTouchEvent(u);d.recycle();u.recycle();}
 Bitmap draw(){Bitmap b=Bitmap.createBitmap(1100,480,Bitmap.Config.ARGB_8888);p.stage.draw(new Canvas(b));return b;}
 @Test public void touchDimsUnrelatedConnectionsAndPreservesGeometryAndViewport()throws Exception{
  String talker=named("/talker"),other=named("/other");java.util.Map<String,RectF> before=new java.util.LinkedHashMap<>();for(String id:p.positions.keySet())before.put(id,new RectF(p.positions.get(id)));
  float scale=p.stage.scale,tx=p.stage.tx,ty=p.stage.ty;RectF target=p.stage.screenRect(talker);Bitmap full=draw();tap(target.centerX(),target.centerY());
  assertEquals(talker,p.selected);assertTrue(p.focused.contains(named("/listener")));assertFalse(p.focused.contains(other));assertEquals(before,p.positions);assertEquals(scale,p.stage.scale,0);assertEquals(tx,p.stage.tx,0);assertEquals(ty,p.stage.ty,0);
  Bitmap focused=draw();RectF r=p.stage.screenRect(other);int x=Math.round(r.centerX()),y=Math.round(r.centerY());assertNotEquals(full.getPixel(x,y),focused.getPixel(x,y));
  String preview=System.getenv("POCKET_GRAPH_PREVIEW");if(preview!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(preview)){focused.compress(Bitmap.CompressFormat.PNG,100,out);}}
  p.update(RosGraphModelTest.fixture());assertEquals(talker,p.selected);assertEquals(before,p.positions);assertEquals(scale,p.stage.scale,0);
  tap(1,1);assertEquals("",p.selected);assertTrue(p.focused.isEmpty());
 }
 @Test public void topicAndEdgeTapsShowDirectionAndEndpointQos()throws Exception{
  String topic=named("/value");RectF r=p.stage.screenRect(topic);tap(r.centerX(),r.centerY());assertEquals(topic,p.selected);assertTrue(p.focused.contains(named("/talker")));assertTrue(p.focused.contains(named("/listener")));
  JSONObject e=p.edges.values().iterator().next();PointF[] ends=p.stage.ends(e);float x=(ends[0].x+ends[1].x)/2*p.stage.scale+p.stage.tx,y=(ends[0].y+ends[1].y)/2*p.stage.scale+p.stage.ty;tap(x,y);assertTrue(p.selected.startsWith("e:"));assertTrue(p.details.getText().toString().contains("QoS anunciado"));
 }
 @Test public void panZoomSearchAndRefreshDoNotRebuildTheScene()throws Exception{
  float s=p.stage.scale,x=p.stage.tx;p.findViewById(R.id.graph_zoom_in).performClick();assertTrue(p.stage.scale>s);
  long t=android.os.SystemClock.uptimeMillis();for(int i=0;i<3;i++){MotionEvent e=MotionEvent.obtain(t,t+10*i,i==0?MotionEvent.ACTION_DOWN:i==1?MotionEvent.ACTION_MOVE:MotionEvent.ACTION_UP,100+80*i,100,0);p.stage.onTouchEvent(e);e.recycle();}assertNotEquals(x,p.stage.tx,0);
  p.search.setText("/listener");p.find();assertEquals(named("/listener"),p.selected);
  RectF pos=new RectF(p.positions.get(named("/listener")));JSONObject next=RosGraphModelTest.fixture();next.getJSONArray("nodes").put("/aaa_new");p.update(next);assertEquals(pos,p.positions.get(named("/listener")));
 }
 @Test public void filterAndRemovedSelectionAreExplicitAndClearRetiresAllState()throws Exception{
  JSONObject s=RosGraphModelTest.fixture();s.getJSONArray("topics").put(RosGraphModelTest.topic("/rosout","rcl_interfaces/msg/Log",new JSONArray().put(RosGraphModelTest.endpoint("log","p1",true,"/talker"))));p.update(s);
  String rosout=named("/rosout");assertFalse(p.visibleVertices().contains(rosout));p.infrastructure.performClick();assertTrue(p.visibleVertices().contains(rosout));p.select(rosout);p.infrastructure.performClick();assertEquals("",p.selected);
  p.select(named("/listener"));s.getJSONArray("topics").getJSONObject(0).put("endpoints",new JSONArray().put(RosGraphModelTest.endpoint("w","p1",true,"/talker")));s.put("nodes",new JSONArray().put("/talker").put("/other"));p.update(s);assertEquals("",p.selected);
  p.clear("OFFLINE");assertTrue(p.vertices.isEmpty());assertTrue(p.positions.isEmpty());assertTrue(p.edges.isEmpty());assertEquals("OFFLINE",p.summary.getText().toString());
 }
 @Test public void sceneIsBoundedAndDoesNotClaimCompleteness()throws Exception{
  JSONObject s=RosGraphModelTest.fixture();JSONArray nodes=s.getJSONArray("nodes");for(int i=0;i<300;i++)nodes.put("/node_"+i);p.update(s);assertTrue(p.vertices.size()<=160);assertTrue(p.summary.getText().toString().contains("VISÃO PARCIAL"));
 }
 @Test public void oppositeDirectionsHaveDistinctTouchablePaths()throws Exception{
  JSONObject s=RosGraphModelTest.fixture();s.getJSONArray("topics").getJSONObject(0).getJSONArray("endpoints").put(RosGraphModelTest.endpoint("loop","p1",false,"/talker"));p.update(s);
  java.util.List<JSONObject> pair=new java.util.ArrayList<>();for(JSONObject e:p.edges.values())if((e.optString("from").equals(named("/talker"))||e.optString("to").equals(named("/talker")))&&e.optString("topic").equals(named("/value")))pair.add(e);
  assertEquals(2,pair.size());PointF[] one=p.stage.ends(pair.get(0)),two=p.stage.ends(pair.get(1));assertNotEquals("Opposing arrows cannot overlap",one[0].y,two[1].y,0);
  for(JSONObject e:pair){PointF[] ends=p.stage.ends(e);float x=(ends[0].x*.65f+ends[1].x*.35f)*p.stage.scale+p.stage.tx,y=(ends[0].y*.65f+ends[1].y*.35f)*p.stage.scale+p.stage.ty;assertEquals(e.optString("id"),p.stage.hit(x,y));}
 }
 @Test public void virtualFocusEmitsEventsAndIsClearedWhenTheElementDisappears()throws Exception{
  java.util.List<Integer> events=new java.util.ArrayList<>();android.widget.FrameLayout parent=new android.widget.FrameLayout(p.getContext()){
   @Override public boolean requestSendAccessibilityEvent(View child,android.view.accessibility.AccessibilityEvent event){events.add(event.getEventType());return true;}
  };parent.addView(p);int id=p.stage.virtualIds.get(named("/talker"));
  assertTrue(p.stage.getAccessibilityNodeProvider().performAction(id,android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null));
  assertTrue(events.contains(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED));
  p.clear("OFFLINE");assertEquals(-1,p.stage.accessibilityFocus);assertTrue(events.contains(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED));
 }
 @Test public void actualTwoPointerPinchChangesScaleWithoutSelectingANode()throws Exception{
  float before=p.stage.scale;long start=android.os.SystemClock.uptimeMillis();
  android.view.MotionEvent.PointerProperties[] properties=new android.view.MotionEvent.PointerProperties[2];
  for(int i=0;i<2;i++){properties[i]=new android.view.MotionEvent.PointerProperties();properties[i].id=i;properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;}
  for(int step=0;step<8;step++){
   int count=step==0?1:2;MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[count];
   for(int i=0;i<count;i++){coordinates[i]=new MotionEvent.PointerCoords();coordinates[i].x=i==0?400-step*20:600+step*20;coordinates[i].y=200;coordinates[i].pressure=1;coordinates[i].size=1;}
   int action=step==0?MotionEvent.ACTION_DOWN:step==1?MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT):step==7?MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT):MotionEvent.ACTION_MOVE;
   MotionEvent e=MotionEvent.obtain(start,start+step*30,action,count,properties,coordinates,0,0,1,1,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0);p.stage.onTouchEvent(e);e.recycle();
  }
  MotionEvent up=MotionEvent.obtain(start,start+250,MotionEvent.ACTION_UP,260,200,0);p.stage.onTouchEvent(up);up.recycle();assertTrue(p.stage.scale>before);assertEquals("",p.selected);
 }
 @Test public void panKeepsTheInitialSmallMotionOnceDraggingBegins()throws Exception{
  float before=p.stage.tx;long t=android.os.SystemClock.uptimeMillis();float[] xs={5,7,8,75};assertFalse("Background pan starts outside elements",p.vertices.containsKey(p.stage.hit(5,5)));
  for(int i=0;i<xs.length;i++){MotionEvent e=MotionEvent.obtain(t,t+i*20,i==0?MotionEvent.ACTION_DOWN:MotionEvent.ACTION_MOVE,xs[i],5,0);p.stage.onTouchEvent(e);e.recycle();}
  assertEquals(before+70,p.stage.tx,.1f);
 }
 @Test public void overviewZoomKeepsLabelsAndBordersReadableInScreenUnits() throws Exception {
  p.stage.scale=.3f;Bitmap b=draw();assertTrue("Overview labels need a minimum screen-space size",p.stage.pen.getTextSize()*p.stage.scale>=p.dp(12)-.01);assertTrue("Box borders should not vanish below a pixel",p.stage.pen.getStrokeWidth()*p.stage.scale>=p.dp(1)-.01);b.recycle();
 }
 @Test public void nodeHighlightDoesNotPileRoleLabelsOverConnections()throws Exception{
  p.select(named("/talker"));JSONObject e=null;for(JSONObject edge:p.edges.values())if(edge.optString("from").equals(named("/talker")))e=edge;assertNotNull(e);
  // Circular labels choose a collision-free pose; fixed card-era pixel rectangles are not their contract.
  GraphCircularViewTest.ShapeCanvas all=new GraphCircularViewTest.ShapeCanvas();p.stage.draw(all);assertEquals("Roles belong on the selected edge, not a pile over every branch",0,java.util.Collections.frequency(all.textValues,"Publica")+java.util.Collections.frequency(all.textValues,"Assina"));
  p.select(e.optString("id"));GraphCircularViewTest.ShapeCanvas selected=new GraphCircularViewTest.ShapeCanvas();p.stage.draw(selected);assertEquals("A selected edge still renders exactly its own role",1,java.util.Collections.frequency(selected.textValues,e.optString("role")));
 }
}
