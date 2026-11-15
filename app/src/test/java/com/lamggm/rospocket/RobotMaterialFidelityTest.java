package com.lamggm.rospocket;
import android.graphics.*;
import java.nio.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RobotMaterialFidelityTest {
 @Test public void vertexColorsWithoutMaterialUseWhiteGltfBaseInsteadOfGreyTint()throws Exception{JSONObject j=new JSONObject(RobotGlbTest.json());j.remove("materials");JSONObject primitive=j.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(0);primitive.remove("material");primitive.getJSONObject("attributes").put("COLOR_0",2);j.getJSONArray("buffers").getJSONObject(0).put("byteLength",56);j.getJSONArray("bufferViews").put(new JSONObject().put("buffer",0).put("byteOffset",44).put("byteLength",12));j.getJSONArray("accessors").put(new JSONObject().put("bufferView",2).put("componentType",5121).put("normalized",true).put("count",3).put("type","VEC4"));ByteBuffer b=ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN);b.put(RobotGlbTest.binary());b.position(44);for(int i=0;i<3;i++)b.put(new byte[]{(byte)240,80,20,(byte)255});assertEquals(Color.rgb(240,80,20),RobotGlb.decode(RobotGlbTest.glb(j.toString(),b.array())).get(0).color);}
 @Test public void embeddedMeshMaterialIsNotFlattenedByGenericUrdfMeshColor()throws Exception{String xml="<robot name='robo_real'><link name='base'><visual><geometry><mesh filename='package://robot/meshes/base.dae'/></geometry><material name='genericGrey'><color rgba='.2 .2 .2 1'/></material></visual></link></robot>";RobotAssetBundle assets=RobotAssetBundle.decode(RobotAssetBundleTest.archiveForDescription("meshes/base.glb",null,"robo_real",xml));RobotModelView view=new RobotModelView(RuntimeEnvironment.getApplication());view.layout(0,0,700,400);view.setModel(RobotDescription.parse(xml),assets);Bitmap bitmap=Bitmap.createBitmap(700,400,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));int red=0;for(int y=0;y<400;y++)for(int x=0;x<700;x++){int c=bitmap.getPixel(x,y);if(Color.red(c)>70&&Color.red(c)>Color.green(c)*2&&Color.red(c)>Color.blue(c)*2)red++;}assertTrue("Embedded red material must remain red, not generic URDF grey",red>100);bitmap.recycle();}
}
