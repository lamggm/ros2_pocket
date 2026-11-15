package com.lamggm.rospocket;
import java.nio.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotGlbNormalsTest {
 static byte[] fixture(String modify)throws Exception{JSONObject j=new JSONObject(RobotGlbTest.json());byte[] bin=Arrays.copyOf(RobotGlbTest.binary(),80);ByteBuffer b=ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<3;i++){b.putFloat(44+i*12,(float)(1/Math.sqrt(2)));b.putFloat(48+i*12,(float)(1/Math.sqrt(2)));b.putFloat(52+i*12,0);}j.getJSONArray("buffers").getJSONObject(0).put("byteLength",80);j.getJSONArray("bufferViews").put(new JSONObject().put("buffer",0).put("byteOffset",44).put("byteLength",36));j.getJSONArray("accessors").put(new JSONObject().put("bufferView",2).put("componentType",5126).put("count",3).put("type","VEC3"));j.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives").getJSONObject(0).getJSONObject("attributes").put("NORMAL",2);j.getJSONArray("nodes").getJSONObject(0).put("scale",new JSONArray().put(2).put(1).put(1)).put("translation",new JSONArray().put(3).put(4).put(5));if("count".equals(modify))j.getJSONArray("accessors").getJSONObject(2).put("count",2);if("nan".equals(modify))b.putFloat(44,Float.NaN);return RobotGlbTest.glb(j.toString(),bin);}
 @Test public void importsInverseTransposeNormalsAndConvertsThemToUrdfAxesWithoutTranslation()throws Exception{RobotGlb.Triangle t=RobotGlb.decode(fixture("")).get(0);assertNotNull("NORMAL cannot be silently discarded",t.normals);for(int i=0;i<3;i++)assertArrayEquals(new double[]{1/Math.sqrt(5),0,2/Math.sqrt(5)},Arrays.copyOfRange(t.normals,i*3,i*3+3),1e-6);}
 @Test public void rejectsMalformedNormalPopulationAndNonfiniteChannels()throws Exception{assertThrows(IllegalArgumentException.class,()->RobotGlb.decode(fixture("count")));assertThrows(IllegalArgumentException.class,()->RobotGlb.decode(fixture("nan")));}
 @Test public void legacyAssetsWithoutNormalsRetainFlatFallback(){assertNull(RobotGlb.decode(RobotGlbTest.glb(RobotGlbTest.json(),RobotGlbTest.binary())).get(0).normals);}
}
