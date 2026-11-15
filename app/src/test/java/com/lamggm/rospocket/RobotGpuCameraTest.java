package com.lamggm.rospocket;
import android.opengl.Matrix;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotGpuCameraTest {
 static RobotGpuMesh.Data data(){RobotGpuMesh.Builder b=new RobotGpuMesh.Builder(1);b.add(RobotGpuMeshTest.visual(),new double[]{0,0,0},new double[]{1,0,0},new double[]{0,1,0},0xffffffff);return b.finish();}
 @Test public void positiveElevationLooksDownOnUrdfZAndFitsTheActualGeometry(){RobotGpuMesh.Data d=data();float[] m=RobotGpuRenderer.cameraMatrix(d,1200,400,.65,.4,1);for(float v:m)assertTrue(Float.isFinite(v));float[] low=new float[4],high=new float[4];Matrix.multiplyMV(low,0,m,0,new float[]{3.5f,4.5f,5,1},0);Matrix.multiplyMV(high,0,m,0,new float[]{3.5f,4.5f,5.2f,1},0);assertTrue("Positive URDF Z must be closer from above",high[2]<low[2]);for(int i=0;i<3;i++){float[] clip=new float[4];Matrix.multiplyMV(clip,0,m,0,new float[]{d.vertices.get(i*10),d.vertices.get(i*10+1),d.vertices.get(i*10+2),1},0);assertTrue(Math.abs(clip[0])<1&&Math.abs(clip[1])<1);}}
 @Test public void positiveYawPreservesTheEstablishedDragDirection(){RobotGpuMesh.Data d=data();float[] m=RobotGpuRenderer.cameraMatrix(d,700,400,.65,.4,1),clip=new float[4];Matrix.multiplyMV(clip,0,m,0,new float[]{3.5f,5f,5,1},0);assertTrue("Positive world Y must project left for positive local yaw",clip[0]<0);}
 @Test public void pinchChangesProjectionWithoutChangingModelVertices(){RobotGpuMesh.Data d=data();float[] a=RobotGpuRenderer.cameraMatrix(d,700,400,.65,.4,1),b=RobotGpuRenderer.cameraMatrix(d,700,400,.65,.4,2);assertNotEquals(a[0],b[0],1e-6);assertEquals(3,d.vertices.get(0),1e-6);}
}
