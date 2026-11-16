package com.lamggm.rospocket;
import java.nio.*;import java.nio.charset.StandardCharsets;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class MapPacketTest {
 static byte[] packet(JSONObject meta,byte[] data){byte[] json=meta.toString().getBytes(StandardCharsets.UTF_8);return ByteBuffer.allocate(4+json.length+data.length).order(ByteOrder.LITTLE_ENDIAN).putInt(json.length).put(json).put(data).array();}
 static JSONObject meta()throws Exception{return new JSONObject().put("kind","map").put("running",true).put("frameId","map").put("stampSec",8).put("stampNanosec",2).put("width",2).put("height",2).put("resolution",.1).put("originX",1).put("originY",2).put("originYaw",.5).put("ageMs",0);}
 @Test public void occupancyGridDecodesUnknownFreeAndOccupiedCellsWithoutInventingMeasurements()throws Exception{
  SensorPacket.Decoded decoded;try{decoded=new SensorPacket(packet(meta(),new byte[]{-1,0,50,100})).decode();}catch(IllegalArgumentException unsupported){throw new AssertionError("OccupancyGrid must decode as a bounded visual map",unsupported);}
  assertNotNull(decoded.image);assertEquals(2,decoded.image.getWidth());assertEquals(2,decoded.image.getHeight());assertNotEquals(decoded.image.getPixel(0,0),decoded.image.getPixel(1,0));assertNotEquals(decoded.image.getPixel(1,0),decoded.image.getPixel(1,1));assertEquals(0xffedf4f5,decoded.image.getPixel(1,0));assertEquals(0xff17262e,decoded.image.getPixel(1,1));
 }
 @Test public void dimensionsResolutionOccupancyAndFrameAdmissionStayBoundedAndStrict()throws Exception{
  for(JSONObject m:new JSONObject[]{meta().put("width",0),meta().put("width",2049),meta().put("height",-1),meta().put("width",2.5),meta().put("width","2"),meta().put("resolution",0),meta().put("resolution",".1"),meta().put("frameId","/map"),meta().put("frameId","map robot"),meta().put("originX",1e9),meta().put("stampNanosec",1000000000)}){try{new SensorPacket(packet(m,new byte[]{-1,0,50,100})).decode();fail("Invalid map metadata accepted: "+m);}catch(IllegalArgumentException expected){}}
  for(byte[] cells:new byte[][]{new byte[]{0},new byte[]{-2,0,0,0},new byte[]{101,0,0,0}})try{new SensorPacket(packet(meta(),cells)).decode();fail("Invalid occupancy buffer accepted");}catch(IllegalArgumentException expected){}
 }
 @Test public void rotatedMapOriginAndInversePreserveMetricCoordinates()throws Exception{
  MapGrid map=new MapGrid(meta().put("originYaw",Math.PI/2),new byte[]{0,0,0,0});double[] world=map.world(2,1);assertEquals(map.x-map.resolution,world[0],1e-9);assertEquals(map.y+2*map.resolution,world[1],1e-9);assertArrayEquals(new double[]{2,1},map.pixel(world[0],world[1]),1e-9);
 }
}
