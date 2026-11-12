package com.lamggm.rospocket;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** One explicitly selected visual reader. Pixel decoding never runs on UI/DDS. */
@android.annotation.SuppressLint("ViewConstructor")
class SensorPanel extends LinearLayout {
 static final class Request {
  final String wire,type;Request(String w,String t){wire=w;type=t;}
 }
 private volatile Request selected;
 private final Request[] saved=new Request[2];
 private volatile boolean available=false,visible=false;
 private volatile long stream=0;
 private volatile long sequence=0;private boolean decoding=false,closed=false;private Task pending;
 private Runnable pendingUi;private boolean uiQueued=false;
 private final java.util.concurrent.Executor decoder;
 private final ExecutorService ownedDecoder;
 private final TextView title,status;
 private final TextView displayTitle;
 private final Surface surface;
 private int mode=0;private double zoom=1;
 private SensorPacket.Decoded shown;
 private static final int FG=0xffe6edf3,ACCENT=0xff5cdbb8;
 private static final class Task {
  final Request request;final SensorPacket packet;final long stream,sequence;final BooleanSupplier guard;
  Task(Request r,SensorPacket p,long g,long s,BooleanSupplier ok){request=r;packet=p;stream=g;sequence=s;guard=ok;}
 }
 SensorPanel(Context c){this(c,null);}
 SensorPanel(Context c,java.util.concurrent.Executor executor){
  super(c);setOrientation(VERTICAL);setTag("sensor-panel");
  ownedDecoder=executor==null?Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"pocket-image-decode");t.setDaemon(true);return t;}):null;decoder=executor==null?ownedDecoder:executor;
  LinearLayout top=new LinearLayout(c);top.setGravity(android.view.Gravity.CENTER_VERTICAL);addView(top);
  displayTitle=new TextView(c);displayTitle.setText("LaserScan 2D");displayTitle.setTextSize(19);displayTitle.setTextColor(UiTheme.color(c,FG));top.addView(displayTitle,new LayoutParams(0,-2,1));
  LinearLayout details=new LinearLayout(c);details.setOrientation(VERTICAL);details.setTag("sensor-details");details.setVisibility(GONE);
  Button more=new VisualButton(c,"icon","more");more.setContentDescription("Detalhes do sensor, parar e zoom");more.setOnClickListener(v->details.setVisibility(details.getVisibility()==GONE?VISIBLE:GONE));top.addView(more,new LayoutParams(dp(48),dp(48)));
  Button expand=new VisualButton(c,"icon","expand");expand.setContentDescription("Ampliar ou reduzir visualização");top.addView(expand,new LayoutParams(dp(48),dp(48)));
  LinearLayout row=new LinearLayout(c);addView(row);button(row,"LaserScan",()->switchMode(0));button(row,"Câmera",()->switchMode(1));row.findViewWithTag("sensor-LaserScan").setSelected(true);
  surface=new Surface(c);surface.setTag("sensor-surface");addView(surface,new LinearLayout.LayoutParams(-1,(int)(320*getResources().getDisplayMetrics().density)));
  expand.setOnClickListener(v->{android.view.ViewGroup.LayoutParams p=surface.getLayoutParams();p.height=p.height>dp(320)?dp(240):dp(420);surface.setLayoutParams(p);});
  status=text("Sem leitor visual · best effort / volatile");status.setTag("sensor-status");status.setTextSize(11);status.setPadding(0,dp(10),0,0);
  addView(details);title=new TextView(c);title.setText("SENSORES VISUAIS · selecione um tópico no Explorer");title.setTextColor(UiTheme.color(c,FG));title.setTextSize(12);title.setTag("sensor-title");details.addView(title);
  LinearLayout scale=new LinearLayout(c);details.addView(scale);button(scale,"Zoom +",()->{zoom=Math.min(16,zoom*2);surface.invalidate();});button(scale,"Zoom −",()->{zoom=Math.max(.25,zoom/2);surface.invalidate();});button(scale,"Enquadrar",()->{zoom=1;surface.invalidate();});
  LinearLayout stop=new LinearLayout(c);details.addView(stop);button(stop,"Parar",()->{saved[mode]=null;selected=null;invalidateStream();});
  TextView help=new TextView(c);help.setText("Um scan ou câmera por vez · frame original, sem TF/mapa · imagens até 921600 pixels · preview até 10Hz, não gravação.");help.setTextColor(UiTheme.color(c,0xff9aa9bc));help.setTextSize(11);details.addView(help);
 }
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView text(String value){TextView t=new TextView(getContext());t.setTextColor(UiTheme.color(getContext(),FG));t.setText(value);addView(t);return t;}
 private void button(LinearLayout parent,String name,Runnable action){Button b=new VisualButton(getContext(),"secondary",null);b.setText(name);b.setTag("sensor-"+name);b.setOnClickListener(v->action.run());parent.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}
 void select(String wire,String type){
  if(!SensorPacket.supports(type))throw new IllegalArgumentException("Sensor não suportado");
  mode=type.endsWith("LaserScan_")?0:1;displayTitle.setText(mode==0?"LaserScan 2D":"Câmera");selected=new Request(wire,type);saved[mode]=selected;zoom=1;invalidateStream();
  title.setText((mode==0?"LASERSCAN 2D":"CÂMERA ROS")+" · "+wire+"\n"+type);refreshTabs();
 }
 private void switchMode(int value){mode=value;displayTitle.setText(mode==0?"LaserScan 2D":"Câmera");selected=saved[value];zoom=1;invalidateStream();title.setText(selected==null?"Selecione um tópico "+(mode==0?"LaserScan":"Image/CompressedImage")+" no Explorer":selected.wire+"\n"+selected.type);refreshTabs();}
 private void refreshTabs(){View scan=findViewWithTag("sensor-LaserScan"),camera=findViewWithTag("sensor-Câmera");scan.setSelected(mode==0);camera.setSelected(mode==1);UiTheme.apply(scan);UiTheme.apply(camera);}
 BenchProfiles.Visual profile(){
  Request scan=saved[0],camera=saved[1];
  return new BenchProfiles.Visual(mode==0?"scan":"camera",scan==null?null:new BenchProfiles.Sensor(scan.wire,scan.type,true),camera==null?null:new BenchProfiles.Sensor(camera.wire,camera.type,false));
 }
 void configure(BenchProfiles.Visual visual){
  saved[0]=visual.scan==null?null:new Request(visual.scan.wire,visual.scan.type);
  saved[1]=visual.camera==null?null:new Request(visual.camera.wire,visual.camera.type);
  switchMode("scan".equals(visual.mode)?0:1);
 }
 Request desired(){return available&&visible?selected:null;}
 long epoch(){return stream;}
 boolean current(Request request,long epoch){return stream==epoch&&desired()==request;}
 void available(boolean value){if(available!=value){available=value;invalidateStream();}}
 void visible(boolean value){if(visible!=value){visible=value;invalidateStream();}}
 void invalidateStream(){
  final long stamp;
  synchronized(this){stamp=++stream;pending=null;pendingUi=null;sequence++;}
  Runnable clear=()->{if(stream==stamp){shown=null;surface.invalidate();status.setText("Aguardando dados novos · sem imagem anterior");}};
  if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())clear.run();else queueUi(clear);
 }
 void error(Request r,String message,BooleanSupplier guard){
  final long stamp;
  synchronized(this){
   if(closed||selected!=r||desired()!=r||!guard.getAsBoolean())return;
   stamp=++sequence;pending=null;
   queueUi(()->{if(selected==r&&desired()==r&&sequence==stamp&&guard.getAsBoolean()){shown=null;surface.invalidate();status.setText("ERRO · "+message);}});
  }
 }
 boolean accept(Request r,byte[] bytes,BooleanSupplier guard){
  try{
   SensorPacket p=new SensorPacket(bytes);
   if(!p.meta.optBoolean("running")){error(r,"Leitor visual inativo",guard);return false;}
   String error=p.meta.optString("error");if(!error.isEmpty()){error(r,error,guard);return true;}
   if(!p.meta.has("kind"))return true;
   synchronized(this){if(closed||selected!=r||desired()!=r||!guard.getAsBoolean())return true;pending=new Task(r,p,stream,++sequence,guard);if(decoding)return true;decoding=true;}
   decoder.execute(this::drain);
   return true;
  }catch(Exception ex){error(r,"Pacote visual inválido: "+ex.getMessage(),guard);return false;}
 }
 private void drain(){
  for(;;){
   final Task task;synchronized(this){task=pending;pending=null;if(task==null||closed){decoding=false;return;}}
   try{
    SensorPacket.Decoded decoded=decode(task.packet);
    queueDecoded(task,()->{if(current(task)){shown=decoded;surface.invalidate();refreshStatus();}});
   }catch(Exception ex){queueDecoded(task,()->{if(current(task)){shown=null;surface.invalidate();status.setText("ERRO · "+ex.getMessage());}});}
  }
 }
 private boolean current(Task t){return !closed&&stream==t.stream&&sequence==t.sequence&&selected==t.request&&desired()==t.request&&t.guard.getAsBoolean();}
 SensorPacket.Decoded decode(SensorPacket packet)throws Exception{return packet.decode();}
 private void queueDecoded(Task task,Runnable result){
  // Admission and replacement share the invalidation lock: a retired decode
  // must not overwrite a queued clear/error, even when its UI guard would fail.
  synchronized(this){if(current(task))queueUi(result);}
 }
 private void queueUi(Runnable result){
  synchronized(this){if(closed)return;pendingUi=result;if(uiQueued)return;uiQueued=true;}
  post(this::deliverUi);
 }
 private void deliverUi(){final Runnable latest;synchronized(this){latest=pendingUi;pendingUi=null;uiQueued=false;}if(latest!=null)latest.run();}
 void dispose(){synchronized(this){closed=true;pending=null;pendingUi=null;stream++;}if(ownedDecoder!=null)ownedDecoder.shutdownNow();}
 private void refreshStatus(){
  if(shown==null){status.setText("Aguardando primeira amostra válida · best effort / volatile");return;}
  long now=System.nanoTime();double age=shown.initialAgeMs/1000.0+Math.max(0,now-shown.capturedNs)/1e9;
  String detail=shown.image!=null?shown.image.getWidth()+" × "+shown.image.getHeight():shown.meta.optInt("points")+" pontos / "+shown.meta.optInt("ranges")+" ranges · decimação "+shown.meta.optInt("stride",1);
  status.setText((shown.fresh(now)?"RECEBENDO":"OBSOLETO")+" · "+String.format(java.util.Locale.ROOT,"%.1f s",age)+"\nframe: "+shown.meta.optString("frameId")+" · "+detail);
 }
 private final class Surface extends View {
  private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
  Surface(Context c){super(c);setContentDescription("Visualização alternada de câmera ou LaserScan no frame do sensor");}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);canvas.drawColor(UiTheme.color(getContext(),0xff101820));SensorPacket.Decoded d=shown;if(d==null){pen.setAlpha(255);pen.setColor(VisualButton.muted(getContext()));pen.setTextSize(dp(13));pen.setTextAlign(Paint.Align.CENTER);canvas.drawText("Aguardando amostras",getWidth()/2f,getHeight()*.65f,pen);pen.setTextAlign(Paint.Align.LEFT);return;}
   boolean fresh=d.fresh(System.nanoTime());pen.setAlpha(fresh?255:80);
   if(d.image!=null){float scale=Math.min((float)getWidth()/d.image.getWidth(),(float)getHeight()/d.image.getHeight());float w=d.image.getWidth()*scale,h=d.image.getHeight()*scale;canvas.drawBitmap(d.image,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),pen);}
   else if(d.xy!=null){
    double extent=d.meta.optDouble("rangeMax",10)/zoom;double scale=Math.min(getWidth(),getHeight())*.45/extent;float cx=getWidth()/2f,cy=getHeight()/2f;
    pen.setColor(UiTheme.color(getContext(),0xff405064));pen.setAlpha(255);pen.setStyle(Paint.Style.STROKE);
    for(int ring=1;ring<=4;ring++){float radius=(float)(extent*ring/4*scale);canvas.drawCircle(cx,cy,radius,pen);}
    canvas.drawLine(cx,0,cx,getHeight(),pen);canvas.drawLine(0,cy,getWidth(),cy,pen);pen.setStyle(Paint.Style.FILL);
    pen.setColor(UiTheme.color(getContext(),FG));pen.setTextSize(12*getResources().getDisplayMetrics().density);canvas.drawText(String.format(java.util.Locale.ROOT,"raio %.2g m · +X ↑ / +Y ←",extent),8,pen.getTextSize()+4,pen);
    pen.setColor(UiTheme.color(getContext(),ACCENT));pen.setAlpha(fresh?255:80);int save=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());
    for(int i=0;i<d.xy.length;i+=2){double x=cx-d.xy[i+1]*scale,y=cy-d.xy[i]*scale;if(Double.isFinite(x)&&Double.isFinite(y)&&x>=0&&x<getWidth()&&y>=0&&y<getHeight())canvas.drawCircle((float)x,(float)y,2.5f,pen);}
    canvas.restoreToCount(save);pen.setColor(UiTheme.color(getContext(),0xffffa97d));pen.setAlpha(255);canvas.drawCircle(cx,cy,4,pen);
   }
   pen.setAlpha(255);refreshStatus();if(isAttachedToWindow())postInvalidateDelayed(500);
  }
 }
}
