package com.lamggm.rospocket;
import java.nio.charset.StandardCharsets;
/** Actual production JNI, real loopback Session start; no ROS producer or Android claim. */
public class GraphFormatJniBridgeTest {
 private static void print(byte[] value){System.out.println(new String(value,StandardCharsets.UTF_8));}
 public static void main(String[] args){
  for(String format:new String[]{"humble","jazzy","auto","HUMBLE","",null}){
   print(NativeBridge.startWithGraphFormat(86,"127.0.0.1","",format));
   print(NativeBridge.poll());
  }
  NativeBridge.stop();
 }
}
