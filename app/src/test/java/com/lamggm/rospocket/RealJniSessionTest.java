package com.lamggm.rospocket;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;
import org.json.*;import java.util.concurrent.*;import static org.junit.Assert.*;
/** Real Linux JNI/Session/ddsc and separate loopback producer. NOT Android native execution. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class RealJniSessionTest {
 private final ScheduledExecutorService worker=MainActivity.diagnosticWorker();
 private JSONObject poll() throws Exception{return worker.submit(()->NativeBridge.json(NativeBridge.poll())).get(3,TimeUnit.SECONDS);}
 private interface Condition{boolean get(JSONObject p)throws Exception;}
 private JSONObject await(Condition c)throws Exception{long end=System.nanoTime()+5_000_000_000L;while(System.nanoTime()<end){JSONObject p=poll();if(c.get(p))return p;Thread.sleep(30);}throw new AssertionError("genuine DDS intake timeout");}
 private static long logs(JSONObject p)throws Exception{JSONArray a=p.getJSONObject("rosout").getJSONArray("entries");return a.length()==0?0:a.getJSONObject(a.length()-1).getLong("line");}
 @Test public void realJniLeaseFailureReceiptPollRecoveryAndSurvivors() throws Exception {
  Assume.assumeTrue("Explicit isolated host JNI gate only",System.getenv("POCKET_REAL_JNI_LIBDIR")!=null);
  Process producer=new ProcessBuilder(System.getenv("POCKET_REAL_JNI_WRITER")).inheritIO().start();DeviceTestHooks.Controller c=DeviceTestHooks.CONTROLLER;DeviceTestHooks.Lease lease=null;
  try{
   lease=c.begin(worker);final DeviceTestHooks.Lease l=lease;
   worker.submit(()->{try{assertTrue(NativeBridge.json(NativeBridge.start(67,"127.0.0.1","")).getBoolean("ok"));for(int i=0;i<4;i++)assertTrue(NativeBridge.json(NativeBridge.watch(i,"rt/pocket_diagnostic/watch"+i,"std_msgs::msg::dds_::String_",false,false)).getBoolean("ok"));}catch(Exception e){throw new RuntimeException(e);}}).get(3,TimeUnit.SECONDS);
   await(p->{for(int i=0;i<4;i++)if(p.getJSONArray("watches").getJSONObject(i).getLong("count")==0)return false;return logs(p)>0;});
   for(int slot:new int[]{0,2})for(String stage:new String[]{"topic","reader"}){
    JSONObject baseline=poll();Object owner=new Object();WatchSlots.Request request=new WatchSlots.Request(slot,1,"rt/pocket_diagnostic/watch"+slot,"std_msgs::msg::dds_::String_",false,false);
    worker.submit(()->{try{c.capture(owner,owner,1,System.nanoTime(),baseline.getLong("sessionGeneration"),new WatchSlots().frames());}catch(Exception e){throw new RuntimeException(e);}}).get(3,TimeUnit.SECONDS);
    DeviceTestHooks.Ticket ticket=c.ticket(l,owner,request,1,owner,1,stage);
    worker.submit(()->{DeviceTestHooks.Acquisition a=c.before(ticket,owner,request,1,owner,1);try{JSONObject result=NativeBridge.json(NativeBridge.watch(request.slot,request.wire,request.type,false,false));assertFalse(result.getBoolean("ok"));}catch(Exception e){throw new RuntimeException(e);}finally{c.after(a);}}).get(3,TimeUnit.SECONDS);
    JSONObject receipt=new JSONObject(ticket.receipt);assertTrue(ticket.matchesReceipt(receipt));assertEquals(stage,receipt.getString("stage"));
    if(stage.equals("topic")){assertEquals(-3,receipt.getLong("topicReturn"));assertTrue(receipt.isNull("readerReturn"));}else{assertTrue(receipt.getLong("topicReturn")>0);assertEquals(-3,receipt.getLong("readerReturn"));assertEquals(0,receipt.getLong("readerParent"));}
    for(int i=0;i<3;i++){JSONObject p=poll(),w=p.getJSONArray("watches").getJSONObject(slot);assertFalse(w.getBoolean("readerActive"));assertFalse(w.getBoolean("fresh"));assertEquals(0,w.getLong("count"));assertEquals(0,w.getLong("matched"));assertEquals(-1,w.getDouble("age"),0);assertEquals("",w.getString("last"));assertEquals("",w.getString("subscription"));assertTrue(w.isNull("message"));assertFalse(w.getString("error").isEmpty());assertEquals(receipt.getLong("attemptedGeneration"),w.getLong("generation"));Thread.sleep(40);}
    final int failed=slot;await(p->{for(int i=0;i<4;i++)if(i!=failed){JSONObject before=baseline.getJSONArray("watches").getJSONObject(i),now=p.getJSONArray("watches").getJSONObject(i);if(now.getLong("generation")!=before.getLong("generation")||now.getLong("count")<=before.getLong("count"))return false;}return logs(p)>logs(baseline);});
    worker.submit(()->NativeBridge.watch(request.slot,request.wire,request.type,false,false)).get();
    JSONObject first=await(p->p.getJSONArray("watches").getJSONObject(failed).getLong("count")>0).getJSONArray("watches").getJSONObject(slot);
    await(p->{JSONObject now=p.getJSONArray("watches").getJSONObject(failed);return now.getLong("count")>first.getLong("count")&&now.getLong("receivedMonotonicNs")>first.getLong("receivedMonotonicNs")&&!now.getString("last").equals(first.getString("last"));});
   }
   worker.submit(()->{NativeBridge.stop();assertTrue(NativeBridge.json(NativeBridge.diagnosticState()).optBoolean("runnerActive"));return null;}).get();
   assertTrue(c.end(l,worker).get(3,TimeUnit.SECONDS));lease=null;assertFalse(NativeBridge.json(NativeBridge.diagnosticState()).getBoolean("runnerActive"));
  }finally{if(lease!=null)c.end(lease,worker).get(3,TimeUnit.SECONDS);worker.submit(()->NativeBridge.stop()).get();producer.destroy();if(!producer.waitFor(3,TimeUnit.SECONDS))producer.destroyForcibly();}
 }
}
