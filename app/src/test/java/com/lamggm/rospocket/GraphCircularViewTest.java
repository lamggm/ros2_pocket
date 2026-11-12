package com.lamggm.rospocket;

import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

/** Actual Android Canvas; synthetic discovery only in tests, never in the APK. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31,qualifiers="w1280dp-h850dp")
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GraphCircularViewTest {
 static final class ShapeCanvas extends Canvas {
  int circles,boxes;float smallest=Float.MAX_VALUE;
  final java.util.List<RectF> textBoxes=new java.util.ArrayList<>();final java.util.List<String> textValues=new java.util.ArrayList<>();
  ShapeCanvas(){super(Bitmap.createBitmap(1100,620,Bitmap.Config.ARGB_8888));}
  @Override public void drawCircle(float x,float y,float r,Paint p){circles++;smallest=Math.min(smallest,r);super.drawCircle(x,y,r,p);}
  @Override public void drawRoundRect(RectF r,float x,float y,Paint p){boxes++;super.drawRoundRect(r,x,y,p);}
  @Override public void drawText(String text,float x,float y,Paint p){if(!text.isEmpty()){textValues.add(text);Paint.FontMetrics f=p.getFontMetrics();textBoxes.add(new RectF(x,y+f.ascent,x+p.measureText(text),y+f.descent));}super.drawText(text,x,y,p);}
 }
 @Test public void denseNativeSceneDoesNotStackItsExternalLabels() throws Exception {RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.update(GraphCircularPreviewTest.galaxy());while(p.physics.awake)p.advancePhysics();p.stage.layout(0,0,1100,620);p.stage.fit(false);ShapeCanvas c=new ShapeCanvas();p.stage.draw(c);assertTrue("Namespace names remain available in the overview",c.textBoxes.size()>=6);for(int i=0;i<c.textBoxes.size();i++)for(int j=i+1;j<c.textBoxes.size();j++)assertFalse("Native labels overlap: "+i+" / "+j,RectF.intersects(c.textBoxes.get(i),c.textBoxes.get(j)));}
 @Test public void dotsStayVisibleAtOverviewZoom() throws Exception {RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.update(RosGraphModelTest.fixture());p.stage.layout(0,0,1100,620);p.stage.scale=.1f;ShapeCanvas c=new ShapeCanvas();p.stage.draw(c);assertTrue("Circular glyphs need at least a six-dp screen radius",c.smallest*p.stage.scale>=p.dp(6)-.01);}
 @Test public void nativeCanvasDrawsNamespaceBubblesAroundItsRealVertices() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.update(GraphNamespaceSceneTest.sample());while(p.physics.awake)p.advancePhysics();p.stage.layout(0,0,1100,620);p.stage.fit(false);ShapeCanvas c=new ShapeCanvas();p.stage.draw(c);
  assertTrue("Native scene needs namespace and enclosing-circle strokes, not only round nodes",c.circles>=p.visibleVertices().size()*2+3);assertEquals(0,c.boxes);
 }
 @Test public void directedEdgesMeetTheActualCircularBoundary() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.update(RosGraphModelTest.fixture());
  String node=GraphInteractionTest.named(p,"/talker"),topic=GraphInteractionTest.named(p,"/value");
  p.positions.put(node,new RectF(0,0,260,68));p.positions.put(topic,new RectF(0,500,260,568));
  org.json.JSONObject edge=null;for(org.json.JSONObject e:p.edges.values())if(e.optString("from").equals(node)&&e.optString("to").equals(topic))edge=e;
  assertNotNull(edge);PointF[] ends=p.stage.ends(edge);
  assertEquals(18,Math.hypot(ends[0].x-130,ends[0].y-34),.01);
  assertEquals(14,Math.hypot(ends[1].x-130,ends[1].y-534),.01);
 }
 @Test public void actualStageUsesCircularGlyphsWithoutGraphCards() throws Exception {
  RosGraphPanel p=new RosGraphPanel(RuntimeEnvironment.getApplication());p.update(RosGraphModelTest.fixture());p.physicsToggle.setChecked(false);p.stage.layout(0,0,1100,620);p.stage.fit(false);
  ShapeCanvas canvas=new ShapeCanvas();p.stage.draw(canvas);
  assertEquals("Approved graph has circles, not rounded cards",0,canvas.boxes);
  assertTrue("Every observed vertex needs a native circular glyph",canvas.circles>=p.visibleVertices().size());
 }
}
