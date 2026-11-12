package com.lamggm.rospocket;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;

/** Native Views/Canvas with synthetic data only; no physical device or DDS evidence. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31,qualifiers="w1280dp-h850dp")
@LooperMode(LooperMode.Mode.PAUSED) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class RosoutPreviewTest {
 static JSONObject sample() throws Exception {
  JSONArray a=new JSONArray();String[] messages={"[DEMO] Telemetria disponível para leitura.","[DEMO] Ciclo 24 concluído; atualização do console.","[DEMO] Novo quadro recebido: 640 × 360, JPEG.","[DEMO] Aviso simulado: exemplo para inspeção visual.","[DEMO] Quatro leituras ativas no painel.","[DEMO] Erro simulado; nenhuma falha real no sistema.","[DEMO] Buffer consultado sem alterar publishers.","[DEMO] Aviso simulado: mensagem para inspeção.","[DEMO] Fluxo de imagens disponível para o painel."};int[] levels={20,10,20,30,20,40,10,30,20};
  long start=java.time.Instant.parse("2026-11-11T12:14:02Z").getEpochSecond();for(int i=0;i<messages.length;i++)a.put(RosoutPresentationTest.entry(messages[i],start+2*i,levels[i]).put("logger",i%3==0?"demo.painel":i%3==1?"demo.console_demo":"demo.camera").put("intakeAgeMs",16200-i*2000));
  return new JSONObject().put("active",true).put("count",9).put("bytes",2400).put("evicted",0).put("entries",a).put("offeredReliability",new JSONArray().put("reliable")).put("offeredDurability",new JSONArray().put("transient_local"));
 }
 private void render(int width,int height,String prefix) throws Exception {
  ConnectionActivityTest f=new ConnectionActivityTest();f.setup();
  try{
   f.bridge.snapshot=new JSONObject(f.bridge.snapshot).put("rosout",sample()).toString();f.tick();f.paint();f.a.findViewById(R.id.nav_logs).performClick();RosoutConsoleView console=(RosoutConsoleView)f.get("rosoutConsole");View root=f.a.getWindow().getDecorView().findViewWithTag("app-root");File dir=new File("build/reports/logs-v13");assertTrue(dir.isDirectory()||dir.mkdirs());
   int starts=f.bridge.starts,stops=f.bridge.stops,subscriptions=f.bridge.subscriptions;
   for(String theme:new String[]{"dark","light"}){
    UiTheme.save(f.a,theme);f.a.applyAppearance();
    for(boolean selected:width>=1000?new boolean[]{false,true}:new boolean[]{false}){
     if(selected)console.findViewWithTag("log-row:3").performClick();else if(console.findViewWithTag("log-inspector-close")!=null)console.findViewWithTag("log-inspector-close").performClick();
     RosoutPresentationTest.layout(f.a.getWindow().getDecorView(),width,height);RosoutPresentationTest.layout(root,width,height);root.getViewTreeObserver().dispatchOnGlobalLayout();
     assertTrue("A compact row must actually have drawable bounds before an inspector is opened",console.findViewWithTag("log-row:0").getWidth()>0&&console.findViewWithTag("log-row:0").getHeight()>0);
     View footerStatus=console.findViewById(R.id.rosout_status);Rect footerRect=new Rect();assertTrue("Reader status must fit above fixed navigation",footerStatus.getGlobalVisibleRect(footerRect));assertEquals("Reader status cannot be clipped by the viewport",footerStatus.getHeight(),footerRect.height());
     if(selected&&width>=1000){View copy=console.findViewWithTag("log-copy");Rect visible=new Rect();assertTrue("copy="+copy.getWidth()+"x"+copy.getHeight()+" y="+copy.getY()+" inspector="+console.findViewWithTag("log-inspector").getWidth()+"x"+console.findViewWithTag("log-inspector").getHeight()+" console="+console.getWidth()+" stage="+console.findViewWithTag("log-stage").getHeight(),copy.getGlobalVisibleRect(visible));assertEquals("Copy action must be fully visible",copy.getHeight(),visible.height());assertEquals(View.VISIBLE,console.findViewWithTag("log-inspector").getVisibility());}
     Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(new File(dir,prefix+"-"+(selected?"inspector":"console")+"-"+theme+"-host-synthetic.png"))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();
    }
   }
   assertEquals(starts,f.bridge.starts);assertEquals(stops,f.bridge.stops);assertEquals(subscriptions,f.bridge.subscriptions);
  }finally{f.cleanup();}
 }
 @Test public void tabletConsoleAndLateralInspectorRenderInBothThemes() throws Exception {render(1280,850,"tablet");}
 @Test @Config(qualifiers="w412dp-h850dp") public void phoneCompactConsoleRendersInBothThemes() throws Exception {render(412,850,"phone");}
}
