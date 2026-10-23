package com.lamggm.rospocket;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Run in BOTH debug and release variants. Creating the Activity never starts DDS. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class QaDumpGateTest {
 @Test public void exactReadOnlyCommandIsDebugOnly(){
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).create().get();
  StringWriter text=new StringWriter();
  activity.dump("",null,new PrintWriter(text),new String[]{"--pocket-qa-v1"});
  assertEquals(BuildConfig.DEBUG,text.toString().contains("ROS_POCKET_QA_V1_BEGIN"));
  if(!BuildConfig.DEBUG)assertEquals("ROS Pocket QA unavailable",text.toString().trim());
 }
 @Test public void extraArgumentsCannotActivateQaExportOrControls(){
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).create().get();
  StringWriter text=new StringWriter();
  activity.dump("",null,new PrintWriter(text),new String[]{"--pocket-qa-v1","arm"});
  assertFalse(text.toString().contains("ROS_POCKET_QA_V1_BEGIN"));
 }
}
