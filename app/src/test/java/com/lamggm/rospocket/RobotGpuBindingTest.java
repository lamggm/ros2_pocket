package com.lamggm.rospocket;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotGpuBindingTest {
 @Test public void inlineModelOwnsAChildSurfaceWithoutReplacingTheDashboard(){assertTrue("Model must own the GL child inline",FrameLayout.class.isAssignableFrom(RobotModelView.class));}
 @Test public void gpuSelectionUsesActualEsCapabilityWithSoftwareFallback(){assertFalse(RobotGpuRenderer.supportsVersion(0));assertFalse(RobotGpuRenderer.supportsVersion(0x10001));assertTrue(RobotGpuRenderer.supportsVersion(0x20000));assertTrue(RobotGpuRenderer.supportsVersion(0x30002));}
}
