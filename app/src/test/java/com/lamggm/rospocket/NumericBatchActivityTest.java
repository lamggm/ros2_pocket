package com.lamggm.rospocket;

import android.widget.CheckBox;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.*;

/** Real ordinary Activity worker/Views with an explicitly synthetic JNI transport. */
public class NumericBatchActivityTest extends ConnectionActivityTest {
 static final class BatchFake extends Fake {
  int selections;long epoch;int[] slots;String[] paths;Thread selectionThread;
  CountDownLatch selectionEntered,selectionRelease;final List<String> order=new ArrayList<>();
  public byte[] selectNumeric(long epoch,int[] slots,String[] paths){
   selections++;this.epoch=epoch;this.slots=slots.clone();this.paths=paths.clone();selectionThread=Thread.currentThread();order.add("select");
   if(selectionEntered!=null){selectionEntered.countDown();try{selectionRelease.await(5,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}}
   return bytes("{\"ok\":true}");
  }
  @Override public byte[] poll(){
   order.add("poll");try{snapshot=new JSONObject(snapshot).put("numericBatch",new JSONObject(snapshot).getJSONObject("numericBatch").put("epoch",epoch)).toString();}catch(JSONException error){throw new RuntimeException(error);}
   return super.poll();
  }
 }
 private JSONObject snapshot(long count,JSONArray samples)throws Exception{
  JSONObject root=NumericBatchControllerTest.snapshot(count,samples);JSONObject first=root.getJSONArray("watches").getJSONObject(0);first.put("subscription","/numeric");
  for(String key:new String[]{"subscription","type","readerActive","fresh","subscriptionState","error","count","receivedMonotonicNs","sourceTimestampNs","numericFields"})root.put(key,first.get(key));
  return root.put("readerGeneration",5).put("ddsPeers",1).put("rosNodes",0).put("connectionState","connected").put("topics",new JSONArray()).put("nodes",new JSONArray());
 }
 private BatchFake selected()throws Exception{
  BatchFake fake=new BatchFake();fake.snapshot=snapshot(1,new JSONArray()).toString();set("bridge",fake);bridge=fake;tick();paint();
  PlotView view=a.getWindow().getDecorView().findViewWithTag("live-plot");CheckBox choice=view.findViewWithTag("plot-field:Watch 1 · /numeric\ndata");assertNotNull(choice);choice.performClick();fake.order.clear();return fake;
 }
 private JSONArray points(int first,int last)throws Exception{JSONArray result=new JSONArray();for(int i=first;i<=last;i++)result.put(new JSONArray().put(i).put(i*1000).put(i*10).put((double)i));return result;}
 @Test public void ordinaryWorkerSendsBoundedSelectionBeforePollingAndIngestsWholeBatch()throws Exception{
  BatchFake fake=selected();fake.snapshot=snapshot(4,points(2,4)).toString();tick();paint();
  assertEquals("Activity must send the native numeric filter",1,fake.selections);assertEquals(1,fake.epoch);assertArrayEquals(new int[]{0},fake.slots);assertArrayEquals(new String[]{"data"},fake.paths);
  assertNotSame("JNI configuration cannot run on UI",Thread.currentThread(),fake.selectionThread);assertEquals(Arrays.asList("select","poll"),fake.order);
  PlotController plots=(PlotController)get("plots");assertEquals(3,plots.history.points("Watch 1 · /numeric\ndata").size());assertEquals(1,fake.starts);assertEquals("Selecting fields must not create extra DDS readers",1,fake.subscriptions);
 }
 @Test public void selectionChangedDuringBlockedNativeConfigurationCannotAdmitRetiredBatch()throws Exception{
  BatchFake fake=selected();fake.snapshot=snapshot(4,points(2,4)).toString();fake.selectionEntered=new CountDownLatch(1);fake.selectionRelease=new CountDownLatch(1);
  Future<?> task=((ScheduledExecutorService)get("worker")).submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception error){throw new RuntimeException(error);}});
  try{
   assertTrue("Native selection operation did not execute",fake.selectionEntered.await(3,TimeUnit.SECONDS));
   PlotController plots=(PlotController)get("plots");plots.removeSelection("Watch 1 · /numeric\ndata");assertTrue(plots.select("Watch 1 · /numeric","data"));
  }finally{fake.selectionRelease.countDown();}
  task.get(5,TimeUnit.SECONDS);paint();PlotController plots=(PlotController)get("plots");assertTrue(plots.history.points("Watch 1 · /numeric\ndata").isEmpty());
  fake.selectionEntered=null;fake.selectionRelease=null;fake.snapshot=snapshot(7,points(5,7)).toString();tick();paint();assertEquals(3,fake.epoch);assertEquals(3,plots.history.points("Watch 1 · /numeric\ndata").size());
 }
}
