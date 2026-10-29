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
  byte[] poll();
  default byte[] diagnosticState(){return "{}".getBytes(StandardCharsets.UTF_8);}
  byte[] subscribe(String topic,String type,boolean reliable,boolean transientLocal);
  default byte[] watch(int slot,String topic,String type,boolean reliable,boolean transientLocal){return ("{\"ok\":false,\"error\":\"Multiwatch indisponível nesta ponte\"}").getBytes(StandardCharsets.UTF_8);}
  default byte[] removeWatch(int slot){return ("{\"ok\":false,\"error\":\"Multiwatch indisponível nesta ponte\"}").getBytes(StandardCharsets.UTF_8);}
  void stop();
  default void clearRosout() {}
  default byte[] startSensor(int d,String a,String p,String w,String t){return "{\"ok\":false,\"error\":\"Ponte visual indisponível\"}".getBytes(StandardCharsets.UTF_8);}
  default byte[] pollSensor(){return new byte[0];}
  default void stopSensor() {}
  static JSONObject json(byte[] bytes) throws org.json.JSONException {return new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
 }
 static final class JniApi implements Api {
  public byte[] start(int d,String a,String p){return NativeBridge.start(d,a,p);}
  public byte[] poll(){return NativeBridge.poll();}
  public byte[] diagnosticState(){return NativeBridge.diagnosticState();}
  public byte[] subscribe(String w,String t,boolean r,boolean l){return NativeBridge.subscribe(w,t,r,l);}
  public byte[] watch(int s,String w,String t,boolean r,boolean l){return NativeBridge.watch(s,w,t,r,l);}
  public byte[] removeWatch(int s){return NativeBridge.removeWatch(s);}
  public void stop(){NativeBridge.stop();}
  public void clearRosout(){NativeBridge.clearRosout();}
  public byte[] startSensor(int d,String a,String p,String w,String t){return NativeBridge.startSensor(d,a,p,w,t);}
  public byte[] pollSensor(){return NativeBridge.pollSensor();}
  public void stopSensor(){NativeBridge.stopSensor();}
 }
 static native byte[] start(int domain,String address,String peer);
 static native byte[] poll();
 static native byte[] diagnosticState();
 static native byte[] subscribe(String topic,String type,boolean reliable,boolean transientLocal);
 static native byte[] watch(int slot,String topic,String type,boolean reliable,boolean transientLocal);
 static native byte[] removeWatch(int slot);
 static native void stop();
 static native void clearRosout();
 static native byte[] startSensor(int d,String a,String p,String w,String t);
 static native byte[] pollSensor();
 static native void stopSensor();
 static JSONObject json(byte[] bytes) throws org.json.JSONException {return Api.json(bytes);}
}
