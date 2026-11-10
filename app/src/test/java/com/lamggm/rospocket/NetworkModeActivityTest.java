package com.lamggm.rospocket;
import android.content.Context;
import android.net.*;
import android.widget.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class NetworkModeActivityTest {
 ConnectionActivityTest c;
 @Before public void setup()throws Exception{c=new ConnectionActivityTest();c.setup();c.call("disconnect",new Class<?>[]{});}
 @After public void cleanup()throws Exception{c.cleanup();}
 Spinner selector(){int id=c.a.getResources().getIdentifier("network_mode","id",c.a.getPackageName());Spinner mode=c.a.findViewById(id);assertNotNull("Connection must expose explicit LAN/VPN mode",mode);return mode;}
 void vpn(){
  c.cm.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,ConnectivityManager.TYPE_VPN,0,true,true));c.cm.setDefaultNetworkActive(true);
  Network network=((ConnectivityManager)c.a.getSystemService(Context.CONNECTIVITY_SERVICE)).getActiveNetwork();assertNotNull(network);
  NetworkCapabilities caps=new NetworkCapabilities();Shadows.shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI);Shadows.shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_VPN);Shadows.shadowOf(caps).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);c.cm.setNetworkCapabilities(network,caps);
  LinkProperties lp=new LinkProperties();lp.setInterfaceName("tun-test");lp.setLinkAddresses(java.util.Collections.singletonList(org.robolectric.util.ReflectionHelpers.callConstructor(LinkAddress.class,org.robolectric.util.ReflectionHelpers.ClassParameter.from(String.class,"198.51.100.8/32"))));c.cm.setLinkProperties(network,lp);
 }
 @Test public void vpnRequestSelectsTheVpnAndKeepsItsImmutableMode()throws Exception{
  Spinner mode=selector();assertEquals(0,mode.getSelectedItemPosition());vpn();AtomicReference<String> request=new AtomicReference<>();
  ConnectionActivityTest.Fake fake=new ConnectionActivityTest.Fake(){public byte[] startWithNetworkMode(int d,String a,String p,String f,String m){request.set(a+"|"+p+"|"+m);return super.start(d,a,p);}};c.set("bridge",fake);
  mode.setSelection(1);((EditText)c.a.findViewById(R.id.peer)).setText("203.0.113.9");c.paint();c.call("requestConnection",new Class<?>[]{boolean.class},false);assertFalse(mode.isEnabled());mode.setSelection(0);c.paint();c.tick();c.paint();
  assertEquals("198.51.100.8|203.0.113.9|vpn",request.get());assertTrue(c.text(R.id.diagnostics).contains("Rede: VPN"));assertFalse(c.text(R.id.diagnostics).contains("Rede: Wi-Fi"));assertTrue(c.text(R.id.status).contains("VPN"));assertTrue(c.text(R.id.diagnostics).contains("tun-test"));
 }
 @Test public void vpnSensorUsesTheSameImmutableTransportAsTelemetry()throws Exception{
  vpn();AtomicReference<String> sensorMode=new AtomicReference<>();ConnectionActivityTest.Fake fake=new ConnectionActivityTest.Fake(){
   public byte[] startWithNetworkMode(int d,String a,String p,String f,String m){return super.start(d,a,p);}
   public byte[] startSensorWithNetworkMode(int d,String a,String p,String w,String t,String m){sensorMode.set(a+"|"+m);return bytes("{\"ok\":true}");}
  };c.set("bridge",fake);selector().setSelection(1);((EditText)c.a.findViewById(R.id.peer)).setText("203.0.113.9");c.paint();c.call("requestConnection",new Class<?>[]{boolean.class},false);c.tick();c.paint();
  c.a.findViewById(R.id.nav_monitor).performClick();SensorPanel panel=(SensorPanel)c.get("sensorPanel");panel.select("rt/test/scan","sensor_msgs::msg::dds_::LaserScan_");assertNotNull(panel.desired());selector().setSelection(0);c.paint();c.work("tickSensor",new Class<?>[]{int.class},1);assertEquals("198.51.100.8|vpn",sensorMode.get());
 }
 @Test public void sensorAnnouncementFormatComesFromImmutableConnectionRequest()throws Exception{
  vpn();AtomicReference<String> observed=new AtomicReference<>();ConnectionActivityTest.Fake fake=new ConnectionActivityTest.Fake(){
   public byte[] startWithNetworkMode(int d,String a,String p,String f,String m){return super.start(d,a,p);}
   public byte[] startSensorConfigured(int d,String a,String p,String w,String t,String m,String f){observed.set(a+"|"+m+"|"+f);return bytes("{\"ok\":true}");}
  };c.set("bridge",fake);selector().setSelection(1);((Spinner)c.a.findViewById(R.id.graph_format)).setSelection(1);((EditText)c.a.findViewById(R.id.peer)).setText("203.0.113.9");c.paint();c.call("requestConnection",new Class<?>[]{boolean.class},false);c.tick();c.paint();
  c.a.findViewById(R.id.nav_monitor).performClick();SensorPanel panel=(SensorPanel)c.get("sensorPanel");panel.select("rt/test/scan","sensor_msgs::msg::dds_::LaserScan_");selector().setSelection(0);((Spinner)c.a.findViewById(R.id.graph_format)).setSelection(0);c.paint();c.work("tickSensor",new Class<?>[]{int.class},1);assertEquals("198.51.100.8|vpn|humble",observed.get());
 }
 @Test public void appliedProfileAndCaptureUseTheirExplicitNetworkMode()throws Exception{
  BenchProfiles.Profile old=BenchProfiles.decode(BenchProfilesTest.catalog(BenchProfilesTest.profile())).profiles.get(0);
  BenchProfiles.Profile vpn=new BenchProfiles.Profile(old.id,old.name,old.domain,old.peer,old.watches,old.plots,old.visual,old.graphFormat,"vpn");
  c.call("loadProfile",new Class<?>[]{BenchProfiles.Profile.class},vpn);c.paint();assertEquals("Applying a VPN profile must not retain a LAN selector",1,selector().getSelectedItemPosition());
  java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("captureProfile",String.class,String.class);m.setAccessible(true);BenchProfiles.Profile captured=(BenchProfiles.Profile)m.invoke(c.a,old.id,"Captured");assertEquals("vpn",captured.networkMode);
 }
 @Test public void unavailableVpnCannotFallBackToThePresentWifi()throws Exception{
  selector().setSelection(1);((EditText)c.a.findViewById(R.id.peer)).setText("203.0.113.9");c.paint();c.call("requestConnection",new Class<?>[]{boolean.class},false);c.tick();c.paint();assertEquals(0,c.bridge.starts);assertTrue(c.text(R.id.status).contains("VPN"));
 }
 @Test public void vpnRequiresAnExplicitPeerBeforeStartingAnyReader()throws Exception{
  vpn();selector().setSelection(1);((EditText)c.a.findViewById(R.id.peer)).setText("");c.paint();c.call("requestConnection",new Class<?>[]{boolean.class},false);c.paint();assertEquals(false,c.get("connectWanted"));assertEquals(0,c.bridge.starts);assertTrue(c.text(R.id.status).contains("peer IPv4 explícito"));
 }
}
