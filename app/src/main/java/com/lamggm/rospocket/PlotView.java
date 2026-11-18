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
 private final TextView summary;
 private final TextView sourceLabel;
 private final LinearLayout configuration;
 private final Button pause;
 private final Chart chart;private final LinearLayout toolbar;private int tilePixels;private boolean compact,previewOwned;
 private android.app.AlertDialog unitDialog;
 private boolean paused;private long revision=-1;
 private long lastDrawNanos;
 private long preparedRevision,drawnRevision=-1;
 private String drawnSeries="[]",chartTitle="Gráfico";
 private List<PlotHistory.Series> visible=Collections.emptyList();
 PlotView(Context context,PlotController controller){
  super(context);this.controller=controller;setId(R.id.plot_panel);setOrientation(VERTICAL);setTag("live-plot");
  toolbar=new LinearLayout(context);toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);toolbar.setBaselineAligned(false);addView(toolbar);
  TextView title=new TextView(context);title.setTag("plot-inner-title");title.setText("Gráfico");title.setTextSize(19);title.setTextColor(UiTheme.color(getContext(),0xffe6edf3));toolbar.addView(title,new LayoutParams(0,-2,1));
  configuration=new LinearLayout(context);configuration.setOrientation(VERTICAL);configuration.setTag("plot-configuration");configuration.setVisibility(GONE);
  pause=new VisualButton(context,"icon","pause");pause.setId(R.id.plot_pause);pause.setTag("plot-pause");pause.setText("Pausar gráfico");pause.setContentDescription("Pausar gráfico");pause.setOnClickListener(v->{paused=!paused;pause.setSelected(paused);pause.setText(paused?"Retomar gráfico":"Pausar gráfico");pause.setContentDescription(pause.getText());UiTheme.apply(pause);refresh();});toolbar.addView(pause,new LayoutParams(dp(48),dp(48)));
  Button units=new VisualButton(context,"icon","edit");units.setTag("plot-units");units.setText("Unidades das séries");units.setContentDescription("Editar unidades das séries");units.setOnClickListener(v->unitChoices());toolbar.addView(units,new LayoutParams(dp(48),dp(48)));
  Button settings=new VisualButton(context,"icon","more");settings.setText("Séries e detalhes");settings.setTag("plot-settings");settings.setContentDescription("Escolher séries, detalhes e exportar gráfico");settings.setOnClickListener(v->configuration.setVisibility(configuration.getVisibility()==GONE?VISIBLE:GONE));toolbar.addView(settings,new LayoutParams(dp(48),dp(48)));
  sourceLabel=new TextView(context);sourceLabel.setText("Selecione campos no menu ⋯");sourceLabel.setTextColor(UiTheme.color(context,0xff9aa9bc));sourceLabel.setTextSize(12);sourceLabel.setSingleLine(true);sourceLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);sourceLabel.setGravity(android.view.Gravity.CENTER_VERTICAL);addView(sourceLabel,new LayoutParams(-1,dp(48)));
  chart=new Chart(context);chart.setId(R.id.plot_chart);chart.setContentDescription("Gráfico numérico: tempo de recepção monotônico, valores brutos");addView(chart,new LayoutParams(-1,(int)(240*getResources().getDisplayMetrics().density)));
  summary=new TextView(context);summary.setText("Escolha campos numéricos em ⋯");summary.setTextSize(11);summary.setTextColor(UiTheme.color(context,0xff9aa9bc));summary.setMaxLines(2);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.setPadding(0,dp(12),0,dp(8));addView(summary);
  addView(configuration);TextView hint=new TextView(context);hint.setTag("plot-instructions");hint.setText("Para traçar campos da inspeção estruturada, marque as caixas abaixo (Watch + caminho).\nToques na árvore selecionam apenas o inspetor; as caixas selecionam o gráfico.\nAté 4 séries · 30 s / 600 pontos por série.\nEixo Y bruto compartilhado (autoescala); unidades somente declaradas.\nRecepção monotônica, não horário da origem. Lacunas não são zeros.");hint.setTextColor(UiTheme.color(getContext(),0xff9aa9bc));hint.setTextSize(12);configuration.addView(hint);
  choices=new LinearLayout(context);choices.setOrientation(VERTICAL);configuration.addView(choices);
  legend=new TextView(context);legend.setTextColor(UiTheme.color(getContext(),0xffe6edf3));legend.setTextSize(12);configuration.addView(legend);
 }
 void title(String value){if(!chartTitle.equals(value)){chartTitle=value;chart.setContentDescription(value+" · tempo de recepção monotônico, valores brutos");chart.invalidate();}}
 void previewMode(boolean compact,int height){
  if(compact)closeDetails();
  if(!previewOwned||this.compact!=compact){previewOwned=true;this.compact=compact;toolbar.setVisibility(compact?GONE:VISIBLE);toolbar.findViewWithTag("plot-inner-title").setVisibility(INVISIBLE);sourceLabel.setVisibility(compact?GONE:VISIBLE);summary.setVisibility(compact?GONE:VISIBLE);if(compact)configuration.setVisibility(GONE);}
  int target=Math.max(1,height);LinearLayout.LayoutParams lp=(LayoutParams)chart.getLayoutParams();if(lp.height!=target){lp.height=target;chart.setLayoutParams(lp);chart.invalidate();}
 }
 void tileHeight(int height){if(tilePixels!=height){tilePixels=height;requestLayout();}}
 @Override protected void onMeasure(int widthSpec,int heightSpec){if(tilePixels<=0){super.onMeasure(widthSpec,heightSpec);return;}android.view.ViewGroup.LayoutParams lp=chart.getLayoutParams();lp.height=dp(96);super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int overhead=getMeasuredHeight()-chart.getMeasuredHeight();lp.height=Math.max(dp(96),tilePixels-overhead);super.onMeasure(widthSpec,heightSpec);}
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 List<PlotHistory.Series> displayed(){return visible;}
 List<PlotHistory.Series> exportSeries(){synchronized(controller){refresh();return visible;}}
 boolean pausedForExport(){return paused;}
 org.json.JSONObject qaState(long now) throws org.json.JSONException {
  android.graphics.Rect rect=new android.graphics.Rect();
  boolean fullyVisible=chart.isShown()&&chart.hasWindowFocus()&&chart.getWidth()>0&&chart.getHeight()>0
   &&chart.getGlobalVisibleRect(rect)&&rect.width()==chart.getWidth()&&rect.height()==chart.getHeight();
  return new org.json.JSONObject().put("observedMonotonicNs",now).put("paused",paused).put("chartFullyVisible",fullyVisible)
   .put("lastDrawMonotonicNs",lastDrawNanos==0?org.json.JSONObject.NULL:lastDrawNanos)
   .put("drawAgeNs",lastDrawNanos==0||now<lastDrawNanos?org.json.JSONObject.NULL:now-lastDrawNanos)
   .put("preparedRevision",preparedRevision).put("drawnRevision",drawnRevision<0?org.json.JSONObject.NULL:drawnRevision)
   .put("drawPending",drawnRevision!=preparedRevision).put("drawnSeries",new org.json.JSONArray(drawnSeries))
   .put("series",controller.qaSeries(visible));
 }
 void refresh(){refresh(controller.revision());}
 // A worker invalidation may occur after this revision is observed.
 void refresh(long observedRevision){
  preparedRevision++;
  long resetRevision=controller.resetRevision();
  if(!paused||(resetRevision>revision&&resetRevision<=observedRevision))visible=controller.history.snapshot();
  else if(revision!=observedRevision){
   // Slot retirement removes only that frozen trace. Healthy paused traces never
   // get replaced by advancing intake just because another reader changed.
   List<PlotHistory.Series> retained=new ArrayList<>();
   Map<String,PlotHistory.Source> current=new HashMap<>();
   for(PlotHistory.Series s:controller.history.snapshot())current.put(s.id,s.provenance);
   for(PlotHistory.Series s:visible){
    PlotHistory.Source identity=current.get(s.id);
    // An ID can be reselected before refresh after its reader was retired.
    // Keep only the original immutable identity, never its same-name replacement.
    if(identity!=null&&s.provenance.same(identity))retained.add(s);
   }
   visible=Collections.unmodifiableList(retained);
  }
  visible=controller.presentation(visible);
  revision=observedRevision;
  choices.removeAllViews();
  for(PlotController.Field f:controller.fields()){
   CheckBox check=new CheckBox(getContext());check.setTag("plot-field:"+f.id());String shownUnit=controller.unit(f);check.setText(f.watch+" · "+f.path+(shownUnit==null?" [unidade desconhecida]":" ["+shownUnit+"]"));check.setTextColor(UiTheme.color(getContext(),0xffe6edf3));check.setChecked(controller.selected(f.id()));
   check.setOnCheckedChangeListener((b,on)->{if(on){if(!controller.select(f.watch,f.path)){check.setChecked(false);Toast.makeText(getContext(),"Limite de quatro séries",Toast.LENGTH_SHORT).show();}}else {removeSeries(f.id());}refresh();});choices.addView(check);
  }
  for(PlotHistory.Series s:controller.history.snapshot()){
   Button unit=new Button(getContext());unit.setText("Unidade · "+s.id.replace('\n',' '));unit.setTag("plot-unit:"+s.id);unit.setOnClickListener(v->unitMenu(s));choices.addView(unit);
   Button remove=new Button(getContext());remove.setText("Remover "+s.id.replace('\n',' '));remove.setTag("plot-remove:"+s.id);
   remove.setOnClickListener(v->{removeSeries(s.id);refresh();});choices.addView(remove);
  }
  StringBuilder labels=new StringBuilder(paused?"PAUSADO · ingestão continua\n":"Histórico de amostras recebidas\n");
  Set<Integer> counters=new HashSet<>();
  int i=0;for(PlotHistory.Series s:visible){labels.append(++i).append(" · ").append(s.id.replace('\n',' ')).append(s.unit==null?" · unidade desconhecida":" · "+s.unit).append("\n");
   if(s.provenance.knownLocalDiscarded!=null&&counters.add(s.provenance.slot))labels.append("Watch ").append(s.provenance.slot+1).append(" · descartes locais do lote: ").append(s.provenance.knownLocalDiscarded).append(" (por reader; não perda DDS/rede)\n");
  }
  if(visible.isEmpty())labels.append("Sem série selecionada / sem dados numéricos atuais.");
  for(String field:controller.pendingProfileFields())labels.append("\nPERFIL · campo pendente: ").append(field);
  legend.setText(labels);sourceLabel.setText(visible.isEmpty()?"Selecione campos no menu ⋯":visible.size()==1?visible.get(0).id.replace('\n',' '):visible.size()+" séries selecionadas");StringBuilder shortLabel=new StringBuilder(paused?"PAUSADO · ":"");if(visible.isEmpty())shortLabel.append("Sem histórico selecionado");else for(PlotHistory.Series s:visible){if(shortLabel.length()>0)shortLabel.append("  ·  ");shortLabel.append(s.id.replace('\n',' ')).append(": ");PlotHistory.Point last=s.points.isEmpty()?null:s.points.get(s.points.size()-1);shortLabel.append(last==null||last.value==null?"—":last.value.toString()).append(s.unit==null?" [unidade desconhecida]":" "+s.unit);}summary.setText(shortLabel);summary.setTextColor(UiTheme.color(getContext(),paused?0xffffa97d:0xff9aa9bc));chart.invalidate();
 }
 void closeDetails(){if(unitDialog!=null){unitDialog.dismiss();unitDialog=null;}}
 private void showUnitDialog(android.app.AlertDialog dialog){closeDetails();unitDialog=dialog;dialog.setOnDismissListener(ignored->{if(unitDialog==dialog)unitDialog=null;});dialog.show();UiTheme.dialog(dialog);}
 private void unitChoices(){
  List<PlotHistory.Series> selected=new ArrayList<>(visible);if(selected.isEmpty()){Toast.makeText(getContext(),"Selecione uma série numérica para editar sua unidade.",Toast.LENGTH_SHORT).show();return;}
  String[] labels=new String[selected.size()];for(int i=0;i<labels.length;i++){PlotHistory.Series s=selected.get(i);labels[i]=s.id.replace('\n',' ')+" · "+(s.unit==null?"unidade desconhecida":s.unit);}
  android.app.AlertDialog picker=new android.app.AlertDialog.Builder(getContext()).setTitle("Unidade de qual série?").setItems(labels,(d,index)->unitMenu(selected.get(index))).setNegativeButton("Cancelar",null).create();showUnitDialog(picker);
 }
 private void unitMenu(PlotHistory.Series series){
  Object owner=controller.unitOwner(series);LinearLayout form=new LinearLayout(getContext());form.setOrientation(VERTICAL);form.setPadding(dp(16),dp(8),dp(16),dp(8));TextView hint=new TextView(getContext());hint.setText("Unidade declarada, sem conversão de valores. Vazio herda o instrumento do mesmo campo, quando disponível. Salve a configuração do perfil para persistir.");form.addView(hint);
  EditText input=new EditText(getContext());input.setTag("plot-series-unit");input.setContentDescription("Unidade da série (vazio para automático)");input.setSingleLine(true);input.setText(controller.declaredUnit(series));input.setTextColor(UiTheme.color(getContext(),0xffe6edf3));input.setHintTextColor(UiTheme.color(getContext(),0xff9aa9bc));form.addView(input,new LayoutParams(-1,dp(48)));TextView error=new TextView(getContext());form.addView(error);
  android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(getContext()).setTitle("Unidade da série").setView(form).setPositiveButton("Usar",null).setNegativeButton("Cancelar",null).create();showUnitDialog(dialog);dialog.getButton(-1).setOnClickListener(v->{try{if(controller.setUnit(series,owner,input.getText().toString().trim()))refresh();dialog.dismiss();}catch(IllegalArgumentException invalid){error.setText("Máximo 24 caracteres, sem controles. Valores não são convertidos.");}});
 }
 private void removeSeries(String id){
  controller.removeSelection(id);
  if(paused){
   List<PlotHistory.Series> retained=new ArrayList<>();
   for(PlotHistory.Series s:visible)if(!s.id.equals(id))retained.add(s);
   visible=Collections.unmodifiableList(retained);
  }
 }
 static double fraction(double value,double min,double max){
  if(min==max)return .5;
  double span=max-min;if(Double.isFinite(span))return Math.max(0,Math.min(1,(value-min)/span));
  double scale=Math.max(Math.abs(min),Math.abs(max));
  double lo=min/scale,hi=max/scale;
  return Math.max(0,Math.min(1,(value/scale-lo)/(hi-lo)));
 }
 private final class Chart extends View {
  private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
  Chart(Context c){super(c);setBackgroundColor(UiTheme.color(c,0xff0d1117));}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);
   if(getWidth()<=24||getHeight()<=(compact?24:75))return;
   drawChart(canvas);
   // Bind statistics to this completed draw, not refresh()'s future frame.
   if(BuildConfig.DEBUG){
    try{drawnSeries=controller.qaSeries(visible).toString();drawnRevision=preparedRevision;lastDrawNanos=System.nanoTime();}
    catch(org.json.JSONException ex){drawnSeries="[]";drawnRevision=-1;lastDrawNanos=0;}
   }
  }
  private float sp(int size){return size*getResources().getDisplayMetrics().scaledDensity;}
  private String clipText(String value,float width){return android.text.TextUtils.ellipsize(value,new android.text.TextPaint(pen),Math.max(0,width),android.text.TextUtils.TruncateAt.END).toString();}
  private String tick(double value){return new java.text.DecimalFormat("0.##",java.text.DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value==0?0:value);}
  private String yTick(double value){double abs=Math.abs(value);return abs!=0&&(abs<.01||abs>=10000)?String.format(Locale.ROOT,"%.2e",value):tick(value);}
  private String timeTick(double seconds){return seconds<0&&seconds>-.01?String.format(Locale.ROOT,"%.1g s",seconds):tick(seconds)+" s";}
  private void drawChart(Canvas canvas){
   float inset=dp(8),right=getWidth()-inset,top=inset,baseline=top+sp(14);
   if(right<=inset)return;
   int fg=UiTheme.color(getContext(),0xffe6edf3),muted=UiTheme.color(getContext(),0xff9aa9bc);
   pen.setTextAlign(Paint.Align.LEFT);pen.setColor(fg);pen.setTextSize(sp(14));
   canvas.drawText(clipText(chartTitle,right-inset),inset,baseline,pen);
   top=baseline+sp(12);pen.setTextSize(sp(11));float line=pen.getFontSpacing()+dp(2);
   int legendRows=(visible.size()+1)/2;
   float bottom=getHeight()-inset-(legendRows+1)*line;
   if(bottom-top<dp(14))return;
   double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;long first=Long.MAX_VALUE,last=0;
   for(PlotHistory.Series s:visible)for(PlotHistory.Point p:s.points){first=Math.min(first,p.time);last=Math.max(last,p.time);if(p.value!=null){min=Math.min(min,p.value);max=Math.max(max,p.value);}}
   pen.setColor(muted);
   if(!Double.isFinite(min)||!Double.isFinite(max)){canvas.drawText("Sem dados",inset,top+line,pen);drawLegend(canvas,right,line,fg);return;}
   String low=yTick(min),high=yTick(max),middle=yTick(min/2+max/2);boolean constant=min==max;
   float left=inset+Math.max(pen.measureText(middle),Math.max(pen.measureText(low),pen.measureText(high)))+dp(6);
   if(right-left<dp(20))return;
   pen.setStrokeWidth(dp(1));
   for(int row=0;row<3;row++){
    float y=top+(bottom-top)*row/2;
    pen.setColor(VisualButton.border(getContext()));canvas.drawLine(left,y,right,y,pen);
    if(constant&&row!=1)continue;
    pen.setColor(muted);pen.setTextAlign(Paint.Align.RIGHT);
    canvas.drawText(constant?low:row==0?high:row==1?middle:low,left-dp(5),y-(pen.ascent()+pen.descent())/2,pen);
   }
   pen.setColor(VisualButton.border(getContext()));canvas.drawLine(left,top,left,bottom,pen);pen.setColor(muted);
   if(last==first) {pen.setTextAlign(Paint.Align.CENTER);canvas.drawText("0 s",(left+right)/2,bottom+line,pen);}
   else {
    String start=timeTick(-(last-first)/1e9);pen.setTextAlign(Paint.Align.LEFT);canvas.drawText(start,left,bottom+line,pen);
    if(right-left>pen.measureText(start)+pen.measureText("0 s")+dp(8)){pen.setTextAlign(Paint.Align.RIGHT);canvas.drawText("0 s",right,bottom+line,pen);}
   }
   drawLegend(canvas,right,line,fg);
   int i=0;
   for(PlotHistory.Series s:visible){
    pen.setColor(UiTheme.color(getContext(),COLORS[i++%COLORS.length]));pen.setStrokeWidth(3);
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
  private void drawLegend(Canvas canvas,float right,float line,int fg){
   if(visible.isEmpty())return;
   float cell=(getWidth()-dp(16))/2f;
   pen.setTextAlign(Paint.Align.RIGHT);
   for(int i=0;i<visible.size();i++){
    PlotHistory.Series series=visible.get(i);int n=visible.size()-1-i;
    float x=right-n%2*cell,y=getHeight()-dp(8)-pen.descent()-n/2*line;
    String path=series.path;int last=path.lastIndexOf('.');if(last>=0){int parent=path.lastIndexOf('.',last-1);if(parent>=0)path=path.substring(parent+1);}
    String name=(series.provenance.slot>=0?(series.provenance.slot+1)+": ":"")+path+(series.unit==null?"":" · "+series.unit);
    name=clipText(name,cell-dp(16));if(name.isEmpty())continue;
    pen.setColor(fg);canvas.drawText(name,x,y,pen);
    pen.setColor(UiTheme.color(getContext(),COLORS[i%COLORS.length]));canvas.drawCircle(x-pen.measureText(name)-dp(6),y+(pen.ascent()+pen.descent())/2,dp(2),pen);
   }
   pen.setTextAlign(Paint.Align.LEFT);
  }
 }
}
