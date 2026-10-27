package com.lamggm.rospocket;

import android.content.Context;
import android.widget.CheckBox;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.nio.file.*;
import java.lang.invoke.MethodHandles;

import java.util.*;
import static org.junit.Assert.*;

/** Executes the actual androidTest runner body in the host framework runtime, never DDS. */
@RunWith(org.robolectric.RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class ReaderFailureRunnerTest {
 @org.junit.Before public void processIdentity(){org.robolectric.shadows.ShadowProcess.setPid(1234);}
 static Class<?> probe;
 static synchronized Class<?> probe() throws Exception {
  if(probe!=null)return probe;
  Path source=Paths.get("src/androidTest/java/com/lamggm/rospocket/ReaderFailureInstrumentation.java");
  Path dir=Files.createTempDirectory(Paths.get(System.getenv("TMPDIR")),"runner-behavior-");
  Path runner=dir.resolve("ReaderFailureInstrumentation.java");
  // Only the test-copy's final modifier is removed to override framework transport;
  // all production runner method bodies are compiled unchanged, including await(); projection is widened only for scripted readiness input.
  Files.write(runner,new String(Files.readAllBytes(source),java.nio.charset.StandardCharsets.UTF_8).replace("public final class ReaderFailureInstrumentation","public class ReaderFailureInstrumentation").replace("private JSONObject projection()","protected JSONObject projection()").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  Path adapter=dir.resolve("RunnerProbe.java");
  Files.write(adapter,("package com.lamggm.rospocket; public class RunnerProbe extends ReaderFailureInstrumentation { public static MainActivity target; public static boolean reliable,transientLocal; public static int calls; public static boolean stopSetup=true; public static java.util.concurrent.Callable<org.json.JSONObject> source; protected org.json.JSONObject projection() throws Exception {return source==null?super.projection():source.call();} public static android.os.Bundle result; public android.content.Context getTargetContext(){return target;} public android.app.Activity startActivitySync(android.content.Intent i){return target;} public void waitForIdleSync(){} public void runOnMainSync(Runnable r){r.run();if(++calls==1 && stopSetup){reliable=((android.widget.CheckBox)target.findViewById(R.id.qos_reliable)).isChecked();transientLocal=((android.widget.CheckBox)target.findViewById(R.id.qos_transient)).isChecked();throw new AssertionError(\"stop after actual setup\");}} public void finish(int c,android.os.Bundle b){result=b;} }").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  String cp=System.getProperty("java.class.path");
  for(Class<?> c:new Class<?>[]{MainActivity.class,DeviceTestHooks.class,BuildConfig.class,R.class,android.app.Instrumentation.class,org.json.JSONObject.class}){
   java.net.URL url=c.getResource("/"+c.getName().replace('.','/')+".class");
   if(url!=null&&url.getProtocol().equals("jar"))cp+=java.io.File.pathSeparator+((java.net.JarURLConnection)url.openConnection()).getJarFileURL().getPath();
   else if(c.getProtectionDomain().getCodeSource()!=null&&c.getProtectionDomain().getCodeSource().getLocation()!=null)cp+=java.io.File.pathSeparator+c.getProtectionDomain().getCodeSource().getLocation().getPath();
  }
  Object compiler=Class.forName("javax.tools.ToolProvider").getMethod("getSystemJavaCompiler").invoke(null);
  int status=(Integer)Class.forName("javax.tools.Tool").getMethod("run",java.io.InputStream.class,java.io.OutputStream.class,java.io.OutputStream.class,String[].class).invoke(compiler,null,null,null,new String[]{"-classpath",cp,"-d",dir.toString(),runner.toString(),adapter.toString()});
  assertEquals("actual runner must compile; setup errors are not behavioral RED",0,status);
  List<Path> classes=new ArrayList<>();try(java.util.stream.Stream<Path> files=Files.walk(dir)){files.filter(p->p.toString().endsWith(".class")).forEach(classes::add);}
  classes.sort(Comparator.comparing(p->p.getFileName().toString().equals("RunnerProbe.class")?2:p.getFileName().toString().equals("ReaderFailureInstrumentation.class")?1:0));
  for(Path p:classes)MethodHandles.Lookup.class.getMethod("defineClass",byte[].class).invoke(MethodHandles.lookup(),Files.readAllBytes(p));
  return probe=Class.forName("com.lamggm.rospocket.RunnerProbe",true,ReaderFailureRunnerTest.class.getClassLoader());
 }
 static Object runner(Class<?> p,MainActivity a) throws Exception {
  p.getField("target").set(null,a);p.getField("calls").setInt(null,0);p.getField("result").set(null,null);
  Object runner=p.getConstructor().newInstance();java.lang.reflect.Field arguments=p.getSuperclass().getDeclaredField("arguments");arguments.setAccessible(true);arguments.set(runner,new android.os.Bundle());return runner;
 }
 static org.json.JSONArray topics(int count) throws Exception {
  org.json.JSONArray topics=new org.json.JSONArray();for(int i=0;i<count;i++)topics.put(new org.json.JSONObject().put("name","/pocket_diagnostic/watch"+i).put("type","std_msgs/msg/String").put("wireName","rt/pocket_diagnostic/watch"+i).put("wireType","std_msgs::msg::dds_::String_").put("supported",true));return topics;
 }
 @Test public void incrementalDiscoveryAndLateExplorerPublicationWaitForEveryExactFixtureView() throws Exception {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().visible().get();Class<?> p=probe();Object runner=runner(p,a);
  int[] observations={0};p.getField("stopSetup").setBoolean(null,false);
  p.getField("source").set(null,(java.util.concurrent.Callable<org.json.JSONObject>)()->{
   int n=++observations[0];if(n==8)throw new AssertionError("stop after all actual selections");
   org.json.JSONArray set=topics(n==1?1:4);
   if(n==3)((ExplorerView)a.findViewById(R.id.topics)).update(set);
   return new org.json.JSONObject().put("poll",new org.json.JSONObject().put("topics",set));
  });
  java.lang.reflect.Field f=DeviceTestHooks.Controller.class.getDeclaredField("control");f.setAccessible(true);Object old=f.get(DeviceTestHooks.CONTROLLER);
  try{f.set(DeviceTestHooks.CONTROLLER,new DeviceTestHooksTest.Fake());p.getMethod("onStart").invoke(runner);
   android.os.Bundle result=(android.os.Bundle)p.getField("result").get(null);
   assertEquals("must not fail on progressive discovery or late Views","java.lang.AssertionError: stop after all actual selections",result.getString("failure"));assertEquals(8,observations[0]);
  }finally{f.set(DeviceTestHooks.CONTROLLER,old);p.getField("source").set(null,null);p.getField("stopSetup").setBoolean(null,true);}
 }
 @Test public void hiddenOrDisabledExplorerViewsAreNotReadyEvenWithCompleteDiscovery() throws Exception {
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().visible().get();Class<?> p=probe();Object runner=runner(p,a);p.getField("stopSetup").setBoolean(null,false);
  java.lang.reflect.Field activity=p.getSuperclass().getDeclaredField("activity");activity.setAccessible(true);activity.set(runner,a);
  java.lang.reflect.Method ready=p.getSuperclass().getDeclaredMethod("fixtureReady",org.json.JSONObject.class);ready.setAccessible(true);
  ExplorerView explorer=a.findViewById(R.id.topics);org.json.JSONArray set=topics(4);explorer.update(set);org.json.JSONObject poll=new org.json.JSONObject().put("topics",set);
  try{explorer.setVisibility(android.view.View.INVISIBLE);assertFalse("hidden ancestor is not rendered",(Boolean)ready.invoke(runner,poll));explorer.setVisibility(android.view.View.VISIBLE);
   android.view.View read=explorer.findViewWithTag("read:rt/pocket_diagnostic/watch2\nstd_msgs::msg::dds_::String_");read.setEnabled(false);assertFalse((Boolean)ready.invoke(runner,poll));read.setEnabled(true);assertTrue("positive control requires actual enabled shown Views",(Boolean)ready.invoke(runner,poll));
  }finally{p.getField("stopSetup").setBoolean(null,true);}
 }
 @Test public void malformedThenValidConditionCannotBeHiddenByAwait() throws Exception {
  for(Exception invalid:new Exception[]{new org.json.JSONException("original malformed poll"),new IllegalStateException("original foreign source")}){
   Class<?> p=probe();Object runner=p.getConstructor().newInstance();Class<?> condition=Class.forName("com.lamggm.rospocket.ReaderFailureInstrumentation$Condition",true,p.getClassLoader());
   int[] attempts={0};Object c=java.lang.reflect.Proxy.newProxyInstance(p.getClassLoader(),new Class<?>[]{condition},(o,m,args)->{if(++attempts[0]==1)throw invalid;return true;});
   java.lang.reflect.Method await=p.getSuperclass().getDeclaredMethod("await",condition);await.setAccessible(true);
   try{await.invoke(runner,c);fail("invalid observation followed by good input was concealed");}catch(java.lang.reflect.InvocationTargetException error){assertSame("preserve original diagnostic",invalid,error.getCause());}
   assertEquals("malformed observation must fail immediately",1,attempts[0]);
  }
 }
 static org.json.JSONObject validPoll() throws Exception {
  org.json.JSONArray rows=new org.json.JSONArray();for(int i=0;i<4;i++)rows.put(new org.json.JSONObject().put("slot",i).put("generation",7).put("count",1).put("matched",1).put("readerActive",true).put("fresh",true).put("age",0.1).put("receivedMonotonicNs",123).put("subscription","/pocket_diagnostic/watch"+i).put("type","std_msgs::msg::dds_::String_").put("last","value").put("error","").put("subscriptionState","receiving").put("message",org.json.JSONObject.NULL));
  return new org.json.JSONObject().put("sessionGeneration",3).put("running",true).put("topics",topics(4)).put("watches",rows).put("rosout",new org.json.JSONObject().put("entries",new org.json.JSONArray().put(new org.json.JSONObject().put("logger","pocket_diagnostic.logger").put("line",1))));
 }
 static void publish(DeviceTestHooksTest.Fake n) throws Exception {
  DeviceTestHooks.Controller c=DeviceTestHooks.CONTROLLER;
  assertTrue(c.publish(c.revision(),new org.json.JSONObject(n.state()).put("revision",1).put("session",3),validPoll(),System.nanoTime(),1,2,new WatchSlots().frames()));
 }
 static void lease(Object runner,DeviceTestHooks.Lease l) throws Exception {java.lang.reflect.Field f=runner.getClass().getSuperclass().getDeclaredField("lease");f.setAccessible(true);f.set(runner,l);}
 static org.json.JSONObject actualProjection(Object runner) throws Throwable {
  java.lang.reflect.Method m=runner.getClass().getSuperclass().getDeclaredMethod("projection");m.setAccessible(true);
  try{return (org.json.JSONObject)m.invoke(runner);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
 }
 @Test public void malformedWrongTypedOrForeignPollFollowedByValidPollStillFailsImmediately() throws Exception {
  Class<?> p=probe();Object runner=p.getConstructor().newInstance();java.lang.reflect.Field f=DeviceTestHooks.Controller.class.getDeclaredField("control");f.setAccessible(true);Object old=f.get(DeviceTestHooks.CONTROLLER);
  DeviceTestHooksTest.Fake n=new DeviceTestHooksTest.Fake();DeviceTestHooks.Lease l=null;
  try{f.set(DeviceTestHooks.CONTROLLER,n);l=DeviceTestHooks.CONTROLLER.begin(MainActivity.diagnosticWorker());lease(runner,l);
   java.util.List<String> bad=new java.util.ArrayList<>();bad.add("{malformed");
   org.json.JSONObject wrong=validPoll();wrong.getJSONArray("watches").getJSONObject(0).put("count","1");bad.add(wrong.toString());
   wrong=validPoll();wrong.getJSONArray("watches").getJSONObject(0).put("fresh","true");bad.add(wrong.toString());
   wrong=validPoll();wrong.getJSONArray("watches").getJSONObject(0).remove("matched");bad.add(wrong.toString());
   bad.add(validPoll().put("sessionGeneration",4).toString());
   Class<?> condition=Class.forName("com.lamggm.rospocket.ReaderFailureInstrumentation$Condition",true,p.getClassLoader());
   java.lang.reflect.Method await=p.getSuperclass().getDeclaredMethod("await",condition);await.setAccessible(true);
   java.lang.reflect.Field raw=DeviceTestHooks.Controller.class.getDeclaredField("projection");raw.setAccessible(true);
   for(String invalid:bad){publish(n);raw.set(DeviceTestHooks.CONTROLLER,invalid);int[] attempts={0};
    Object c=java.lang.reflect.Proxy.newProxyInstance(p.getClassLoader(),new Class<?>[]{condition},(o,m,args)->{attempts[0]++;try{actualProjection(runner);return true;}finally{publish(n);}});
    try{await.invoke(runner,c);fail("invalid poll was accepted: "+invalid);}catch(java.lang.reflect.InvocationTargetException e){assertTrue("must retain input error, not timeout: "+e.getCause(),e.getCause() instanceof org.json.JSONException||e.getCause() instanceof IllegalStateException);}
    assertEquals("invalid input cannot be retried",1,attempts[0]);
   }
  }finally{if(l!=null)assertTrue(DeviceTestHooks.CONTROLLER.end(l,MainActivity.diagnosticWorker()).get());f.set(DeviceTestHooks.CONTROLLER,old);}
 }
 @Test public void genuineMissingCaptureWaitsAtRetrievalButForeignLeaseIsNotReadiness() throws Exception {
  Class<?> p=probe();Object runner=p.getConstructor().newInstance();java.lang.reflect.Field f=DeviceTestHooks.Controller.class.getDeclaredField("control");f.setAccessible(true);Object old=f.get(DeviceTestHooks.CONTROLLER);
  DeviceTestHooksTest.Fake n=new DeviceTestHooksTest.Fake();DeviceTestHooks.Lease l=null;
  java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
  try{f.set(DeviceTestHooks.CONTROLLER,n);l=DeviceTestHooks.CONTROLLER.begin(MainActivity.diagnosticWorker());lease(runner,l);DeviceTestHooks.CONTROLLER.invalidate();
   Thread thread=executor.submit(()->Thread.currentThread()).get();
   java.util.concurrent.Future<org.json.JSONObject> ready=executor.submit(()->{try{return actualProjection(runner);}catch(Throwable e){throw new RuntimeException(e);}});
   long end=System.nanoTime()+2_000_000_000L;boolean waiting=false;
   while(System.nanoTime()<end){for(StackTraceElement frame:thread.getStackTrace())if(frame.getClassName().endsWith("ReaderFailureInstrumentation")&&frame.getMethodName().equals("projection")&&thread.getState()==Thread.State.TIMED_WAITING)waiting=true;if(waiting)break;Thread.sleep(2);}
   assertTrue("only retrieval should retry genuinely missing capture",waiting);publish(n);assertNotNull(ready.get(2,java.util.concurrent.TimeUnit.SECONDS));
   lease(runner,new DeviceTestHooks.Lease(l.epoch+1));
   try{actualProjection(runner);fail("foreign lease accepted");}catch(Throwable e){assertTrue(e instanceof IllegalStateException);assertFalse(e instanceof DeviceTestHooks.NotCaptured);}
  }finally{executor.shutdownNow();assertTrue(executor.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS));if(l!=null)assertTrue(DeviceTestHooks.CONTROLLER.end(l,MainActivity.diagnosticWorker()).get());f.set(DeviceTestHooks.CONTROLLER,old);}
 }
 @Test public void persistedTransientLocalIsExplicitlyDisabledBeforeFixtureIntakeAndPreferencesRestored() throws Exception {
  Context ctx=RuntimeEnvironment.getApplication();
  ctx.getSharedPreferences("connection",Context.MODE_PRIVATE).edit().putBoolean("reliable",true).putBoolean("transient",true).commit();
  MainActivity a=Robolectric.buildActivity(MainActivity.class).create().get();
  assertTrue(((CheckBox)a.findViewById(R.id.qos_transient)).isChecked());
  Class<?> p=probe();p.getField("target").set(null,a);p.getField("calls").setInt(null,0);
  java.lang.reflect.Field f=DeviceTestHooks.Controller.class.getDeclaredField("control");f.setAccessible(true);Object old=f.get(DeviceTestHooks.CONTROLLER);
  try{f.set(DeviceTestHooks.CONTROLLER,new DeviceTestHooksTest.Fake());
   Object runner=p.getConstructor().newInstance();java.lang.reflect.Field arguments=p.getSuperclass().getDeclaredField("arguments");arguments.setAccessible(true);arguments.set(runner,new android.os.Bundle());
   p.getMethod("onStart").invoke(runner);
   assertFalse("baseline must request best-effort",p.getField("reliable").getBoolean(null));
   assertFalse("volatile fixture cannot satisfy persisted transient-local",p.getField("transientLocal").getBoolean(null));
   assertTrue("user primary QoS restored",ctx.getSharedPreferences("connection",Context.MODE_PRIVATE).getBoolean("transient",false));
  }finally{f.set(DeviceTestHooks.CONTROLLER,old);}
 }
}
