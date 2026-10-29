package com.lamggm.rospocket;
import org.junit.Test;import static org.junit.Assert.*;import org.json.JSONObject;
import java.util.*;import java.util.concurrent.*;
public class SensorConcurrencyTest extends ConnectionActivityTest {
 static class VisualFake extends Fake {
  int visualStarts,visualStops,visualPolls;boolean throwStop,throwPoll,failVisualStart;
  CountDownLatch entered,release;byte[] packet;
  public byte[] startSensor(int d,String a,String p,String w,String t){visualStarts++;return bytes(failVisualStart?"{\"ok\":false,\"error\":\"temporary\"}":"{\"ok\":true}");}
  public byte[] pollSensor(){visualPolls++;if(throwPoll)throw new IllegalStateException("poll failed");if(entered!=null){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){throw new RuntimeException(e);}}return packet;}
  public void stopSensor(){visualStops++;if(throwStop)throw new IllegalStateException("sensor cleanup failed");}
 }
 VisualFake prepare(SensorPanel panel)throws Exception{
  VisualFake s=new VisualFake();s.packet=SensorPacketTest.packet(new JSONObject().put("running",true).put("count",0).put("ageMs",-1),new byte[0]);bridge=s;set("bridge",s);set("sensorPanel",panel);tick();paint();
  panel.available(true);panel.visible(true);panel.select("rt/camera","sensor_msgs::msg::dds_::Image_");return s;
 }
 void visualTick()throws Exception{work("tickSensor",new Class<?>[]{int.class},1);}
 @Test public void pollExceptionRetriesOnlyTheVisualReaderWithBackoff()throws Exception{
  SensorPanel panel=new SensorPanel(a,Runnable::run);VisualFake s=prepare(panel);s.throwPoll=true;visualTick();int starts=s.visualStarts,telemetry=s.starts;s.throwPoll=false;
  visualTick();assertEquals("No tight retry loop",starts,s.visualStarts);Thread.sleep(1100);visualTick();
  assertEquals("A stopped visual reader must start again",starts+1,s.visualStarts);assertEquals(telemetry,s.starts);panel.dispose();
 }
 @Test public void rejectedVisualStartCanRecoverWithoutRetargetingWatches()throws Exception{
  SensorPanel panel=new SensorPanel(a,Runnable::run);VisualFake s=prepare(panel);s.failVisualStart=true;visualTick();int starts=s.visualStarts;s.failVisualStart=false;
  visualTick();assertEquals(starts,s.visualStarts);Thread.sleep(1100);visualTick();assertEquals("Rejected start must not remain applied forever",starts+1,s.visualStarts);panel.dispose();
 }
 @Test public void sensorTeardownFailureStillAttemptsTelemetryStop()throws Exception{
  SensorPanel panel=new SensorPanel(a,Runnable::run);VisualFake s=prepare(panel);int before=s.stops;s.throwStop=true;
  try{work("stopSession",new Class<?>[]{});}catch(Exception expected){}
  assertTrue("Telemetry stop must run even if sensor stop throws",s.stops>before);s.throwStop=false;panel.dispose();
 }
 @Test public void visibilityABACannotAdmitAPacketFromBeforeTheSwitch()throws Exception{
  List<Runnable> decodes=Collections.synchronizedList(new ArrayList<>());SensorPanel panel=new SensorPanel(a,decodes::add);VisualFake s=prepare(panel);visualTick();
  s.packet=SensorPacketTest.packet(new JSONObject().put("running",true).put("count",1).put("ageMs",0).put("kind","image").put("width",1).put("height",1).put("step",3).put("encoding","rgb8"),new byte[]{1,2,3});
  s.entered=new CountDownLatch(1);s.release=new CountDownLatch(1);
  Future<?> f=((ScheduledExecutorService)get("worker")).submit(()->{try{call("tickSensor",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
  assertTrue(s.entered.await(5,TimeUnit.SECONDS));panel.visible(false);panel.visible(true);s.release.countDown();f.get(5,TimeUnit.SECONDS);
  assertEquals("Late packet may not enter decode after a visibility ABA",0,decodes.size());panel.dispose();
 }
}
