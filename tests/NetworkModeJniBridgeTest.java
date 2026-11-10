package com.lamggm.rospocket;
import java.nio.charset.StandardCharsets;
/** Actual compiled NativeBridge/JNI; SDK classes are compile-time signatures only. */
public class NetworkModeJniBridgeTest {
 static void emit(byte[] data){System.out.println(new String(data,StandardCharsets.UTF_8));}
 public static void main(String[] args)throws Exception{
  for(String mode:new String[]{"invalid","vpn","lan"}){emit(NativeBridge.startWithNetworkMode(84,"127.0.0.1","","jazzy",mode));NativeBridge.stop();}
  emit(NativeBridge.startWithNetworkMode(84,"127.0.0.1","127.0.0.1","humble","vpn"));NativeBridge.stop();
  java.lang.reflect.Method sensor=NativeBridge.class.getDeclaredMethod("startSensorWithNetworkMode",int.class,String.class,String.class,String.class,String.class,String.class);
  for(String mode:new String[]{"invalid","vpn","lan"}){emit((byte[])sensor.invoke(null,84,"127.0.0.1","","rt/test/scan","sensor_msgs::msg::dds_::LaserScan_",mode));NativeBridge.stopSensor();}
  emit((byte[])sensor.invoke(null,84,"127.0.0.1","127.0.0.1","rt/test/scan","sensor_msgs::msg::dds_::LaserScan_","vpn"));NativeBridge.stopSensor();
  java.lang.reflect.Method configured=NativeBridge.class.getDeclaredMethod("startSensorConfigured",int.class,String.class,String.class,String.class,String.class,String.class,String.class);
  for(String format:new String[]{"invalid",null,"humble","jazzy"}){emit((byte[])configured.invoke(null,84,"127.0.0.1","127.0.0.1","rt/test/scan","sensor_msgs::msg::dds_::LaserScan_","vpn",format));NativeBridge.stopSensor();}
 }
}
