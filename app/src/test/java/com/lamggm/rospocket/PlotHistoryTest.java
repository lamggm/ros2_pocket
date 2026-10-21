package com.lamggm.rospocket;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlotHistoryTest {
 @Test public void keepsBoundedCountAndWindowForFourOverlaySeries(){
  PlotHistory h=new PlotHistory(3,10);
  for(int i=0;i<4;i++)assertTrue(h.select("s"+i,"data",null));
  assertFalse(h.select("fifth","data",null));
  for(int i=0;i<20;i++)h.append("s0",i, i*1.0,null);
  assertEquals(3,h.points("s0").size());assertEquals(17,h.points("s0").get(0).time);
  h.append("s0",40,2.0,null);assertEquals(1,h.points("s0").size());
  assertTrue(h.remove("s1"));assertTrue(h.select("fifth","data",null));
 }
 @Test public void sustainedIntakeNeverExceedsCountBudgetAndSnapshotsAreImmutable(){
  PlotHistory h=new PlotHistory(600,30_000_000_000L);for(int i=0;i<4;i++)h.select("s"+i,"x",null);
  for(int n=1;n<=10000;n++)for(int i=0;i<4;i++)h.append("s"+i,n,(double)n,null);
  for(PlotHistory.Series s:h.snapshot())assertEquals(600,s.points.size());
  java.util.List<PlotHistory.Series> frozen=h.snapshot();h.clear();assertEquals(600,frozen.get(0).points.size());assertTrue(h.points("s0").isEmpty());
  try{frozen.get(0).points.clear();fail("Immutable presentation copy");}catch(UnsupportedOperationException expected){}
 }
 @Test public void nonfiniteAndReceiveGapsNeverBecomeInventedZero(){
  PlotHistory h=new PlotHistory(20,30_000_000_000L);h.select("s","x",null);
  h.append("s",1,5.0,100L);h.append("s",2,Double.NaN,101L);h.append("s",3,Double.POSITIVE_INFINITY,102L);
  assertNull(h.points("s").get(1).value);assertNull(h.points("s").get(2).value);
  h.append("s",4_000_000_004L,9.0,103L);assertTrue(h.points("s").get(3).breakBefore);
  assertNull(h.snapshot().get(0).unit);
 }
 @Test public void sourceClockResetBreaksLineButReceiveClockResetClearsHistory(){
  PlotHistory h=new PlotHistory(20,30_000_000_000L);h.select("s","data","m/s");
  h.append("s",100,1.0,1000L);h.append("s",200,2.0,50L);
  assertTrue(h.points("s").get(1).breakBefore);assertEquals(200,h.points("s").get(1).time);
  h.append("s",20,3.0,60L);assertEquals(1,h.points("s").size());
  h.append("s",20,4.0,70L);assertEquals(1,h.points("s").size());
  assertEquals("m/s",h.snapshot().get(0).unit);
 }
}
