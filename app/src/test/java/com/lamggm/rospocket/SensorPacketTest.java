package com.lamggm.rospocket;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;
import org.json.JSONObject;import java.nio.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SensorPacketTest {
 static byte[] packet(JSONObject m,byte[] data){byte[] j=m.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);return ByteBuffer.allocate(4+j.length+data.length).order(ByteOrder.LITTLE_ENDIAN).putInt(j.length).put(j).put(data).array();}
 @Test public void rgbRowPaddingPreservesRealColors()throws Exception{
  JSONObject m=new JSONObject().put("kind","image").put("width",2).put("height",1).put("step",8).put("encoding","rgb8").put("ageMs",0);
  SensorPacket.Decoded d=new SensorPacket(packet(m,new byte[]{(byte)255,0,0,0,(byte)255,0,99,99})).decode();
  assertNotNull("Image topic must produce a bitmap",d.image);assertEquals(0xffff0000,d.image.getPixel(0,0));assertEquals(0xff00ff00,d.image.getPixel(1,0));
 }
 @Test public void scanCoordinatesRemainTypedAndInvalidBuffersFail()throws Exception{
  JSONObject m=new JSONObject().put("kind","scan").put("points",1).put("rangeMax",10).put("ageMs",0);
  byte[] xy=ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(2).putFloat(3).array();
  assertArrayEquals(new float[]{2,3},new SensorPacket(packet(m,xy)).decode().xy,0);
  try{new SensorPacket(packet(m,new byte[4])).decode();fail("Truncated point buffer accepted");}catch(IllegalArgumentException expected){}
 }
}
