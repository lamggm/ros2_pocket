package com.lamggm.rospocket;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small, deterministic native line icons; no emoji/font/network dependency. */
final class PocketIcons extends Drawable {
 private final String name;private final int size;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 PocketIcons(String name,int color,int size){this.name=name;this.size=size;p.setColor(color);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
 @Override public void draw(Canvas c){int save=c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);p.setStyle(Paint.Style.STROKE);
  switch(name){
   case "panel":for(int y=4;y<=14;y+=10)for(int x=4;x<=14;x+=10)c.drawRoundRect(x,y,x+6,y+6,1.5f,1.5f,p);break;
   case "explore":c.drawCircle(10,10,6,p);c.drawLine(14.5f,14.5f,20,20,p);break;
   case "graph":c.drawLine(6,6,18,12,p);c.drawLine(6,18,18,12,p);c.drawCircle(5,5,2.6f,p);c.drawCircle(5,19,2.6f,p);c.drawCircle(19,12,2.6f,p);break;
   case "logs":for(int y=5;y<=19;y+=7){c.drawLine(4,y,5,y,p);c.drawLine(9,y,20,y,p);}break;
   case "connection":c.drawRoundRect(3,6,11,15,3,3,p);c.drawRoundRect(13,9,21,18,3,3,p);c.drawLine(8,12,16,12,p);break;
   case "more":p.setStyle(Paint.Style.FILL);for(int y=5;y<=19;y+=7)c.drawCircle(12,y,1.6f,p);break;
   case "add":c.drawLine(12,5,12,19,p);c.drawLine(5,12,19,12,p);break;
   case "expand":c.drawLine(4,9,4,4,p);c.drawLine(4,4,9,4,p);c.drawLine(15,4,20,4,p);c.drawLine(20,4,20,9,p);c.drawLine(4,15,4,20,p);c.drawLine(4,20,9,20,p);c.drawLine(15,20,20,20,p);c.drawLine(20,20,20,15,p);break;
   case "pause":c.drawLine(8,5,8,19,p);c.drawLine(16,5,16,19,p);break;
   case "play":Path triangle=new Path();triangle.moveTo(7,4);triangle.lineTo(20,12);triangle.lineTo(7,20);triangle.close();c.drawPath(triangle,p);break;
   case "chevron":c.drawLine(9,5,16,12,p);c.drawLine(16,12,9,19,p);break;
   case "star":Path star=new Path();for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?9:4;float x=12+(float)Math.cos(a)*r,y=12+(float)Math.sin(a)*r;if(i==0)star.moveTo(x,y);else star.lineTo(x,y);}star.close();c.drawPath(star,p);break;
   default:c.drawCircle(12,12,8,p);c.drawLine(12,12,18,6,p);c.drawCircle(12,12,3,p);
  }c.restoreToCount(save);
 }
 @Override public void setAlpha(int a){p.setAlpha(a);invalidateSelf();}
 @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 @Override public int getIntrinsicWidth(){return size;}
 @Override public int getIntrinsicHeight(){return size;}
}
