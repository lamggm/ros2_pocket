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
 @Test public void preparationBuildUsesReservedV02DevIdentity() {
  assertEquals("0.2.0",BuildConfig.VERSION_NAME);
  assertEquals(2,BuildConfig.VERSION_CODE);
  assertEquals("com.lamggm.rospocket.dev",BuildConfig.APPLICATION_ID);
  assertTrue("Preparation stays debug; no release signer or package migration",BuildConfig.DEBUG);
 }
 @Test public void subtitleUsesActualBuildVersionAndPreservesReadOnlyIdentity() {
  MainActivity activity=Robolectric.buildActivity(MainActivity.class).create().get();
  assertEquals("One real header must describe the actual build, not a historical label",1,
   matchingText(activity.getWindow().getDecorView(),"v"+BuildConfig.VERSION_NAME+" · DDS direto · SOMENTE LEITURA"));
 }
}
