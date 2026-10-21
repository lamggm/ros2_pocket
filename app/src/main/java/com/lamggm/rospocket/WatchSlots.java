package com.lamggm.rospocket;

/** Thread-safe desired/applied identities; immutable requests fence old snapshots.
 * Slot zero remains the legacy Activity's primary reader; extras own slots 1..3.
 */
final class WatchSlots {
 static final class Request {
  final int slot; final long version; final String wire,type; final boolean reliable,transientLocal;
  Request(int s,long v,String w,String t,boolean r,boolean l){slot=s;version=v;wire=w;type=t;reliable=r;transientLocal=l;}
 }
 static final class Frame {
  final Request request; final boolean pending; final String error;
  Frame(Request r,boolean p,String e){request=r;pending=p;error=e;}
 }
 private final Request[] desired=new Request[4];
 private final long[] applied={-1,-1,-1,-1};
 private final String[] errors={"","","",""};
 WatchSlots(){for(int i=0;i<4;i++)desired[i]=new Request(i,0,"","",false,false);}
 synchronized Request get(int slot){return desired[slot];}
 synchronized Request request(int slot,String w,String t,boolean r,boolean l){
  Request next=new Request(slot,desired[slot].version+1,w,t,r,l);desired[slot]=next;errors[slot]="";return next;
 }
 synchronized boolean current(Request r){return desired[r.slot]==r;}
 synchronized void complete(Request r,String e){if(current(r)){applied[r.slot]=r.version;errors[r.slot]=e;}}
 synchronized Frame frame(int slot){Request r=desired[slot];return new Frame(r,!r.wire.isEmpty()&&applied[slot]!=r.version,errors[slot]);}
 synchronized void resetApplied(){for(int i=0;i<4;i++)applied[i]=-1;}
}
