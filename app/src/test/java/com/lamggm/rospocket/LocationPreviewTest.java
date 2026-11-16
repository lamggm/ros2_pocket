package com.lamggm.rospocket;
import android.graphics.*;import android.view.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;import org.json.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import static org.junit.Assert.*;
/** Actual Android Views/Canvas. All room, map and pose data here are synthetic test fixtures only. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE) @LooperMode(LooperMode.Mode.PAUSED)
public class LocationPreviewTest {
 @Test @Config(qualifiers="w1200dp-h900dp") public void tabletDark()throws Exception{render("tablet-dark",1200,900,"dark");}
 @Test @Config(qualifiers="w1200dp-h900dp") public void tabletLight()throws Exception{render("tablet-light",1200,900,"light");}
 @Test @Config(qualifiers="w400dp-h1000dp") public void phoneDark()throws Exception{render("phone-dark",400,1000,"dark");}
 @Test @Config(qualifiers="w400dp-h1000dp") public void phoneLight()throws Exception{render("phone-light",400,1000,"light");}
 void render(String name,int w,int h,String theme)throws Exception{
  ConnectionActivityTest f=new ConnectionActivityTest();f.setup();f.controller.visible();f.paint();try{UiTheme.save(f.a,theme);f.a.applyAppearance();f.a.findViewById(R.id.nav_location).performClick();LocationPanel panel=f.a.getWindow().getDecorView().findViewWithTag("location-page");panel.configure(new LocationConfig("rt/demo/map","base_link","rt/demo/tf","rt/demo/tf_static",true,true));
   byte[] data=new byte[50*36];for(int y=0;y<36;y++)for(int x=0;x<50;x++)data[y*50+x]=(byte)(x<2||x>47||y<2||y>33?-1:x==3||x==46||y==3||y==32||x>=15&&x<=19&&y>=10&&y<=25||x>=29&&x<=39&&y>=12&&y<=15?100:0);
   JSONObject meta=MapPacketTest.meta().put("width",50).put("height",36).put("resolution",.2).put("originX",-5).put("originY",-3).put("originYaw",0);
   LocationPanel.Request request=panel.desired();long epoch=panel.epoch();assertTrue(panel.accept(request,epoch,MapPacketTest.packet(meta,data),()->true));java.lang.reflect.Field decoder=LocationPanel.class.getDeclaredField("decoder");decoder.setAccessible(true);((ExecutorService)decoder.get(panel)).submit(()->{}).get(5,TimeUnit.SECONDS);f.paint();assertNotNull(panel.map());
   long now=System.nanoTime();RobotTf tree=new RobotTf();tree.accept(new JSONArray().put(new JSONObject().put("parent","map").put("child","base_link").put("writer","synthetic-preview").put("stamp",9000000000L).put("static",false).put("translation",new JSONArray().put(1.7).put(-1.8).put(0)).put("rotation",new JSONArray().put(0).put(0).put(.19866933079506122).put(.9800665778412416))),now);RobotTf.Pose pose=tree.solve("map",Collections.singleton("base_link"),now);assertTrue(pose.live);panel.pose(request,epoch,panel.map(),pose);
   View root=f.a.getWindow().getDecorView().findViewWithTag("app-root");root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);
   Bitmap map=Bitmap.createBitmap(panel.surface.getWidth(),panel.surface.getHeight(),Bitmap.Config.ARGB_8888);panel.surface.draw(new Canvas(map));assertTrue("Robot pose must produce real Canvas pixels",count(map,0xff087f67)>10);map.recycle();
   Bitmap image=Bitmap.createBitmap(w,h+32,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);root.draw(canvas);Paint note=new Paint(Paint.ANTI_ALIAS_FLAG);note.setColor(0xff10181c);canvas.drawRect(0,h,w,h+32,note);note.setColor(0xffedf4f5);note.setTextSize(12);canvas.drawText("ANDROID HOST · MAPA/POSE SINTÉTICOS · NÃO É CAPTURA DO ROBÔ",8,h+21,note);
   String dir=System.getenv("POCKET_LOCATION_PREVIEW_DIR");if(dir!=null){File folder=new File(dir);folder.mkdirs();try(OutputStream out=new FileOutputStream(new File(folder,name+"-HOST-SYNTHETIC.png"))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}}image.recycle();
   panel.surface.clearPose();Bitmap empty=Bitmap.createBitmap(panel.surface.getWidth(),panel.surface.getHeight(),Bitmap.Config.ARGB_8888);panel.surface.draw(new Canvas(empty));assertEquals("Invalidated pose must disappear, never move to origin",0,count(empty,0xff087f67));empty.recycle();
  }finally{f.cleanup();}
 }
 static int count(Bitmap b,int color){int n=0;for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)if(b.getPixel(x,y)==color)n++;return n;}
}
