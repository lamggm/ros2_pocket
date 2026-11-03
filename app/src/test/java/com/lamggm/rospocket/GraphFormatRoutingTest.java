package com.lamggm.rospocket;

import android.widget.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class GraphFormatRoutingTest {
 ConnectionActivityTest c;
 @Before public void before()throws Exception{c=new ConnectionActivityTest();c.setup();}
 @After public void after()throws Exception{c.cleanup();}
 @Test public void selectedFormatReachesWorkerFromImmutableConnectionRequest()throws Exception{
  c.call("disconnect",new Class<?>[]{});
  AtomicReference<String> received=new AtomicReference<>();
  ConnectionActivityTest.Fake routed=new ConnectionActivityTest.Fake(){
   public byte[] startWithGraphFormat(int d,String address,String peer,String format){received.set(format);return super.start(d,address,peer);}
  };
  c.bridge=routed;c.set("bridge",routed);
  Spinner selector=c.a.findViewById(R.id.graph_format);selector.setSelection(1);c.paint();
  c.call("requestConnection",new Class<?>[]{boolean.class},false);assertFalse(selector.isEnabled());
  // A programmatic/framework View draft must not retarget the immutable request.
  selector.setSelection(0);c.paint();c.tick();c.paint();
  assertEquals("Worker must receive the requested Humble format, not legacy/default or a later UI draft","humble",received.get());
  assertEquals(1,routed.starts);
 }
 @Test public void graphErrorRefreshesAnUnchangedEmptyNodeListWithoutPoisoningIntake()throws Exception{
  c.tick();c.paint();
  c.bridge.snapshot=new org.json.JSONObject(c.bridge.snapshot).put("graph",new org.json.JSONObject().put("format","jazzy").put("selection","manual").put("state","unsupported").put("error","GID/layout incompatível")).toString();
  c.set("lastUi",0L);c.tick();c.paint();
  assertTrue("An unsupported graph must not look like an ordinary empty graph",c.text(R.id.nodes).contains("não suportado"));
  assertTrue(c.text(R.id.nodes).contains("GID/layout incompatível"));
  assertTrue("Graph incompatibility must not claim that healthy typed intake failed",c.text(R.id.counts).contains("RECEBENDO"));
 }
}
