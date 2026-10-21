package com.lamggm.rospocket;

/** Portable foreground retry/fencing policy. Times are monotonic milliseconds, never wall time. */
public final class ConnectionPolicy {
 private long generation, failedAt, delay;
 private String identity="", error="";
 public synchronized long generation(){return generation;}
 public synchronized boolean accepts(long token){return token==generation;}
 public synchronized void invalidate(){generation++;}
 public synchronized void identity(String value){if(!identity.equals(value)){identity=value;invalidate();resetDelay();}}
 private void resetDelay(){delay=0;failedAt=0;}
 public synchronized void reset(){invalidate();identity="";resetDelay();error="";}
 public synchronized boolean due(long now){return delay==0 || now-failedAt>=delay;}
 public synchronized void failed(long now,String message){failedAt=now;delay=delay==0?1000:Math.min(30000,delay*2);error=message;}
 public synchronized void recovered(){resetDelay();error="";}
 public synchronized long delayMillis(){return delay;}
 public synchronized String error(){return error;}
 public static boolean fresh(boolean nativeFresh,double age,long capturedNanos,long nowNanos){
  long elapsed=nowNanos-capturedNanos;
  return nativeFresh&&elapsed>=0&&age>=0&&age+elapsed/1_000_000_000.0<=3;
 }
 public static boolean needsStart(boolean running,boolean configChanged,boolean networkChanged){return !running||configChanged||networkChanged;}
}
