package com.lamggm.rospocket;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotGlbTest {
 static byte[] glb(String json,byte[] binary){byte[] j=json.getBytes(StandardCharsets.UTF_8);int js=(j.length+3)&~3,bs=(binary.length+3)&~3;ByteBuffer b=ByteBuffer.allocate(12+8+js+8+bs).order(ByteOrder.LITTLE_ENDIAN);b.putInt(0x46546c67).putInt(2).putInt(b.capacity());b.putInt(js).putInt(0x4e4f534a).put(j);while(b.position()<20+js)b.put((byte)32);b.putInt(bs).putInt(0x004e4942).put(binary);return b.array();}
 static String json(){return "{\"asset\":{\"version\":\"2.0\"},\"buffers\":[{\"byteLength\":42}],\"bufferViews\":[{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36},{\"buffer\":0,\"byteOffset\":36,\"byteLength\":6}],\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},{\"bufferView\":1,\"componentType\":5123,\"count\":3,\"type\":\"SCALAR\"}],\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorFactor\":[1,0,0,1]}}],\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0},\"indices\":1,\"material\":0}]}],\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}],\"scene\":0}";}
 static byte[] binary(){ByteBuffer b=ByteBuffer.allocate(42).order(ByteOrder.LITTLE_ENDIAN);for(float v:new float[]{0,0,0,1,0,0,0,1,0})b.putFloat(v);b.putShort((short)0).putShort((short)1).putShort((short)2);return b.array();}
 @Test public void decodesRealGlbTrianglesColorsAndConvertsYUpToUrdfZUp(){List<RobotGlb.Triangle> t=RobotGlb.decode(glb(json(),binary()));assertEquals(1,t.size());assertArrayEquals(new double[]{0,0,0,1,0,0,0,0,1},t.get(0).xyz,1e-8);assertEquals(0xffff0000,t.get(0).color);}
 @Test public void appliesSceneTransformsBeforeChangingCoordinateFrame()throws Exception{JSONObject j=new JSONObject(json());j.getJSONArray("nodes").getJSONObject(0).put("translation",new JSONArray().put(0).put(2).put(3));assertArrayEquals(new double[]{0,-3,2,1,-3,2,0,-3,3},RobotGlb.decode(glb(j.toString(),binary())).get(0).xyz,1e-8);}
 @Test public void rejectsExternalBuffersInvalidIndicesNonfiniteAndNodeCycles()throws Exception{byte[] invalid=binary().clone();ByteBuffer.wrap(invalid).order(ByteOrder.LITTLE_ENDIAN).putShort(40,(short)9);assertThrows(IllegalArgumentException.class,()->RobotGlb.decode(glb(json(),invalid)));byte[] nan=binary().clone();ByteBuffer.wrap(nan).order(ByteOrder.LITTLE_ENDIAN).putFloat(0,Float.NaN);assertThrows(IllegalArgumentException.class,()->RobotGlb.decode(glb(json(),nan)));JSONObject j=new JSONObject(json());j.getJSONArray("buffers").getJSONObject(0).put("uri","http://never-fetch.invalid/a.bin");assertThrows(IllegalArgumentException.class,()->RobotGlb.decode(glb(j.toString(),binary())));JSONObject cycle=new JSONObject(json());cycle.getJSONArray("nodes").getJSONObject(0).put("children",new JSONArray().put(0));assertThrows(IllegalArgumentException.class,()->RobotGlb.decode(glb(cycle.toString(),binary())));}
}
