package com.lamggm.rospocket;
import org.junit.*;import java.util.*;import static org.junit.Assert.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotTfRenderTest {
 @Test public void drawsLinkTfTimesLocalVisualNotBakedUrdfAndHidesMissingFrames()throws Exception{RobotDescription d=RobotDescription.parse(RobotDescriptionTest.SMALL);RobotDescription.Visual v=d.visuals.get(1);RobotCad.Mesh mesh=RobotCad.decode(RobotGlbNormalsTest.fixture(""));double[] local=RobotCad.scaled(v.localTransform,v.size);local[3]=1;RobotCad.Part part=new RobotCad.Part(v,mesh,v.transform,local);double[] observed=RobotDescription.identity();observed[3]=2;Map<String,double[]> poses=Collections.singletonMap(v.link,observed);assertEquals(3,RobotGpuRenderer.transform(part,poses)[3],0);assertSame(part.model,RobotGpuRenderer.transform(part,null));assertNull(RobotGpuRenderer.transform(part,Collections.emptyMap()));}
}
