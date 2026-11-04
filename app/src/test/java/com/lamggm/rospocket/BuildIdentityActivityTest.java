package com.lamggm.rospocket;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

/** Actual generated BuildConfig and Android Views; no JNI or device qualification. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=31)
@LooperMode(LooperMode.Mode.PAUSED)
public class BuildIdentityActivityTest {
 private static int matchingText(View view,String expected) {
  int matches=view instanceof TextView && expected.contentEquals(((TextView)view).getText()) ? 1 : 0;
  if(view instanceof ViewGroup) {
   ViewGroup group=(ViewGroup)view;
   for(int i=0;i<group.getChildCount();i++) matches+=matchingText(group.getChildAt(i),expected);
  }
  return matches;
 }
 @Test public void v1DebugBuildRetainsSeparateDevIdentity() {
  assertEquals("1.0.0-dev",BuildConfig.VERSION_NAME);
  assertEquals(10,BuildConfig.VERSION_CODE);
  assertEquals("com.lamggm.rospocket.dev",BuildConfig.APPLICATION_ID);
  assertTrue("Preparation stays debug; no release signer or package migration",BuildConfig.DEBUG);
 }
 @Test public void launcherHasRealAdaptiveIconAndDevLabel() {
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).create().get();
  assertEquals("ROS Pocket DEV",activity.getApplicationInfo().loadLabel(activity.getPackageManager()).toString());
  assertNotEquals("Launcher icon must not fall back to the generic Android icon",0,activity.getApplicationInfo().icon);
  assertTrue(activity.getApplicationInfo().loadIcon(activity.getPackageManager()) instanceof android.graphics.drawable.AdaptiveIconDrawable);
 }
 @Test public void subtitleUsesActualBuildVersionAndPreservesReadOnlyIdentity() {
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).create().get();
  assertEquals("One real header must describe the actual build, not a historical label",1,
   matchingText(activity.getWindow().getDecorView(),"v"+BuildConfig.VERSION_NAME+" · DDS direto · SOMENTE LEITURA"));
 }
}
