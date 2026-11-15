package com.lamggm.rospocket;
import android.graphics.Color;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotGpuMeshTest {
 static RobotDescription.Visual visual(){return RobotDescription.parse("<robot name='gpu'><link name='b'><visual><origin xyz='3 4 5'/><geometry><box size='1 1 1'/></geometry></visual></link></robot>").visuals.get(0);}
 @Test public void batchesRealWorldPositionsNormalsAndUntintedMaterialsInOneBoundedBuffer(){RobotGpuMesh.Builder b=new RobotGpuMesh.Builder(120000);b.add(visual(),new double[]{0,0,0},new double[]{1,0,0},new double[]{0,1,0},Color.rgb(240,80,20));RobotGpuMesh.Data d=b.finish();assertEquals(1,d.triangleCount);assertEquals(30,d.vertices.remaining());assertEquals(3,d.vertices.get(0),1e-6);assertEquals(4,d.vertices.get(1),1e-6);assertEquals(5,d.vertices.get(2),1e-6);assertEquals(1,d.vertices.get(5),1e-6);assertEquals(240/255f,d.vertices.get(6),1e-6);assertEquals(80/255f,d.vertices.get(7),1e-6);assertEquals(20/255f,d.vertices.get(8),1e-6);assertEquals(3.5,d.cx,1e-6);assertEquals(4.5,d.cy,1e-6);assertEquals(5,d.cz,1e-6);assertTrue(d.radius>0);}
 @Test public void exceedingTheAssembledBudgetInvalidatesThePartialMesh(){RobotGpuMesh.Builder b=new RobotGpuMesh.Builder(1);double[] a={0,0,0},c={0,1,0},d={1,0,0};b.add(visual(),a,c,d,Color.RED);assertThrows(IllegalArgumentException.class,()->b.add(visual(),a,c,d,Color.RED));assertThrows(IllegalArgumentException.class,()->b.finish());}
}
