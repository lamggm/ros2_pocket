package com.lamggm.rospocket;

import android.widget.*;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Actual Views/configuration; no JNI, DDS or Android-device claim. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class GraphFormatActivityTest {
 static Spinner selector(MainActivity a){
  int id=a.getResources().getIdentifier("graph_format","id",a.getPackageName());
  assertNotEquals("Graph format selector must have a stable resource ID",0,id);
  Spinner s=a.findViewById(id);assertNotNull("Manual graph format selector exists",s);return s;
 }
 @Test public void humbleSelectionIsSavedAppliedAndRestoredWithoutConnecting() throws Exception {
  var first=Robolectric.buildActivity(MainActivity.class).create();MainActivity a=first.get();
  try{
   Spinner s=selector(a);assertEquals(0,s.getSelectedItemPosition());s.setSelection(1);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
   ((EditText)a.findViewById(R.id.profile_name)).setText("Humble bench");a.findViewById(R.id.profile_save).performClick();
   File file=new File(a.getFilesDir(),"bench-profiles-v1.json");BenchProfiles saved=new ProfileStore(file).load();
   assertEquals(1,saved.profiles.size());assertEquals("humble",saved.profiles.get(0).graphFormat);assertNull(saved.applied);
   a.findViewById(R.id.profile_apply).performClick();assertEquals("humble",new ProfileStore(file).load().applied.graphFormat);
   var second=Robolectric.buildActivity(MainActivity.class).create();
   try{assertEquals(1,selector(second.get()).getSelectedItemPosition());assertEquals("Conectar",((Button)second.get().findViewById(R.id.connect)).getText().toString());}
   finally{second.destroy();}
  }finally{first.destroy();}
 }
}
