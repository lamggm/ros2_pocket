package com.lamggm.rospocket;
import java.nio.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotGlbDenseTest {
 static byte[] dense(int faces,boolean shared,boolean duplicatePrimitive)throws Exception {
  int vertices=shared?3:faces*3,indexCount=faces*3,positionBytes=vertices*12,indexBytes=indexCount*4,colorBytes=vertices*4;ByteBuffer b=ByteBuffer.allocate(positionBytes+indexBytes+colorBytes).order(ByteOrder.LITTLE_ENDIAN);
  for(int i=0;i<vertices;i++){float origin=(i/3)*.0001f;b.putFloat(origin+(i%3==1?.00002f:0)).putFloat(i%3==2?.00002f:0).putFloat(0);}
  for(int i=0;i<indexCount;i++)b.putInt(shared?i%3:i);for(int i=0;i<vertices;i++)b.put((byte)240).put((byte)80).put((byte)20).put((byte)255);
  JSONObject j=new JSONObject(RobotGlbTest.json());j.getJSONArray("buffers").getJSONObject(0).put("byteLength",b.capacity());JSONArray views=j.getJSONArray("bufferViews");views.getJSONObject(0).put("byteLength",positionBytes);views.getJSONObject(1).put("byteOffset",positionBytes).put("byteLength",indexBytes);views.put(new JSONObject().put("buffer",0).put("byteOffset",positionBytes+indexBytes).put("byteLength",colorBytes));JSONArray a=j.getJSONArray("accessors");a.getJSONObject(0).put("count",vertices);a.getJSONObject(1).put("componentType",5125).put("count",indexCount);a.put(new JSONObject().put("bufferView",2).put("componentType",5121).put("count",vertices).put("type","VEC4").put("normalized",true));JSONArray primitives=j.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives");JSONObject p=primitives.getJSONObject(0);p.remove("material");p.getJSONObject("attributes").put("COLOR_0",2);if(duplicatePrimitive)primitives.put(new JSONObject(p.toString()));return RobotGlbTest.glb(j.toString(),b.array());
 }
 @Test public void acceptsFortyThousandFacesWithOneHundredTwentyThousandIndices()throws Exception{java.util.List<RobotGlb.Triangle> t=RobotGlb.decode(dense(40000,true,false));assertEquals(40000,t.size());assertEquals(0xfff05014,t.get(0).color);}
 @Test public void acceptsDisconnectedPositionAndColorAccessorsBeyondOneHundredThousand()throws Exception{assertEquals(36000,RobotGlb.decode(dense(36000,false,false)).size());}
 @Test public void acceptsFullDeclaredFaceBudget()throws Exception{assertEquals(120000,RobotGlb.decode(dense(120000,true,false)).size());}
 @Test public void rejectsAccessorBeyondThreeElementsPerDeclaredFace()throws Exception{assertThrows(IllegalArgumentException.class,()->RobotGlb.decode(dense(120001,true,false)));}
 @Test public void stillRejectsAggregateTriangleBudgetAcrossPrimitives()throws Exception{assertThrows(IllegalArgumentException.class,()->RobotGlb.decode(dense(60001,true,true)));}
}
