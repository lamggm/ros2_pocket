package com.lamggm.rospocket;

import android.graphics.Bitmap;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** Opt-in replay of REAL separate-process DDS snapshots, not Android/JNI intake.
 * The model, View and native host Canvas are production implementations. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PlotDdsReplayTest {
 @Test public void bothRealRmwStreamsTraverseModelWindowAndCompletedCanvas()throws Exception {
  String dir=System.getenv("POCKET_PLOT_DDS_RECORDS");Assume.assumeTrue("Opt-in real DDS records required",dir!=null);
  for(String rmw:new String[]{"rmw_fastrtps_cpp","rmw_cyclonedds_cpp"})replay(Paths.get(dir,rmw),rmw);
 }
 private void replay(Path dir,String rmw)throws Exception {
  JSONObject result=new JSONObject(new String(Files.readAllBytes(dir.resolve("result.json")),java.nio.charset.StandardCharsets.UTF_8));assertTrue(result.getBoolean("passed"));
  assertEquals(rmw,result.getString("rmw"));
  PlotController c=new PlotController();PlotView v=new PlotView(RuntimeEnvironment.getApplication(),c);
  boolean selected=false,retired=false,returned=false,resetBreak=false,windowEvicted=false;
  long initialOldest=-1,lastReceive=-1,previousSource=-1;String priorPhase="";
  Set<String> phases=new HashSet<>();Map<String,Integer> nonfinite=new HashMap<>();
  int draws=0,finiteFrames=0;Map<Integer,String> watches=new HashMap<>();
  Bitmap frozenPixels=null;List<PlotHistory.Series> frozenSeries=null;
  for(String line:Files.readAllLines(dir.resolve("snapshots.jsonl"))){
   JSONObject record=new JSONObject(line),nativeRow=record.getJSONObject("snapshot");String phase=record.getString("phase");
   c.acceptNative(nativeRow,1,false);phases.add(phase);
   if(!selected){
    for(int slot=0;slot<3;slot++){
     String watch=PlotController.watchName(slot,nativeRow.getJSONArray("watches").getJSONObject(slot));watches.put(slot,watch);assertTrue(c.select(watch,"data"));
    }
    selected=true;continue;
   }
   if(phase.equals("finite")){
    finiteFrames++;v.refresh();
    if(finiteFrames==2){
     v.findViewById(R.id.plot_pause).performClick();frozenPixels=PlotQualificationTest.draw(v);frozenSeries=v.displayed();
    }
    if(finiteFrames==6){
     assertTrue("Real DDS intake must advance while the Canvas stays frozen",frozenPixels.sameAs(PlotQualificationTest.draw(v)));
     assertTrue(c.history.points(watches.get(1)+"\ndata").size()>frozenSeries.get(1).points.size());
     v.findViewWithTag("plot-field:"+watches.get(0)+"\ndata").performClick();
     assertEquals(2,v.displayed().size());assertSame(frozenSeries.get(1),v.displayed().get(0));
     assertSame(frozenSeries.get(2),v.displayed().get(1));
    }
    if(finiteFrames==8){
     v.findViewById(R.id.plot_pause).performClick();assertTrue(c.select(watches.get(0),"data"));v.refresh();
     assertFalse(frozenPixels.sameAs(PlotQualificationTest.draw(v)));
    }
   }
   if(phase.equals("gap")&&!nativeRow.getJSONArray("watches").getJSONObject(0).getBoolean("fresh")){
    assertFalse("A stale source retires selection rather than synthesizing a gap at poll time",c.selected(watches.get(0)+"\ndata"));
    assertTrue(c.selected(watches.get(1)+"\ndata"));retired=true;
   }
   if(phase.equals("return_after_gap")&&!returned){
    assertTrue(retired);assertFalse("Returned native data cannot resurrect a retired series",c.selected(watches.get(0)+"\ndata"));
    assertTrue(c.select(watches.get(0),"data"));assertTrue(c.select(watches.get(2),"data"));returned=true;
   }
   if(phase.equals("removed")||phase.equals("reassigned")){
    assertFalse(c.selected(watches.get(0)+"\ndata"));assertTrue(c.selected(watches.get(1)+"\ndata"));
   }
   JSONObject stamped=nativeRow.getJSONArray("watches").getJSONObject(2);
   if(stamped.getBoolean("fresh")){
    long receive=stamped.getLong("receivedMonotonicNs"),source=stamped.getLong("sourceTimestampNs");
    assertTrue("Receive-monotonic X never follows backwards source",receive>=lastReceive);
    List<PlotHistory.Point> points=c.history.points(watches.get(2)+"\ndata");
    if(phase.equals("source_reset")&&!priorPhase.equals(phase)){
     assertTrue(source<previousSource);assertFalse(points.isEmpty());assertTrue(points.get(points.size()-1).breakBefore);resetBreak=true;
    }
    if(phase.equals("source_jump")&&!priorPhase.equals(phase))assertTrue(source>previousSource);
    lastReceive=receive;previousSource=source;
   }
   for(PlotHistory.Series s:c.history.snapshot()){
    assertTrue(s.points.size()<=600);
    if(!s.points.isEmpty()){
     assertTrue(s.points.get(s.points.size()-1).time-s.points.get(0).time<=30_000_000_000L);
     if(s.id.equals(watches.get(1)+"\ndata")){
      if(initialOldest<0)initialOldest=s.points.get(0).time;
      if(phase.equals("window")&&s.points.get(0).time>initialOldest)windowEvicted=true;
     }
    }
   }
   if(Arrays.asList("nan","positive_infinity","negative_infinity").contains(phase)){
    List<PlotHistory.Point> ps=c.history.points(watches.get(0)+"\ndata");
    assertFalse(ps.isEmpty());assertNull("Nonfinite typed samples remain gaps, not zero",ps.get(ps.size()-1).value);
    nonfinite.put(phase,nonfinite.getOrDefault(phase,0)+1);
   }
   if(!phase.equals(priorPhase)||phase.equals("window")){
    v.refresh();Bitmap frame=PlotQualificationTest.draw(v);assertNotNull(frame);draws++;
    try(java.io.OutputStream png=Files.newOutputStream(dir.resolve("canvas-"+phase+".png"))){
     assertTrue(frame.compress(Bitmap.CompressFormat.PNG,100,png));
    }
    JSONObject qa=v.qaState(System.nanoTime());assertFalse(qa.getBoolean("drawPending"));
    assertEquals(qa.getLong("preparedRevision"),qa.getLong("drawnRevision"));
    assertEquals(qa.getJSONArray("series").toString(),qa.getJSONArray("drawnSeries").toString());
    for(int i=0;i<qa.getJSONArray("drawnSeries").length();i++){
     JSONObject s=qa.getJSONArray("drawnSeries").getJSONObject(i);
     // reset() preserves bounded selected controls but empties their histories.
     // An empty old identity is not rendering/intake evidence and cannot qualify.
     if(s.getInt("pointCount")==0)continue;
     assertEquals(nativeRow.getLong("sessionGeneration"),s.getLong("sessionGeneration"));
     assertEquals(nativeRow.getJSONArray("watches").getJSONObject(s.getInt("slot")).getLong("generation"),s.getLong("readerGeneration"));
    }
   }
   priorPhase=phase;
  }
  for(String phase:new String[]{"nan","positive_infinity","negative_infinity"})assertTrue(nonfinite.getOrDefault(phase,0)>=5);
  assertTrue(returned&&resetBreak&&windowEvicted);assertTrue(draws>60);
  assertTrue(phases.containsAll(Arrays.asList("finite","gap","return_after_gap","source_reset","source_jump","window","removed","reassigned","stop")));
  for(PlotHistory.Series s:c.history.snapshot())assertTrue("Session stop clears every retained point",s.points.isEmpty());
  System.out.println("REAL_DDS_CANVAS_REPLAY "+rmw+" completedDraws="+draws+" realWindowEviction=true boundary600=separate-model-test");
 }
}
