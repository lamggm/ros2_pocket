package com.lamggm.rospocket;
import org.json.JSONObject;
/** No fault-control JNI declarations in nondebug source sets. */
final class DeviceTestHooks {
 static final class Ticket {}
 static final class Acquisition {}
 static Ticket ticket(Object lease,Object owner,WatchSlots.Request r,int life,Object config,long policy,String stage){if(lease!=null||stage!=null)throw new IllegalStateException("diagnostic disabled");return null;}
 static Acquisition before(Ticket t,Object owner,WatchSlots.Request r,int life,Object config,long policy){if(t!=null)throw new IllegalStateException("diagnostic disabled");return null;}
 static void after(Acquisition a){}
 static long revision(){return 0;}
 static void invalidate(){}
 static void cancelOwner(Object owner){}
 static void reject(Ticket t){}
 static JSONObject export(long now) throws org.json.JSONException{return new JSONObject().put("valid",false);}
 static void publish(long rev,JSONObject state,JSONObject poll,long time,int life,long policy,WatchSlots.Frame[] frames,Object owner,Object config){}
}
