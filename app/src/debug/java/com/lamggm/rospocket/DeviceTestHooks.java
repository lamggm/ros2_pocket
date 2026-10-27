package com.lamggm.rospocket;

import java.util.concurrent.*;
import org.json.JSONObject;

/** In-process debug instrumentation only. Never registered as an Android component. */
final class DeviceTestHooks {
 static final Controller CONTROLLER=new Controller(new JniControl());
 static long revision(){return CONTROLLER.revision();}
 static Ticket ticket(Object lease,Object owner,WatchSlots.Request r,int life,Object config,long policy,String stage){if(stage==null&&lease==null)return null;if(!(lease instanceof Lease))throw new IllegalStateException("no lease");return CONTROLLER.ticket((Lease)lease,owner,r,life,config,policy,stage);}
 static Acquisition before(Ticket t,Object owner,WatchSlots.Request r,int life,Object config,long policy){return CONTROLLER.before(t,owner,r,life,config,policy);}
 static void after(Acquisition a){CONTROLLER.after(a);}
 static void invalidate(){CONTROLLER.invalidate();}
 static void cancelOwner(Object owner){CONTROLLER.cancelOwner(owner);}
 static void reject(Ticket t){CONTROLLER.reject(t);}
 static JSONObject export(long now) throws org.json.JSONException{return CONTROLLER.export(now);}
 static void publish(long rev,JSONObject state,JSONObject poll,long time,int life,long policy,WatchSlots.Frame[] frames,Object owner,Object config){synchronized(CONTROLLER){if(CONTROLLER.publish(rev,state,poll,time,life,policy,frames))CONTROLLER.capture(owner,config,life,time,state.optLong("session",-1),frames);}}
 private static final class JniControl implements Control {
  public boolean begin(long e){NativeBridge.diagnosticState();return beginLease(e);}
  public boolean end(long e){return endLease(e);}
  public long[] identity(int slot){return readerIdentity(slot);}
  public long[] arm(int slot,String stage,long[] id,long epoch,long nonce){return armFailure(slot,stage,id,epoch,nonce);}
  public boolean cancel(long[] h){return cancelFailure(h);}
  public String receipt(){return new String(failureReceipt(),java.nio.charset.StandardCharsets.UTF_8);}
  public String state(){return new String(NativeBridge.diagnosticState(),java.nio.charset.StandardCharsets.UTF_8);}
 }
 private static native boolean beginLease(long epoch);
 private static native boolean endLease(long epoch);
 private static native long[] readerIdentity(int slot);
 private static native long[] armFailure(int slot,String stage,long[] identity,long epoch,long nonce);
 private static native boolean cancelFailure(long[] handle);
 private static native byte[] failureReceipt();
 interface Control {
  boolean begin(long epoch); boolean end(long epoch);
  long[] identity(int slot);
  long[] arm(int slot,String stage,long[] id,long epoch,long nonce);
  boolean cancel(long[] handle); String receipt(); String state();
 }
 static final class NotCaptured extends IllegalStateException {NotCaptured(String message){super(message);}}
 static final class Lease { final long epoch; Lease(long e){epoch=e;} }
 static final class Ticket {
  final Lease lease;final Object owner,config;final WatchSlots.Request request;
  final int lifecycle;final long policy,nonce,deadline;final String stage;private final long[] expectedNative;
  volatile String receipt="";private volatile Acquisition binding;
  boolean matchesReceipt(JSONObject r){
   Acquisition a=binding;if(a==null)return false;
   try{return exact(r,"slot")==a.handle[0]&&exact(r,"targetSlot")==a.handle[0]&&exact(r,"session")==a.handle[1]
    &&exact(r,"attemptedGeneration")==a.handle[2]&&exact(r,"request")==a.handle[3]&&exact(r,"lease")==a.handle[4]&&exact(r,"nonce")==a.handle[5]&&stage.equals(r.get("stage"));
   }catch(Exception ex){return false;}
  }
  private static long exact(JSONObject r,String key) throws org.json.JSONException{Object value=r.get(key);if(!(value instanceof Integer||value instanceof Long))throw new org.json.JSONException("integer required");return ((Number)value).longValue();}
  Ticket(Lease l,Object o,WatchSlots.Request r,int life,Object c,long p,long n,String s,long[] id){expectedNative=id.clone();lease=l;owner=o;request=r;lifecycle=life;config=c;policy=p;nonce=n;stage=s;deadline=System.nanoTime()+5_000_000_000L;}
 }
 static final class Acquisition {final Ticket ticket;private final long[] handle;Acquisition(Ticket t,long[] h){ticket=t;handle=h.clone();}}
 static final class Controller {
  final Control control;
  private String phase="INACTIVE";private Lease lease;private Ticket pending;private Acquisition inFlight;
  private boolean operation=false;private long epoch=0,nonce=0,revision=0,sequence=0,cacheRevision=-1,captured=0;
  private String cache="",projection="";
  private Object captureOwner,captureConfig;private int captureLife;private long identityTime;
  private long[][] identities;
  // Called only by the ordinary DDS worker after its coherent poll publication.
  synchronized void capture(Object owner,Object config,int life,long time,long session,WatchSlots.Frame[] frames){
   identities=null;
   if(!phase.equals("ACTIVE"))return;
   long[][] ids=new long[4][];
   for(WatchSlots.Frame f:frames){
    if(f.pending)continue;
    long[] id=control.identity(f.request.slot);
    if(id==null||id.length!=3||id[0]!=session||id[0]<=0||id[1]<0||id[2]<=0)continue;
    ids[f.request.slot]=id.clone();
   }
   captureOwner=owner;captureConfig=config;captureLife=life;identityTime=time;identities=ids;
  }
  private static final String INSTANCE=java.util.UUID.randomUUID().toString();
  private static final long START=startTicks();
  private static long startTicks(){try{String s=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("/proc/self/stat")),java.nio.charset.StandardCharsets.US_ASCII);return Long.parseLong(s.substring(s.lastIndexOf(')')+2).split(" ")[19]);}catch(Exception e){return -1;}}
  synchronized boolean publish(long expected,JSONObject nativeState,JSONObject poll,long time,int lifecycle,long policy,WatchSlots.Frame[] frames){
   cache="";projection="";
   if(expected!=revision)return false;
   try{
    Object capability=nativeState.get("capability"),armed=nativeState.get("armed"),runner=nativeState.get("runnerActive");
    if(!(capability instanceof Boolean)||!(armed instanceof Boolean)||!(runner instanceof Boolean))return false;
    for(String key:new String[]{"revision","session","nativeLeaseEpoch"}){Object value=nativeState.get(key);if(!(value instanceof Integer||value instanceof Long)||((Number)value).longValue()<0)return false;}
    Object session=poll.get("sessionGeneration");if(!(session instanceof Integer||session instanceof Long)||((Number)session).longValue()!=nativeState.getLong("session"))return false;
    long expectedLease=phase.equals("INACTIVE")?0:lease==null?-1:lease.epoch;
    if(nativeState.getLong("nativeLeaseEpoch")!=expectedLease)return false;
    if(capability==Boolean.FALSE&&(armed!=Boolean.FALSE||runner!=Boolean.FALSE||nativeState.get("contract")!=JSONObject.NULL))return false;
    if(capability==Boolean.TRUE&&!"ros-pocket-dds-fault-v1".equals(nativeState.get("contract")))return false;
    boolean active=!phase.equals("INACTIVE");
    if((Boolean)runner!=active)return false;
    JSONObject out=new JSONObject().put("schemaVersion",2).put("valid",true).put("revision",revision)
     .put("captureSequence",++sequence).put("capturedMonotonicNs",time).put("processInstance",INSTANCE)
     .put("pid",android.os.Process.myPid()).put("startTimeTicks",START).put("capability",capability)
     .put("contract",nativeState.get("contract")).put("armed",armed).put("runnerActive",runner)
     .put("leasePhase",phase).put("leaseEpoch",lease==null?0:lease.epoch).put("ticketPending",pending!=null)
     .put("operationInFlight",operation).put("nativeRevision",nativeState.get("revision"))
     .put("sessionGeneration",nativeState.get("session")).put("lifecycle",lifecycle).put("policy",policy);
    org.json.JSONArray requests=new org.json.JSONArray();
    for(WatchSlots.Frame f:frames)requests.put(new JSONObject().put("slot",f.request.slot).put("version",f.request.version).put("appliedVersion",f.appliedVersion).put("pending",f.pending));
    out.put("requests",requests);cache=out.toString();cacheRevision=revision;captured=time;
    if(active){String raw=poll.toString();if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=524288)projection=raw;}
    return true;
   }catch(Exception ex){return false;}
  }
  synchronized JSONObject export(long now) throws org.json.JSONException {
   if(cache.isEmpty()||cacheRevision!=revision||now<captured||now-captured>2_000_000_000L)return new JSONObject().put("schemaVersion",2).put("valid",false).put("capability","unknown").put("armed",JSONObject.NULL).put("runnerActive",JSONObject.NULL).put("exportedMonotonicNs",now);
   return new JSONObject(cache).put("exportedMonotonicNs",now);
  }
  synchronized JSONObject projection(Lease l) throws org.json.JSONException {
   if(l==null||l!=lease||!phase.equals("ACTIVE"))throw new IllegalStateException("foreign or inactive runner lease");
   if(cacheRevision!=revision||projection.isEmpty())throw new NotCaptured("no coherent ordinary poll");
   JSONObject d=export(System.nanoTime());if(d.opt("valid")!=Boolean.TRUE)throw new NotCaptured("ordinary poll expired");
   return new JSONObject().put("diagnostic",d).put("poll",new JSONObject(projection));
  }
  Controller(Control c){control=c;}
  synchronized String phase(){return phase;}
  synchronized long revision(){return revision;}
  synchronized void invalidate(){revision++;}
  synchronized void cancelOwner(Object owner){if(captureOwner==owner)identities=null;if(pending!=null&&pending.owner==owner){pending=null;revision++;}}
  synchronized void reject(Ticket t){if(t!=null&&pending==t){pending=null;revision++;}}
  Lease begin(Executor worker) throws Exception {
   final Lease next;
   synchronized(this){if(!phase.equals("INACTIVE"))throw new IllegalStateException("lease not inactive");next=new Lease(++epoch);lease=next;phase="ACTIVE";identities=null;revision++;}
   CompletableFuture<Boolean> result=new CompletableFuture<>();
   try{worker.execute(()->{try{result.complete(control.begin(next.epoch));}catch(Throwable ex){result.completeExceptionally(ex);}});
    if(!result.get(3,TimeUnit.SECONDS))throw new IllegalStateException("native lease rejected");return next;
   }catch(Exception ex){synchronized(this){phase="UNKNOWN";revision++;}throw ex;}
  }
  synchronized Ticket ticket(Lease l,Object o,WatchSlots.Request r,int life,Object config,long policy,String stage){
   if(l==null||l!=lease||!phase.equals("ACTIVE")||pending!=null||operation||!(stage.equals("topic")||stage.equals("reader")))throw new IllegalStateException("ticket rejected");
   long now=System.nanoTime();
   if(identities==null||captureOwner!=o||captureConfig!=config||captureLife!=life||now<identityTime||now-identityTime>2_000_000_000L||identities[r.slot]==null)throw new IllegalStateException("no authoritative prior reader capture");
   revision++;return pending=new Ticket(l,o,r,life,config,policy,++nonce,stage,identities[r.slot]);
  }
  synchronized Acquisition before(Ticket t,Object owner,WatchSlots.Request r,int life,Object config,long policy){
   if(t==null)return null;
   if(t!=pending||t.lease!=lease||!phase.equals("ACTIVE")||t.owner!=owner||t.request!=r||t.lifecycle!=life||t.config!=config||t.policy!=policy||System.nanoTime()>t.deadline){
    if(pending==t){pending=null;revision++;}throw new IllegalStateException("stale diagnostic ticket");
   }
   pending=null;revision++;operation=true;
   try{long[] id=control.identity(r.slot);
    if(!java.util.Arrays.equals(id,t.expectedNative))throw new IllegalStateException("native reader identity changed");
    long[] handle=control.arm(r.slot,t.stage,t.expectedNative.clone(),t.lease.epoch,t.nonce);
    if(handle==null||handle.length!=6)throw new IllegalStateException("arm not acquired");
    return t.binding=inFlight=new Acquisition(t,handle);
   }catch(Throwable ex){operation=false;revision++;throw ex;}
  }
  synchronized void after(Acquisition acquired){
   if(acquired==null||acquired!=inFlight)return;
   try{
    acquired.ticket.receipt=control.receipt(); // Preserve owned receipt before cancel (consumption may return false).
    control.cancel(acquired.handle);
    JSONObject state=new JSONObject(control.state());
    if(state.opt("armed")!=Boolean.FALSE)throw new IllegalStateException("native arm not empty");
   }catch(Throwable ex){phase="UNKNOWN";throw new IllegalStateException("unverified diagnostic cleanup",ex);}
   finally{inFlight=null;operation=false;revision++;}
  }
  CompletableFuture<Boolean> end(Lease l,Executor worker){
   CompletableFuture<Boolean> result=new CompletableFuture<>();
   synchronized(this){if(l==null||l!=lease||!phase.equals("ACTIVE")){result.complete(false);return result;}phase="CLOSING";pending=null;revision++;}
   try{worker.execute(()->{
    synchronized(this){
     try{JSONObject state=new JSONObject(control.state());
      if(l!=lease||!phase.equals("CLOSING")||pending!=null||operation||state.opt("armed")!=Boolean.FALSE||!control.end(l.epoch))throw new IllegalStateException("shutdown not drained");
      JSONObject ended=new JSONObject(control.state());
      if(ended.opt("armed")!=Boolean.FALSE||ended.opt("runnerActive")!=Boolean.FALSE)throw new IllegalStateException("native lease end not authoritative");
      phase="INACTIVE";revision++;result.complete(true);
     }catch(Throwable ex){phase="UNKNOWN";revision++;result.complete(false);}
    }
   });}catch(Throwable ex){synchronized(this){phase="UNKNOWN";revision++;}result.complete(false);}
   return result;
  }
 }
}
