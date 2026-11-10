package com.lamggm.rospocket;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
final class NativeBridge {
 // Preserve initialization for all existing direct JNI callers.
 static {System.loadLibrary("rospocket");}
 // Initializing this nested interface does not initialize the JNI class. Android tests
 // use a JNI double and this parser, without loading an incompatible host .so.
 interface Api {
  byte[] start(int domain,String address,String peer);
  default byte[] startWithGraphFormat(int domain,String address,String peer,String format){
   if("jazzy".equals(format))return start(domain,address,peer);
   return "{\"ok\":false,\"error\":\"Formato de grafo indisponível nesta ponte\"}".getBytes(StandardCharsets.UTF_8);
  }
  byte[] poll();
  default byte[] startWithNetworkMode(int d,String a,String p,String f,String mode){
   if("lan".equals(mode))return startWithGraphFormat(d,a,p,f);
   return "{\"ok\":false,\"error\":\"Modo VPN indisponível nesta ponte\"}".getBytes(StandardCharsets.UTF_8);
  }
  // Legacy host doubles can keep latest-snapshot intake; production JNI overrides this.
  default byte[] selectNumeric(long epoch,int[] slots,String[] paths){return "{\"ok\":true,\"legacy\":true}".getBytes(StandardCharsets.UTF_8);}
  default byte[] diagnosticState(){return "{}".getBytes(StandardCharsets.UTF_8);}
  byte[] subscribe(String topic,String type,boolean reliable,boolean transientLocal);
  default byte[] watch(int slot,String topic,String type,boolean reliable,boolean transientLocal){return ("{\"ok\":false,\"error\":\"Multiwatch indisponível nesta ponte\"}").getBytes(StandardCharsets.UTF_8);}
  default byte[] removeWatch(int slot){return ("{\"ok\":false,\"error\":\"Multiwatch indisponível nesta ponte\"}").getBytes(StandardCharsets.UTF_8);}
  void stop();
  default void clearRosout() {}
  default byte[] startSensor(int d,String a,String p,String w,String t){return "{\"ok\":false,\"error\":\"Ponte visual indisponível\"}".getBytes(StandardCharsets.UTF_8);}
  default byte[] startSensorWithNetworkMode(int d,String a,String p,String w,String t,String mode){
   if("lan".equals(mode))return startSensor(d,a,p,w,t);
   return "{\"ok\":false,\"error\":\"Modo VPN visual indisponível nesta ponte\"}".getBytes(StandardCharsets.UTF_8);
  }
  default byte[] startSensorConfigured(int d,String a,String p,String w,String t,String mode,String format){
   if("jazzy".equals(format))return startSensorWithNetworkMode(d,a,p,w,t,mode);
   return "{\"ok\":false,\"error\":\"Formato visual indisponível nesta ponte\"}".getBytes(StandardCharsets.UTF_8);
  }
  default byte[] pollSensor(){return new byte[0];}
  default void stopSensor() {}
  static JSONObject json(byte[] bytes) throws org.json.JSONException {return new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
 }
 static final class JniApi implements Api {
  public byte[] start(int d,String a,String p){return NativeBridge.start(d,a,p);}
  public byte[] startWithGraphFormat(int d,String a,String p,String format){return NativeBridge.startWithGraphFormat(d,a,p,format);}
  public byte[] startWithNetworkMode(int d,String a,String p,String f,String m){return NativeBridge.startWithNetworkMode(d,a,p,f,m);}
  public byte[] poll(){return NativeBridge.poll();}
  public byte[] selectNumeric(long epoch,int[] slots,String[] paths){return NativeBridge.selectNumeric(epoch,slots,paths);}
  public byte[] diagnosticState(){return NativeBridge.diagnosticState();}
  public byte[] subscribe(String w,String t,boolean r,boolean l){return NativeBridge.subscribe(w,t,r,l);}
  public byte[] watch(int s,String w,String t,boolean r,boolean l){return NativeBridge.watch(s,w,t,r,l);}
  public byte[] removeWatch(int s){return NativeBridge.removeWatch(s);}
  public void stop(){NativeBridge.stop();}
  public void clearRosout(){NativeBridge.clearRosout();}
  public byte[] startSensor(int d,String a,String p,String w,String t){return NativeBridge.startSensor(d,a,p,w,t);}
  public byte[] startSensorWithNetworkMode(int d,String a,String p,String w,String t,String m){return NativeBridge.startSensorWithNetworkMode(d,a,p,w,t,m);}
  public byte[] startSensorConfigured(int d,String a,String p,String w,String t,String m,String f){return NativeBridge.startSensorConfigured(d,a,p,w,t,m,f);}
  public byte[] pollSensor(){return NativeBridge.pollSensor();}
  public void stopSensor(){NativeBridge.stopSensor();}
 }
 static native byte[] start(int domain,String address,String peer);
 static native byte[] startWithGraphFormat(int domain,String address,String peer,String format);
 static native byte[] startWithNetworkMode(int domain,String address,String peer,String format,String mode);
 static native byte[] poll();
 static native byte[] selectNumeric(long epoch,int[] slots,String[] paths);
 static native byte[] diagnosticState();
 static native byte[] subscribe(String topic,String type,boolean reliable,boolean transientLocal);
 static native byte[] watch(int slot,String topic,String type,boolean reliable,boolean transientLocal);
 static native byte[] removeWatch(int slot);
 static native void stop();
 static native void clearRosout();
 static native byte[] startSensor(int d,String a,String p,String w,String t);
 static native byte[] startSensorWithNetworkMode(int d,String a,String p,String w,String t,String mode);
 static native byte[] startSensorConfigured(int d,String a,String p,String w,String t,String mode,String format);
 static native byte[] pollSensor();
 static native void stopSensor();
 static JSONObject json(byte[] bytes) throws org.json.JSONException {return Api.json(bytes);}
}
