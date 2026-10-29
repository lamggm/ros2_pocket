package com.lamggm.rospocket;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
final class SensorPacket {
 static final int MAX_PIXELS=921600,MAX_PACKET=4*1024*1024;
 final JSONObject meta;final byte[] data;final long capturedNs=System.nanoTime();
 SensorPacket(byte[] packet)throws Exception{
  if(packet==null||packet.length<6||packet.length>MAX_PACKET)throw new IllegalArgumentException("Pacote visual inválido");
  int n=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt();
  if(n<2||n>4096||n>packet.length-4)throw new IllegalArgumentException("Metadados visuais inválidos");
  meta=new JSONObject(new String(packet,4,n,StandardCharsets.UTF_8));data=Arrays.copyOfRange(packet,4+n,packet.length);
 }
 static boolean supports(String type){return type.equals("sensor_msgs::msg::dds_::LaserScan_")||type.equals("sensor_msgs::msg::dds_::Image_")||type.equals("sensor_msgs::msg::dds_::CompressedImage_");}
 static final class Decoded {
  Bitmap image;float[] xy;JSONObject meta;long capturedNs;long initialAgeMs;
  boolean fresh(long now){return initialAgeMs>=0&&now>=capturedNs&&initialAgeMs+(now-capturedNs)/1000000<3000;}
 }
 private static void dimensions(int w,int h){if(w<=0||h<=0||w>1280||h>1280||(long)w*h>MAX_PIXELS)throw new IllegalArgumentException("Imagem excede 921600 pixels/1280 por lado");}
 Decoded decode()throws Exception{
  Decoded d=new Decoded();d.meta=meta;d.capturedNs=capturedNs;d.initialAgeMs=meta.optLong("ageMs",-1);
  switch(meta.optString("kind")){
   case "scan":{
    int n=meta.getInt("points");double range=meta.getDouble("rangeMax");
    if(n<0||n>4096||data.length!=n*8||!Double.isFinite(range)||range<=0)throw new IllegalArgumentException("Pontos LaserScan inválidos");
    d.xy=new float[n*2];ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    for(int i=0;i<d.xy.length;i++){float value=b.getFloat();if(!Float.isFinite(value))throw new IllegalArgumentException("Coordenada não finita");d.xy[i]=value;}
    break;
   }
   case "image":{
    int w=meta.getInt("width"),h=meta.getInt("height"),step=meta.getInt("step");dimensions(w,h);
    String e=meta.getString("encoding");int channels=e.equals("mono8")?1:(e.equals("rgb8")||e.equals("bgr8"))?3:(e.equals("rgba8")||e.equals("bgra8"))?4:0;
    if(channels==0||step<(long)w*channels||(long)step*h!=data.length)throw new IllegalArgumentException("Encoding/step de imagem inválido");
    int[] colors=new int[w*h];boolean bgr=e.startsWith("bgr");
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
     int p=y*step+x*channels,r=data[p]&255,g=channels==1?r:data[p+1]&255,b=channels==1?r:data[p+2]&255;
     if(bgr){int temp=r;r=b;b=temp;}
     int alpha=channels==4?data[p+3]&255:255;colors[y*w+x]=(alpha<<24)|(r<<16)|(g<<8)|b;
    }
    d.image=Bitmap.createBitmap(colors,w,h,Bitmap.Config.ARGB_8888);break;
   }
   case "compressed":{
    boolean jpeg=data.length>=4&&(data[0]&255)==255&&(data[1]&255)==216;
    boolean png=data.length>=8&&data[0]==(byte)137&&data[1]==80&&data[2]==78&&data[3]==71&&data[4]==13&&data[5]==10&&data[6]==26&&data[7]==10;
    String format=meta.getString("format");
    if(data.length>1024*1024||format.contains("compressedDepth")||(!jpeg&&!png)||!(jpeg&&(format.contains("jpeg")||format.contains("jpg"))||png&&format.contains("png")))throw new IllegalArgumentException("JPEG/PNG inválido");
    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);dimensions(bounds.outWidth,bounds.outHeight);
    BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;
    d.image=BitmapFactory.decodeByteArray(data,0,data.length,options);if(d.image==null)throw new IllegalArgumentException("Falha ao decodificar JPEG/PNG");break;
   }
   default:throw new IllegalArgumentException("Payload visual desconhecido");
  }
  return d;
 }
}
