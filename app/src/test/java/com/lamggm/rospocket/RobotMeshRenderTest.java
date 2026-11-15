package com.lamggm.rospocket;
import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RobotMeshRenderTest {
 @Test public void intersectingOpaqueSurfacesUsePerPixelDepthNotTriangleCentroids()throws Exception{String xml="<robot name='crossing'><link name='base'><visual><origin rpy='-.7853981633974483 0 0'/><geometry><box size='2 .001 2'/></geometry><material><color rgba='1 0 0 1'/></material></visual><visual><origin xyz='0 .2 0'/><geometry><box size='2 .001 2'/></geometry><material><color rgba='0 0 1 1'/></material></visual></link></robot>";RobotModelView v=new RobotModelView(RuntimeEnvironment.getApplication());v.layout(0,0,400,400);v.setModel(RobotDescription.parse(xml));v.yaw=0;v.pitch=0;Bitmap b=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));for(int x:new int[]{145,255}){int red=b.getPixel(x,189);assertTrue("Nearer tilted red plane must remain visible at x="+x,Color.red(red)>Color.blue(red)*2);}int blue=b.getPixel(200,145);assertTrue("At greater height the horizontal-depth blue plane is nearer",Color.blue(blue)>Color.red(blue)*2);b.recycle();}
 @Test public void rendersBoundImportedMeshAlongsideUrdfPrimitivesAndRejectsOtherRobotBinding()throws Exception{RobotDescription robot=RobotDescription.parse(RobotDescriptionTest.SMALL);RobotAssetBundle assets=RobotAssetBundle.decode(RobotAssetBundleTest.archive("meshes/base.glb",null,"robo_real"));RobotModelView v=new RobotModelView(RuntimeEnvironment.getApplication());v.layout(0,0,700,360);v.setModel(robot,assets);Bitmap b=Bitmap.createBitmap(700,360,Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));assertEquals("12 box +48 cylinder +1 actual imported mesh face",61,v.drawnFaces);assertTrue(v.getContentDescription().toString().contains("Malhas locais"));v.setModel(RobotDescription.parse(RobotDescriptionTest.SMALL.replace("robo_real","other")),assets);v.draw(new Canvas(b));assertEquals(60,v.drawnFaces);assertTrue(v.getContentDescription().toString().contains("1 malha"));b.recycle();}
}
