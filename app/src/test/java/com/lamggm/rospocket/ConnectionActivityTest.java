package com.lamggm.rospocket;

import android.content.Context;
import android.graphics.Color;
import android.net.*;
import android.os.Looper;
import android.widget.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Real Robolectric Android runtime; JNI is an explicit test double (DDS tested in CTest). */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class ConnectionActivityTest {
 MainActivity a; Fake bridge; ShadowConnectivityManager cm; Network wifi;
 static class Fake implements NativeBridge.Api {
  int starts,stops,subscriptions,polls;boolean failStart,failSubscribe,throwSubscribe;
  String snapshot="{\"running\":true,\"ddsPeers\":1,\"rosNodes\":0,\"connectionState\":\"connected\",\"subscriptionState\":\"receiving\",\"readerActive\":true,\"fresh\":true,\"subscription\":\"/numeric\",\"count\":12,\"age\":0.1,\"matched\":1,\"last\":\"42.5\",\"topics\":[],\"nodes\":[],\"error\":\"\"}";
  public byte[] start(int d,String address,String peer){starts++;return bytes(failStart?"{\"ok\":false,\"error\":\"start failed\"}":"{\"ok\":true}");}
  public byte[] poll(){polls++;return bytes(snapshot);}
  public byte[] subscribe(String w,String t,boolean r,boolean l){subscriptions++;if(throwSubscribe)throw new IllegalStateException("reader exception");return bytes(failSubscribe?"{\"ok\":false,\"error\":\"subscribe failed\"}":"{\"ok\":true}");}
  public void stop(){stops++;}
  static byte[] bytes(String s){return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
 }
 static Field field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
 void set(String name,Object value)throws Exception{field(name).set(a,value);}
 Object get(String name)throws Exception{return field(name).get(a);}
 void call(String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(a,args);}
 void work(String name,Class<?>[] types,Object...args)throws Exception{
  ((ScheduledExecutorService)get("worker")).submit(()->{try{call(name,types,args);}catch(Exception e){throw new RuntimeException(e);}}).get(5,TimeUnit.SECONDS);
 }
 void tick()throws Exception{work("tick",new Class<?>[]{int.class},1);}
 void paint(){shadowOf(Looper.getMainLooper()).idle();}
 String text(int id){return ((TextView)a.findViewById(id)).getText().toString();}
 void address(String value)throws Exception{LinkProperties lp=new LinkProperties();lp.setInterfaceName("wlan-test");lp.setLinkAddresses(java.util.Collections.singletonList(org.robolectric.util.ReflectionHelpers.callConstructor(LinkAddress.class,org.robolectric.util.ReflectionHelpers.ClassParameter.from(String.class,value+"/24"))));cm.setLinkProperties(wifi,lp);}
 @Before public void setup()throws Exception{
  a=Robolectric.buildActivity(MainActivity.class).create().get();bridge=new Fake();set("bridge",bridge);
  a.getSharedPreferences("connection",Context.MODE_PRIVATE).edit().clear().commit();
  cm=shadowOf((ConnectivityManager)a.getSystemService(Context.CONNECTIVITY_SERVICE));cm.clearAllNetworks();
  wifi=ShadowNetwork.newInstance(42);cm.addNetwork(wifi,null);
  NetworkCapabilities caps=new NetworkCapabilities();shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);cm.setNetworkCapabilities(wifi,caps);address("192.0.2.2");
  set("resumed",true);set("generation",1);set("worker",field("DDS_WORKER").get(null));
  call("requestConnection",new Class<?>[]{boolean.class},false);
  set("selectedWire","rt/numeric");set("selectedType","std_msgs::msg::dds_::Float64_");
 }
 @After public void cleanup()throws Exception{call("onStop",new Class<?>[]{});((ScheduledExecutorService)field("DDS_WORKER").get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);paint();}
 @Test public void diagnosticsSeparatePeersAndNodesAndKeepNumericSelection()throws Exception{
  tick();paint();assertTrue(text(R.id.diagnostics).contains("wlan-test"));assertTrue(text(R.id.diagnostics).contains("DDS peers: 1"));assertTrue(text(R.id.diagnostics).contains("Nós ROS: 0"));assertTrue(text(R.id.counts).contains("RECEBENDO"));
  assertEquals("rt/numeric",get("selectedWire"));assertEquals(1,bridge.subscriptions);
  bridge.snapshot=bridge.snapshot.replace("\"ddsPeers\":1","\"ddsPeers\":0").replace("\"fresh\":true","\"fresh\":false").replace("receiving","stale").replace("connected","reconnecting");
  set("lastUi",0L);tick();paint();assertEquals(1,bridge.starts);assertEquals(1,bridge.subscriptions);assertTrue(text(R.id.counts).contains("SEM DADOS RECENTES"));
 }
 @Test public void queuedLiveSnapshotCannotPaintOverNetworkLoss()throws Exception{
  tick();cm.clearAllNetworks();tick();paint();
  assertTrue(text(R.id.counts).contains("OFFLINE"));assertFalse(text(R.id.message).equals("42.5"));assertNotEquals(0xff48d7bb,((TextView)a.findViewById(R.id.counts)).getCurrentTextColor());
  assertNull(((ConnectivityManager)a.getSystemService(Context.CONNECTIVITY_SERVICE)).getBoundNetworkForProcess());
 }
 @Test public void networkLossClearsExplorerSnapshotButKeepsLocalControlsAndExplicitReader()throws Exception{
  org.json.JSONObject topic=new org.json.JSONObject().put("name","/numeric").put("type","std_msgs/msg/Float64").put("wireName","rt/numeric").put("wireType","std_msgs::msg::dds_::Float64_").put("supported",true);
  bridge.snapshot=new org.json.JSONObject(bridge.snapshot).put("topics",new org.json.JSONArray().put(topic)).toString();
  ExplorerView explorer=a.findViewById(R.id.topics);EditText search=explorer.findViewById(R.id.explorer_search);
  tick();paint();assertSame("Session startup must not remove Explorer controls",search,explorer.findViewById(R.id.explorer_search));search.setText("numeric");
  ((CheckBox)explorer.findViewWithTag("favorite:rt/numeric\nstd_msgs::msg::dds_::Float64_")).performClick();
  ((Button)explorer.findViewWithTag("details:rt/numeric\nstd_msgs::msg::dds_::Float64_")).performClick();
  assertEquals(1,explorer.visibleCount());assertEquals(1,bridge.subscriptions);
  cm.clearAllNetworks();tick();paint();
  assertSame("Network loss must not remove Explorer controls",search,explorer.findViewById(R.id.explorer_search));
  assertEquals("numeric",search.getText().toString());assertEquals(0,explorer.visibleCount());
  assertTrue(((TextView)explorer.findViewById(R.id.explorer_details)).getText().toString().contains("não está anunciado"));
  assertFalse(text(R.id.counts).contains("RECEBENDO"));assertEquals(1,bridge.subscriptions);
  cm.addNetwork(wifi,null);NetworkCapabilities caps=new NetworkCapabilities();shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);cm.setNetworkCapabilities(wifi,caps);address("192.0.2.2");
  tick();paint();assertEquals(1,explorer.visibleCount());assertEquals(2,bridge.starts);assertEquals(2,bridge.subscriptions);
  assertTrue(((CheckBox)explorer.findViewWithTag("favorite:rt/numeric\nstd_msgs::msg::dds_::Float64_")).isChecked());
  ((Button)explorer.findViewWithTag("read:rt/numeric\nstd_msgs::msg::dds_::Float64_")).performClick();
  ((ScheduledExecutorService)get("worker")).submit(()->{}).get(5,TimeUnit.SECONDS);assertEquals(3,bridge.subscriptions);
 }
 @Test public void addressChangeFencesOldCallbackAndRecreatesOnlyOnce()throws Exception{
  tick();address("192.0.2.3");tick();paint();assertEquals(2,bridge.starts);assertTrue(text(R.id.diagnostics).contains("192.0.2.3"));assertEquals("rt/numeric",get("selectedWire"));
 }
 @Test public void subscriptionFailureStaysVisibleAcrossPollsWithoutRetryLoop()throws Exception{
  bridge.failSubscribe=true;tick();paint();assertTrue(text(R.id.status).contains("subscribe failed"));assertFalse(text(R.id.counts).contains("RECEBENDO"));
  set("lastUi",0L);tick();paint();assertTrue(text(R.id.status).contains("subscribe failed"));assertEquals(1,bridge.starts);assertEquals(1,bridge.subscriptions);
  bridge.failSubscribe=false;call("resubscribe",new Class<?>[]{});((ScheduledExecutorService)get("worker")).submit(()->{}).get();tick();paint();
  assertTrue(text(R.id.counts).contains("RECEBENDO"));assertEquals(1,bridge.starts);assertEquals(2,bridge.subscriptions);
 }
 @Test public void thrownSubscriptionErrorDoesNotRebuildHealthySession()throws Exception{
  bridge.throwSubscribe=true;tick();paint();tick();paint();
  assertEquals(1,bridge.starts);assertEquals(1,bridge.subscriptions);assertTrue(bridge.polls>=2);assertTrue(text(R.id.status).contains("reader exception"));
 }
 @Test public void failedStartKeepsPollingSchedulerAndExplicitResetCanRetry()throws Exception{
  bridge.failStart=true;tick();paint();tick();assertEquals(1,bridge.starts);assertTrue(text(R.id.status).contains("start failed"));
  call("disconnect",new Class<?>[]{});((ScheduledExecutorService)get("worker")).submit(()->{}).get();
  bridge.failStart=false;call("requestConnection",new Class<?>[]{boolean.class},false);tick();paint();assertEquals(2,bridge.starts);
 }
 @Test public void queuedSubscriptionDoesNotLetOldReaderSnapshotLookActive()throws Exception{
  tick();paint();
  set("lastUi",0L);
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  Future<?> queued=((ScheduledExecutorService)get("worker")).submit(()->{entered.countDown();try{release.await();call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
  assertTrue(entered.await(5,TimeUnit.SECONDS));
  try{set("selectedWire","rt/replacement");call("resubscribe",new Class<?>[]{});}finally{release.countDown();}
  queued.get(5,TimeUnit.SECONDS);((ScheduledExecutorService)get("worker")).submit(()->{}).get();paint();
  assertFalse(text(R.id.counts).contains("RECEBENDO"));assertFalse(text(R.id.message).equals("42.5"));
 }
 @Test public void delayedSnapshotIsStaleAtPaintTime()throws Exception{
  org.json.JSONObject snapshot=new org.json.JSONObject(bridge.snapshot);
  call("render",new Class<?>[]{org.json.JSONObject.class,get("requested").getClass(),String.class,String.class,long.class,boolean.class},snapshot,get("requested"),"test network","",System.nanoTime()-4_000_000_000L,false);
  assertTrue(text(R.id.counts).contains("SEM DADOS RECENTES"));assertNotEquals(0xff48d7bb,((TextView)a.findViewById(R.id.counts)).getCurrentTextColor());
 }
 @Test public void failedReplacementStartClearsQueuedLiveDataAndResources()throws Exception{
  tick();bridge.failStart=true;address("192.0.2.3");tick();paint();
  assertTrue(text(R.id.status).contains("start failed"));assertFalse(text(R.id.counts).contains("RECEBENDO"));assertFalse(text(R.id.message).equals("42.5"));
  assertNull(get("multicast"));assertNull(((ConnectivityManager)a.getSystemService(Context.CONNECTIVITY_SERVICE)).getBoundNetworkForProcess());
 }
 @Test public void explicitDisconnectAndNewPendingConnectionFenceOldPaint()throws Exception{
  tick();call("disconnect",new Class<?>[]{});call("requestConnection",new Class<?>[]{boolean.class},false);paint();
  assertFalse(text(R.id.counts).contains("RECEBENDO"));assertFalse(text(R.id.message).equals("42.5"));
 }
 @Test public void persistsConfigurationButNotOldNumericTelemetryAcrossProcessModel()throws Exception{
  ((EditText)a.findViewById(R.id.domain)).setText("69");((EditText)a.findViewById(R.id.peer)).setText("192.0.2.5");
  call("requestConnection",new Class<?>[]{boolean.class},false);
  ((CheckBox)a.findViewById(R.id.qos_reliable)).setChecked(true);((CheckBox)a.findViewById(R.id.qos_transient)).setChecked(true);
  MainActivity next=Robolectric.buildActivity(MainActivity.class).create().get();
  assertEquals("69",((EditText)next.findViewById(R.id.domain)).getText().toString());
  assertEquals("192.0.2.5",((EditText)next.findViewById(R.id.peer)).getText().toString());
  assertTrue(((CheckBox)next.findViewById(R.id.qos_reliable)).isChecked());assertTrue(((CheckBox)next.findViewById(R.id.qos_transient)).isChecked());
  assertEquals("",field("selectedWire").get(next));assertFalse(((TextView)next.findViewById(R.id.counts)).getText().toString().contains("RECEBENDO"));
 }
 @Test public void lateStopFromOldActivityMustNotTearDownNewSession()throws Exception{
  tick();paint();
  MainActivity previous=a;Fake previousBridge=bridge;
  MainActivity next=Robolectric.buildActivity(MainActivity.class).create().get();Fake nextBridge=new Fake();
  a=next;bridge=nextBridge;set("bridge",nextBridge);set("resumed",true);set("generation",1);set("worker",field("DDS_WORKER").get(null));
  call("requestConnection",new Class<?>[]{boolean.class},false);tick();paint();
  int stopsBefore=nextBridge.stops;int oldStops=previousBridge.stops;
  a=previous;call("onStop",new Class<?>[]{});
  ((ScheduledExecutorService)field("DDS_WORKER").get(null)).submit(()->{}).get();
  a=next;assertEquals(stopsBefore,nextBridge.stops);assertEquals(oldStops,previousBridge.stops);
  assertTrue((Boolean)get("nativeRunning"));assertEquals(wifi,((ConnectivityManager)a.getSystemService(Context.CONNECTIVITY_SERVICE)).getBoundNetworkForProcess());
 }
 @Test public void resumeKeepsNumericReaderAndGetsNewSession()throws Exception{
  tick();paint();call("onStop",new Class<?>[]{});
  ((ScheduledExecutorService)field("DDS_WORKER").get(null)).submit(()->{}).get();
  call("onStart",new Class<?>[]{});
  ((ScheduledExecutorService)field("DDS_WORKER").get(null)).submit(()->{}).get();paint();
  assertEquals("rt/numeric",get("selectedWire"));assertEquals(2,bridge.starts);assertEquals(2,bridge.subscriptions);
 }
 @Test public void readerChangeWhileTickWaitsForPolicyCannotRepaintOldSample()throws Exception{
  tick();paint();assertEquals("42.5",text(R.id.message));set("lastUi",0L);
  ScheduledExecutorService executor=(ScheduledExecutorService)get("worker");
  Thread thread=executor.submit(()->Thread.currentThread()).get(5,TimeUnit.SECONDS);
  Object policy=get("policy");Future<?> pending;
  synchronized(policy){
   pending=executor.submit(()->{try{call("tick",new Class<?>[]{int.class},1);}catch(Exception e){throw new RuntimeException(e);}});
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean blocked=false;
   while(System.nanoTime()<deadline){
    for(StackTraceElement f:thread.getStackTrace())if(f.getClassName().endsWith("ConnectionPolicy")&&f.getMethodName().equals("identity")&&thread.getState()==Thread.State.BLOCKED)blocked=true;
    if(blocked)break;Thread.sleep(2);
   }
   assertTrue("Tick must be blocked before capturing policy generation",blocked);
   set("selectedWire","rt/replacement");set("selectedType","std_msgs::msg::dds_::Float64_");
   call("resubscribe",new Class<?>[]{});
  }
  pending.get(5,TimeUnit.SECONDS);executor.submit(()->{}).get(5,TimeUnit.SECONDS);paint();
  assertEquals(2,bridge.subscriptions);
  assertFalse("Reader A must not reappear as live while B was requested",text(R.id.counts).contains("RECEBENDO"));
  assertNotEquals("42.5",text(R.id.message));
 }
 @Test public void stopInvalidatesQueuedSnapshotReleasesResourcesAndResumeRetainsSettings()throws Exception{
  tick();call("onStop",new Class<?>[]{});((ScheduledExecutorService)field("DDS_WORKER").get(null)).submit(()->{}).get();paint();
  assertFalse((Boolean)get("nativeRunning"));assertNull(get("multicast"));assertEquals("rt/numeric",get("selectedWire"));
  assertEquals("0",a.getSharedPreferences("connection",Context.MODE_PRIVATE).getString("domain","missing"));
  assertTrue(a.getSharedPreferences("connection",Context.MODE_PRIVATE).getBoolean("wanted",false));
 }
}
