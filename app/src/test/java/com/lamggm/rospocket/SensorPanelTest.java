package com.lamggm.rospocket;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.json.JSONObject;
import java.util.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class SensorPanelTest {
 static class QueuedPanel extends SensorPanel {
  final List<Runnable> callbacks=new java.util.concurrent.CopyOnWriteArrayList<>();
  QueuedPanel(){super(RuntimeEnvironment.getApplication(),Runnable::run);}
  @Override public boolean post(Runnable r){callbacks.add(r);return true;}
 }
 static byte[] raw(int r)throws Exception{return SensorPacketTest.packet(new JSONObject().put("running",true).put("count",r+1).put("ageMs",0).put("kind","image").put("width",1).put("height",1).put("step",3).put("encoding","rgb8"),new byte[]{(byte)r,0,0});}
 @Test public void aBlockedUiRetainsOnlyOneLatestDelivery()throws Exception{
  QueuedPanel p=new QueuedPanel();p.available(true);p.visible(true);p.select("rt/camera","sensor_msgs::msg::dds_::Image_");
  for(int i=0;i<100;i++)p.accept(p.desired(),raw(i),()->true);
  assertEquals("Only one UI delivery may be queued, not 100 retained bitmaps",1,p.callbacks.size());
  p.callbacks.remove(0).run();
  SensorPacket.Decoded shown=org.robolectric.util.ReflectionHelpers.getField(p,"shown");assertNotNull(shown);assertEquals(0xff630000,shown.image.getPixel(0,0));p.dispose();
 }
 @Test public void anInvalidatedDecodeCannotOverwriteQueuedCleanup()throws Exception{
  class BlockingPanel extends QueuedPanel {
   volatile java.util.concurrent.CountDownLatch entered,release;
   @Override SensorPacket.Decoded decode(SensorPacket packet)throws Exception{if(entered!=null){entered.countDown();assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS));}return super.decode(packet);}
  }
  BlockingPanel p=new BlockingPanel();p.available(true);p.visible(true);p.select("rt/camera","sensor_msgs::msg::dds_::Image_");
  p.accept(p.desired(),raw(10),()->true);p.callbacks.remove(0).run();assertNotNull(org.robolectric.util.ReflectionHelpers.getField(p,"shown"));
  p.entered=new java.util.concurrent.CountDownLatch(1);p.release=new java.util.concurrent.CountDownLatch(1);
  java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
  try{
   java.util.concurrent.Future<?> decoded=worker.submit(()->{try{p.accept(p.desired(),raw(20),()->true);}catch(Exception e){throw new RuntimeException(e);}});
   assertTrue(p.entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
   Thread cleanup=new Thread(p::invalidateStream);cleanup.start();cleanup.join(2000);assertFalse(cleanup.isAlive());
   p.release.countDown();decoded.get(5,java.util.concurrent.TimeUnit.SECONDS);
   while(!p.callbacks.isEmpty())p.callbacks.remove(0).run();
   assertNull("Invalidated old decode may not erase cleanup and retain the old image",org.robolectric.util.ReflectionHelpers.getField(p,"shown"));
  }finally{p.release.countDown();worker.shutdownNow();p.dispose();}
 }
}
