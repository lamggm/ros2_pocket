package com.lamggm.rospocket;

import android.app.Activity;
import android.content.Intent;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.*;

/** Captures bounded immutable plot data before the external Android picker. */
final class PlotExportPanel extends LinearLayout {
 static final int REQUEST=5005;
 // One provider write process-wide, no queued exports; never uses the DDS worker.
 private static final Executor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"pocket-csv");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private final Activity activity;
 private final PlotView plot;
 private final Button export;
 private final TextView status;
 private List<PlotHistory.Series> pending;
 private boolean paused;
 private boolean writing,closed;
 PlotExportPanel(Activity activity,PlotView plot){
  super(activity);this.activity=activity;this.plot=plot;setOrientation(VERTICAL);
  export=new Button(activity);export.setId(R.id.plot_export);export.setTag("plot-export");export.setText("Exportar gráficos em CSV");export.setOnClickListener(v->begin());addView(export);
  status=new TextView(activity);status.setId(R.id.plot_export_status);status.setTag("plot-export-status");status.setTextColor(0xff9aa9bc);status.setText("CSV de snapshots retidos, não grava todas as mensagens. O seletor de arquivos pode suspender DDS ao colocar o app em segundo plano.");addView(status);
 }
 private void begin(){
  if(pending!=null||writing||closed)return;
  List<PlotHistory.Series> snapshot=plot.exportSeries();boolean points=false;
  for(PlotHistory.Series s:snapshot)if(!s.points.isEmpty())points=true;
  if(!points){status.setText("Selecione séries com pontos antes de exportar.");return;}
  pending=snapshot;paused=plot.pausedForExport();export.setEnabled(false);
  Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"ros-pocket-graphs.csv");
  try{activity.startActivityForResult(intent,REQUEST);status.setText("Snapshot congelado. Escolha onde salvar o CSV.");}
  catch(RuntimeException ex){pending=null;export.setEnabled(true);status.setText("Não foi possível abrir o seletor de arquivos.");}
 }
 boolean result(int request,int code,Intent data){
  if(request!=REQUEST)return false;
  if(closed||writing)return true;
  List<PlotHistory.Series> snapshot=pending;pending=null;
  if(code!=Activity.RESULT_OK){finish("Exportação cancelada.");return true;}
  if(snapshot==null){finish("Snapshot indisponível após recriação. Exporte novamente.");return true;}
  Uri uri=data==null?null:data.getData();
  if(uri==null||!"content".equals(uri.getScheme())){finish("Destino inválido. Use o seletor de documentos.");return true;}
  final boolean mode=paused;
  final ContentResolver resolver=activity.getApplicationContext().getContentResolver();
  final WeakReference<PlotExportPanel> owner=new WeakReference<>(this);
  writing=true;status.setText("Salvando CSV…");
  try{IO.execute(()->write(resolver,uri,snapshot,mode,owner));}
  catch(RejectedExecutionException ex){finish("Outra exportação ainda está sendo concluída. Tente novamente.");}
  return true;
 }
 private static void write(ContentResolver resolver,Uri uri,List<PlotHistory.Series> snapshot,boolean mode,WeakReference<PlotExportPanel> owner){
  String message;
  try{
   byte[] bytes=PlotCsvExporter.encode(snapshot,mode);
   try(OutputStream output=resolver.openOutputStream(uri,"wt")){
    if(output==null)throw new IOException("No output stream");
    output.write(bytes);output.flush();
   }
   message="CSV salvo · "+bytes.length+" bytes";
  }catch(IOException|RuntimeException ex){message="Falha ao salvar CSV. O destino pode conter arquivo vazio/parcial; tente novamente.";}
  final String outcome=message;
  new Handler(Looper.getMainLooper()).post(()->{PlotExportPanel panel=owner.get();if(panel!=null&&!panel.closed)panel.finish(outcome);});
 }
 private void finish(String message){writing=false;export.setEnabled(true);status.setText(message);}
 void dispose(){closed=true;pending=null;export.setEnabled(false);}
}
