package com.lamggm.rospocket;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
final class NativeBridge {
 static {System.loadLibrary("rospocket");}
 static native byte[] start(int domain,String address,String peer);
 static native byte[] poll();
 static native byte[] subscribe(String topic,String type,boolean reliable,boolean transientLocal);
 static native void stop();
 static JSONObject json(byte[] bytes) throws org.json.JSONException { return new JSONObject(new String(bytes,StandardCharsets.UTF_8)); }
}
