package com.lamggm.rospocket;
import android.opengl.Matrix;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotPortraitViewportTest {
 @Test public void actualVerticesFitTallAndWideTilesAtResetZoom(){RobotGpuMesh.Data data=RobotGpuCameraTest.data();for(int[] size:new int[][]{{160,800},{400,400},{1200,200}}){float[] matrix=RobotGpuRenderer.cameraMatrix(data,size[0],size[1],.65,.4,1);for(int i=0;i<3;i++){float[] p={data.vertices.get(i*10),data.vertices.get(i*10+1),data.vertices.get(i*10+2),1},clip=new float[4];Matrix.multiplyMV(clip,0,matrix,0,p,0);assertTrue("Tall resized card must not crop geometry at reset zoom",Math.abs(clip[0])<1&&Math.abs(clip[1])<1);}}}
}
