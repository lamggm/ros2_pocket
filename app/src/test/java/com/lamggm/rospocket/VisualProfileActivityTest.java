package com.lamggm.rospocket;

import android.widget.*;
import java.io.File;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real profile commands with the inherited, explicit JNI double. */
public class VisualProfileActivityTest extends ConnectionActivityTest {
 private SensorPanel sensor(){return a.getWindow().getDecorView().findViewWithTag("sensor-panel");}
 private BenchProfiles book()throws Exception{return new ProfileStore(new File(a.getFilesDir(),"bench-profiles-v1.json")).load();}
 private void save(){((EditText)a.findViewById(R.id.profile_name)).setText("Visual");a.findViewById(R.id.profile_save).performClick();}
 private void drain()throws Exception{((ScheduledExecutorService)field("DDS_WORKER").get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);paint();}
 @Test public void savedScanAndCameraRestoreTogetherOnlyAfterExplicitDisconnectedApply()throws Exception {
  sensor().select("rt/bench/scan","sensor_msgs::msg::dds_::LaserScan_");sensor().select("rt/bench/camera","sensor_msgs::msg::dds_::CompressedImage_");save();
  BenchProfiles.Profile p=book().profiles.get(0);assertEquals("camera",p.visual.mode);assertEquals("rt/bench/scan",p.visual.scan.wire);assertEquals("rt/bench/camera",p.visual.camera.wire);
  int starts=bridge.starts;assertNull(book().applied);a.findViewById(R.id.profile_apply).performClick();assertNull("Connected application must be refused",book().applied);
  a.findViewById(R.id.connect).performClick();drain();sensor().select("rt/other","sensor_msgs::msg::dds_::LaserScan_");a.findViewById(R.id.profile_apply).performClick();
  assertFalse((Boolean)get("connectWanted"));assertNull("Disconnected profile intent must not open a visual reader",sensor().desired());assertEquals(starts,bridge.starts);
  a.findViewById(R.id.connect).performClick();a.findViewById(R.id.nav_monitor).performClick();assertNotNull(sensor().desired());assertEquals("rt/bench/camera",sensor().desired().wire);
  ((Button)sensor().findViewWithTag("sensor-LaserScan")).performClick();assertEquals("rt/bench/scan",sensor().desired().wire);
 }
 @Test public void appliedVisualIntentRestartsDisconnectedAndWaitsForFreshSensorData()throws Exception {
  sensor().select("rt/bench/scan","sensor_msgs::msg::dds_::LaserScan_");save();a.findViewById(R.id.connect).performClick();drain();a.findViewById(R.id.profile_apply).performClick();
  MainActivity old=a;call("onStop",new Class<?>[]{});drain();a=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();a.bridge=new Fake();
  try{
   assertFalse((Boolean)get("connectWanted"));assertNull(sensor().desired());assertFalse(((TextView)sensor().findViewWithTag("sensor-status")).getText().toString().contains("RECEBENDO"));
   a.findViewById(R.id.connect).performClick();a.findViewById(R.id.nav_monitor).performClick();assertNotNull(sensor().desired());assertEquals("rt/bench/scan",sensor().desired().wire);assertEquals(0,((Fake)a.bridge).starts);
  }finally{a=old;}
 }
}
