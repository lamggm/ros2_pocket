package com.lamggm.rospocket;

import android.graphics.Bitmap;
import org.json.*;
/** Immutable bounded map. Row zero is ROS row zero; the viewport applies the Y flip. */
final class MapGrid {
 static final String TYPE="nav_msgs::msg::dds_::OccupancyGrid_";
 static final int MAX_CELLS=1048576,MAX_SIDE=2048;
 final int width,height;final double resolution,x,y,yaw;final String frame;final Bitmap bitmap;
 private static int integer(JSONObject j,String name)throws JSONException{Object v=j.get(name);if(!(v instanceof Integer||v instanceof Long)||((Number)v).longValue()>Integer.MAX_VALUE||((Number)v).longValue()<Integer.MIN_VALUE)throw new IllegalArgumentException("Inteiro de mapa inválido");return ((Number)v).intValue();}
 private static double number(JSONObject j,String name,double limit)throws JSONException{Object v=j.get(name);if(!(v instanceof Number))throw new IllegalArgumentException("Número de mapa inválido");double n=((Number)v).doubleValue();if(!Double.isFinite(n)||Math.abs(n)>limit)throw new IllegalArgumentException("Número de mapa fora dos limites");return n;}
 MapGrid(JSONObject m,byte[] data)throws JSONException{
  width=integer(m,"width");height=integer(m,"height");if(width<=0||height<=0||width>MAX_SIDE||height>MAX_SIDE||(long)width*height>MAX_CELLS||data.length!=(long)width*height)throw new IllegalArgumentException("Mapa excede 1 Mi células / 2048 por lado ou buffer inválido");
  resolution=number(m,"resolution",1000);if(resolution<(double).000001f)throw new IllegalArgumentException("Resolução de mapa inválida");x=number(m,"originX",1e6);y=number(m,"originY",1e6);yaw=number(m,"originYaw",Math.PI);
  Object f=m.get("frameId");if(!(f instanceof String))throw new IllegalArgumentException("Frame de mapa inválido");frame=(String)f;if(frame.isEmpty()||frame.length()>256||frame.startsWith("/")||frame.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IllegalArgumentException("Frame de mapa inválido");
  int sec=integer(m,"stampSec"),nano=integer(m,"stampNanosec");if(sec<0||nano<0||nano>=1000000000)throw new IllegalArgumentException("Timestamp de mapa inválido");
  int[] pixels=new int[data.length];for(int i=0;i<data.length;i++){int p=data[i];if(p< -1||p>100)throw new IllegalArgumentException("Ocupação fora de -1/0–100");pixels[i]=p==-1?0xff63717d:0xff000000|((237+(23-237)*p/100)<<16)|((244+(38-244)*p/100)<<8)|(245+(46-245)*p/100);}
  bitmap=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);
 }
 /** Pixel corner -> declared map frame (no robot pose or inferred calibration). */
 double[] world(double u,double v){double c=Math.cos(yaw),s=Math.sin(yaw);return new double[]{x+resolution*(c*u-s*v),y+resolution*(s*u+c*v)};}
 double[] pixel(double wx,double wy){double dx=wx-x,dy=wy-y,c=Math.cos(yaw),s=Math.sin(yaw);return new double[]{(c*dx+s*dy)/resolution,(-s*dx+c*dy)/resolution};}
}
