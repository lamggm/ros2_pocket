package com.lamggm.rospocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import android.app.Activity;
import android.os.Looper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @LooperMode(LooperMode.Mode.PAUSED)
public class RobotAssetRestoreQueueTest {
 @Test public void cancelledPickerResumesRestoreInterruptedByQueuePressure()throws Exception{
  Activity a=Robolectric.buildActivity(Activity.class).create().get();RobotDescription model=RobotDescription.parse(RobotDescriptionTest.SMALL);
  AtomicReference<RobotAssetBundle> delivered=new AtomicReference<>();java.util.concurrent.atomic.AtomicInteger launches=new java.util.concurrent.atomic.AtomicInteger();
  RobotAssetsPanel panel=new RobotAssetsPanel(a,()->model,delivered::set,task->{if(launches.getAndIncrement()==0)throw new RejectedExecutionException();task.run();});
  try{new RobotAssetStore(a.getFilesDir()).save(RobotAssetBundleTest.archive("meshes/base.glb",null,"robo_real"),model);panel.setCanImport(true);panel.selected(model);
   panel.findViewWithTag("robot-import-assets").performClick();assertNotNull(Shadows.shadowOf(a).getNextStartedActivityForResult());
   Shadows.shadowOf(Looper.getMainLooper()).idleFor(300,TimeUnit.MILLISECONDS);assertNull(delivered.get());
   assertTrue(panel.result(RobotAssetsPanel.IMPORT,Activity.RESULT_CANCELED,null));Shadows.shadowOf(Looper.getMainLooper()).idleFor(300,TimeUnit.MILLISECONDS);
   assertNotNull("Cancel must resume the outstanding local restore",delivered.get());assertTrue(delivered.get().matches(model));assertEquals(2,launches.get());
  }finally{panel.dispose();}
 }
 @Test public void retriesCurrentDescriptionAfterQueuePressureWithoutWaitingForAnotherDescription()throws Exception{
  Activity a=Robolectric.buildActivity(Activity.class).create().get();
  RobotDescription model=RobotDescription.parse(RobotDescriptionTest.SMALL);
  AtomicReference<RobotDescription> selected=new AtomicReference<>(model);AtomicReference<RobotAssetBundle> delivered=new AtomicReference<>();
  java.util.concurrent.atomic.AtomicInteger launches=new java.util.concurrent.atomic.AtomicInteger();
  RobotAssetsPanel panel=new RobotAssetsPanel(a,selected::get,delivered::set,task->{if(launches.getAndIncrement()==0)throw new RejectedExecutionException();task.run();});
  try{new RobotAssetStore(a.getFilesDir()).save(RobotAssetBundleTest.archive("meshes/base.glb",null,"robo_real"),model);panel.selected(model);assertNull(delivered.get());
   Shadows.shadowOf(Looper.getMainLooper()).idleFor(300,TimeUnit.MILLISECONDS);assertNotNull(delivered.get());assertTrue(delivered.get().matches(model));assertEquals(2,launches.get());
  }finally{panel.dispose();}
 }
}
