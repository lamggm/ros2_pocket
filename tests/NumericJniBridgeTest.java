package com.lamggm.rospocket;
import java.nio.charset.StandardCharsets;
/** Linux JVM invokes the real production JNI; JSON is parsed by the Python oracle. */
public class NumericJniBridgeTest {
 private static void print(byte[] bytes){System.out.println(new String(bytes,StandardCharsets.UTF_8));}
 public static void main(String[] args){
  print(NativeBridge.selectNumeric(1,new int[]{0},new String[]{"data"}));
  print(NativeBridge.poll());
  print(NativeBridge.selectNumeric(2,null,new String[0]));
  print(NativeBridge.selectNumeric(2,new int[0],null));
  print(NativeBridge.selectNumeric(2,new int[]{0,1,2,3,0},new String[]{"data","data","data","data","x"}));
  print(NativeBridge.selectNumeric(2,new int[]{0},new String[0]));
  print(NativeBridge.selectNumeric(2,new int[]{0},new String[]{new String(new char[257]).replace('\0','x')}));
  print(NativeBridge.selectNumeric(2,new int[]{0},new String[]{null}));
  print(NativeBridge.selectNumeric(-1,new int[0],new String[0]));
  print(NativeBridge.poll());
  print(NativeBridge.selectNumeric(2,new int[0],new String[0]));
  print(NativeBridge.poll());
  NativeBridge.stop();
 }
}
