package com.lamggm.rospocket;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Android Canvas presentation; pause freezes only this bounded display copy, not intake. */
final class PlotView extends LinearLayout {
 static final int[] COLORS={0xff48d7bb,0xffffa97d,0xff82b8ff,0xffe0a0ff};
 private final PlotController controller;
 private final LinearLayout choices;
 private final TextView legend;
 private final Button pause;
 private final Chart chart;
 private boolean paused;private long revision=-1;
 private List<PlotHistory.Series> visible=Collections.emptyList();
 PlotView(Context context,PlotController controller){
  super(context);this.controller=controller;setOrientation(VERTICAL);setTag("live-plot");
  TextView title=new TextView(context);title.setText("GRÁFICO · snapshots numéricos");title.setTextColor(COLORS[0]);addView(title);
  TextView hint=new TextView(context);hint.setText("Para traçar campos da inspeção estruturada, marque as caixas abaixo (Watch + caminho).\nToques na árvore selecionam apenas o inspetor; as caixas selecionam o gráfico.\nAté 4 séries · 30 s / 600 pontos por série.\nEixo Y bruto compartilhado (autoescala); unidades somente declaradas.\nRecepção monotônica, não horário da origem. Lacunas não são zeros.");hint.setTextColor(0xff9aa9bc);addView(hint);
  pause=new Button(context);pause.setTag("plot-pause");pause.setText("Pausar gráfico");pause.setOnClickListener(v->{paused=!paused;pause.setText(paused?"Retomar gráfico":"Pausar gráfico");refresh();});addView(pause);
  choices=new LinearLayout(context);choices.setOrientation(VERTICAL);addView(choices);
  legend=new TextView(context);legend.setTextColor(0xffe6edf3);addView(legend);
  chart=new Chart(context);chart.setContentDescription("Gráfico numérico: tempo de recepção monotônico, valores brutos");addView(chart,new LayoutParams(-1,(int)(240*getResources().getDisplayMetrics().density)));
 }
 List<PlotHistory.Series> displayed(){return visible;}
 void refresh(){refresh(controller.revision());}
 // A worker invalidation may occur after this revision is observed.
 void refresh(long observedRevision){
  long resetRevision=controller.resetRevision();
  if(!paused||(resetRevision>revision&&resetRevision<=observedRevision))visible=controller.history.snapshot();
  else if(revision!=observedRevision){
   // Slot retirement removes only that frozen trace. Healthy paused traces never
   // get replaced by advancing intake just because another reader changed.
   List<PlotHistory.Series> retained=new ArrayList<>();
   for(PlotHistory.Series s:visible)if(controller.selected(s.id))retained.add(s);
   visible=Collections.unmodifiableList(retained);
  }
  revision=observedRevision;
  choices.removeAllViews();
  for(PlotController.Field f:controller.fields()){
   CheckBox check=new CheckBox(getContext());check.setTag("plot-field:"+f.id());check.setText(f.watch+" · "+f.path+(f.unit==null?" [unidade desconhecida]":" ["+f.unit+"]"));check.setTextColor(0xffe6edf3);check.setChecked(controller.selected(f.id()));
   check.setOnCheckedChangeListener((b,on)->{if(on){if(!controller.select(f.watch,f.path)){check.setChecked(false);Toast.makeText(getContext(),"Limite de quatro séries",Toast.LENGTH_SHORT).show();}}else {removeSeries(f.id());}refresh();});choices.addView(check);
  }
  for(PlotHistory.Series s:controller.history.snapshot()){
   Button remove=new Button(getContext());remove.setText("Remover "+s.id.replace('\n',' '));remove.setTag("plot-remove:"+s.id);
   remove.setOnClickListener(v->{removeSeries(s.id);refresh();});choices.addView(remove);
  }
  StringBuilder labels=new StringBuilder(paused?"PAUSADO · ingestão continua\n":"Histórico de snapshots recebidos\n");
  int i=0;for(PlotHistory.Series s:visible){labels.append(++i).append(" · ").append(s.id.replace('\n',' ')).append(s.unit==null?" · unidade desconhecida":" · "+s.unit).append("\n");}
  if(visible.isEmpty())labels.append("Sem série selecionada / sem dados numéricos atuais.");legend.setText(labels);chart.invalidate();
 }
 private void removeSeries(String id){
  controller.history.remove(id);
  if(paused){
   List<PlotHistory.Series> retained=new ArrayList<>();
   for(PlotHistory.Series s:visible)if(!s.id.equals(id))retained.add(s);
   visible=Collections.unmodifiableList(retained);
  }
 }
 static double fraction(double value,double min,double max){
  if(min==max)return .5;
  double scale=Math.max(Math.abs(min),Math.abs(max));
  double lo=min/scale,hi=max/scale;
  return Math.max(0,Math.min(1,(value/scale-lo)/(hi-lo)));
 }
 private final class Chart extends View {
  private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
  Chart(Context c){super(c);setBackgroundColor(0xff0d1117);}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);float left=12,top=40,right=getWidth()-12,bottom=getHeight()-35;
   if(right<=left||bottom<=top)return;
   pen.setColor(0xff9aa9bc);pen.setStrokeWidth(1);pen.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
   canvas.drawLine(left,bottom,right,bottom,pen);
   double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;long first=Long.MAX_VALUE,last=0;
   for(PlotHistory.Series s:visible)for(PlotHistory.Point p:s.points){first=Math.min(first,p.time);last=Math.max(last,p.time);if(p.value!=null){min=Math.min(min,p.value);max=Math.max(max,p.value);}}
   if(!Double.isFinite(min)||!Double.isFinite(max)){canvas.drawText("Sem pontos finitos",left,top,pen);return;}
   canvas.drawText(String.format(Locale.ROOT,"Y %.4g … %.4g",min,max),left,top-12,pen);
   canvas.drawText(String.format(Locale.ROOT,"recepção: −%.2f s → 0",(last-first)/1e9),left,getHeight()-10,pen);
   int i=0;
   for(PlotHistory.Series s:visible){
    pen.setColor(COLORS[i++%COLORS.length]);pen.setStrokeWidth(3);
    boolean previous=false;float px=0,py=0;
    for(PlotHistory.Point p:s.points){
     if(p.value==null){previous=false;continue;}
     float x=left+(right-left)*(last==first?.5f:(float)((double)(p.time-first)/(last-first)));
     float y=bottom-(bottom-top)*(float)fraction(p.value,min,max);
     if(previous&&!p.breakBefore)canvas.drawLine(px,py,x,y,pen);
     canvas.drawCircle(x,y,2,pen);px=x;py=y;previous=true;
    }
   }
  }
 }
}
