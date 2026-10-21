import com.lamggm.rospocket.ConnectionPolicy;
public class ConnectionPolicyTest {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  check(ConnectionPolicy.fresh(true,0.5,0,1_000_000_000L));
  check(!ConnectionPolicy.fresh(true,0.5,0,4_000_000_000L));
  check(!ConnectionPolicy.fresh(true,-1,0,0));
  check(!ConnectionPolicy.fresh(true,Double.NaN,0,0));
  check(!ConnectionPolicy.fresh(false,0.1,0,0));
  check(!ConnectionPolicy.fresh(true,0.1,10,0));
  ConnectionPolicy p=new ConnectionPolicy();
  p.identity("wifi-a/address-a");long old=p.generation();
  check(p.due(0));p.failed(0,"start failed");
  check(!p.due(999));check(p.due(1000));check(p.error().equals("start failed"));
  p.failed(1000,"start failed");check(!p.due(2999));check(p.due(3000));
  long now=3000;for(int i=0;i<20;i++){p.failed(now,"failed");check(p.delayMillis()==(i==0?4000:i==1?8000:i==2?16000:30000));now+=p.delayMillis();check(p.due(now));}
  check(p.delayMillis()==30000);check(!p.due(now-30001)); // backward input cannot expire deadline
  p.identity("wifi-a/address-b");check(p.due(0));check(!p.accepts(old));
  long current=p.generation();p.recovered();check(p.error().isEmpty());check(p.accepts(current));
  // Polling a healthy session with no peers never drives session retry decisions.
  check(!ConnectionPolicy.needsStart(true,false,false));
  check(ConnectionPolicy.needsStart(true,true,false));
  check(ConnectionPolicy.needsStart(false,false,false));
  check(ConnectionPolicy.needsStart(true,false,true));
  p.failed(-10000,"negative monotonic origin");check(!p.due(-9001));check(p.due(-9000));
  p.invalidate();check(!p.accepts(current));p.reset();check(p.due(0));check(p.error().isEmpty());
  System.out.println("PASS retry deadlines, cap, identity, generation, reset and no-peer policy");
 }
}
