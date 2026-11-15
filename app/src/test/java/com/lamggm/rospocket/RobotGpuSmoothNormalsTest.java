package com.lamggm.rospocket;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotGpuSmoothNormalsTest {
 static RobotDescription.Visual visual(){return RobotDescription.parse("<robot name='n'><link name='b'><visual><origin xyz='3 4 5' rpy='0 0 1.5707963267948966'/><geometry><mesh filename='package://r/a.glb' scale='2 1 1'/></geometry></visual></link></robot>").visuals.get(0);}
 @Test public void transformsSmoothNormalsThroughMeshScaleAndVisualRotationWithoutExpandingThePayload(){RobotGpuMesh.Builder b=new RobotGpuMesh.Builder(1);b.add(visual(),new double[]{0,0,0},new double[]{2,0,0},new double[]{0,1,0},0xfff05014,new double[]{1,1,0,1,1,0,1,1,0});RobotGpuMesh.Data d=b.finish();assertEquals(30,d.vertices.remaining());for(int i=0;i<3;i++){assertEquals(-2/Math.sqrt(5),d.vertices.get(i*10+3),1e-6);assertEquals(1/Math.sqrt(5),d.vertices.get(i*10+4),1e-6);assertEquals(0,d.vertices.get(i*10+5),1e-6);}assertEquals(240/255f,d.vertices.get(6),1e-6);}
 @Test public void malformedSmoothNormalsCannotLeaveAnApparentlyCompletePartialMesh(){RobotGpuMesh.Builder b=new RobotGpuMesh.Builder(2);double[] a={0,0,0},c={1,0,0},d={0,1,0};b.add(visual(),a,c,d,0xffffffff);assertThrows(IllegalArgumentException.class,()->b.add(visual(),a,c,d,0xffffffff,new double[]{Float.NaN,0,1,0,0,1,0,0,1}));assertThrows(IllegalArgumentException.class,()->b.finish());}
}
