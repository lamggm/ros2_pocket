package com.lamggm.rospocket;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;
import java.util.function.*;

/** Local SAF import: independent of DDS, bounded I/O and stale-owner fencing. */
final class RobotAssetsPanel extends LinearLayout {
 static final int IMPORT=6010;
 private static final Executor DEFAULT_IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"pocket-mesh-io");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private final Executor io;private final Handler main=new Handler(Looper.getMainLooper());
 private final Activity activity;private final Supplier<RobotDescription> selection;private final Consumer<RobotAssetBundle> accepted;private final RobotAssetStore store;private final Button button;private final TextView status;
 private boolean busy,waiting,canImport;private volatile boolean closed;private volatile long revision;private RobotDescription pending;private String additionalRequested="",preferredFingerprint="";private long selectionEpoch;
 RobotAssetsPanel(Activity a,Supplier<RobotDescription> selection,Consumer<RobotAssetBundle> accepted){this(a,selection,accepted,DEFAULT_IO);}
 RobotAssetsPanel(Activity a,Supplier<RobotDescription> selection,Consumer<RobotAssetBundle> accepted,Executor io){super(a);activity=a;this.io=io;this.selection=selection;this.accepted=accepted;store=new RobotAssetStore(a.getFilesDir());setOrientation(VERTICAL);
  button=new VisualButton(a,"secondary",null);button.setText("Importar malhas (ZIP)");button.setTag("robot-import-assets");button.setEnabled(false);button.setOnClickListener(v->begin());addView(button,new LayoutParams(-1,dp(48)));
  status=new TextView(a);status.setTag("robot-assets-status");status.setTextSize(11);status.setTextColor(VisualButton.muted(a));status.setText("Malhas externas não vêm pelo DDS. Importe um pacote local compatível.");addView(status);
  File saved=new File(a.getFilesDir(),"robot-assets-v1.zip");if(saved.exists()||new File(saved+".bak").exists()){busy=true;long token=++revision;try{io.execute(()->load(store,new WeakReference<>(this),token));}catch(RejectedExecutionException e){finish("Carregamento local ocupado. Importe novamente se necessário.");}}
 }
 private static void load(RobotAssetStore store,WeakReference<RobotAssetsPanel> owner,long token){RobotAssetBundle value=null;try{value=store.load();}catch(Exception ignored){}complete(owner,token,value,value==null?"Pacote local indisponível. Importe novamente.":"Pacote local carregado · "+value.name+" · "+value.meshCount()+" malha(s)");}
 void selected(RobotDescription model){
  String key=model==null?"":model.fingerprint;if(key.equals(additionalRequested))return;additionalRequested=key;main.removeCallbacksAndMessages(null);long epoch=++selectionEpoch;
  if(model==null||busy||preferredFingerprint.equals(key))return;
  if(!store.hasAdditional(model)&&!store.hasLegacy())return;
  requestLoad(model,epoch);
 }
 private void requestLoad(RobotDescription model,long epoch){
  if(closed||busy||epoch!=selectionEpoch)return;
  WeakReference<RobotAssetsPanel> owner=new WeakReference<>(this);RobotAssetStore source=store;
  try{io.execute(()->{RobotAssetBundle result=null;try{result=source.loadFor(model);}catch(Exception ignored){}RobotAssetBundle loaded=result;new Handler(Looper.getMainLooper()).post(()->{RobotAssetsPanel panel=owner.get();if(panel!=null&&!panel.closed&&!panel.busy&&epoch==panel.selectionEpoch&&loaded!=null){panel.preferredFingerprint=loaded.descriptionHash;panel.accepted.accept(loaded);panel.status.setText("Pacote local carregado · "+loaded.name+" · "+loaded.meshCount()+" malha(s)");}});});}
  catch(RejectedExecutionException ignored){main.postDelayed(()->requestLoad(model,epoch),250);}
 }
 void setCanImport(boolean value){canImport=value;button.setEnabled(value&&!busy&&!closed);}
 private boolean current(long token){return !closed&&revision==token;}
 private void begin(){if(busy||closed)return;pending=selection.get();if(pending==null)return;busy=true;selectionEpoch++;main.removeCallbacksAndMessages(null);long token=++revision;setCanImport(canImport);Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});try{waiting=true;activity.startActivityForResult(picker,IMPORT);status.setText("Escolha o ZIP de assets desta descrição. Apenas arquivos locais; sem publicação ROS.");}catch(RuntimeException e){if(current(token))finish("Não foi possível abrir o seletor de arquivos.");}}
 boolean result(int request,int code,Intent data){if(request!=IMPORT)return false;if(closed||!busy||!waiting)return true;waiting=false;RobotDescription expected=pending;pending=null;long token=revision;if(code!=Activity.RESULT_OK){finish("Importação cancelada; pacote local mantido.");return true;}Uri uri=data==null?null:data.getData();if(uri==null||!"content".equals(uri.getScheme())||expected==null){finish("Origem inválida. Use o seletor de documentos com uma descrição recebida.");return true;}status.setText("Validando pacote e malhas locais…");ContentResolver resolver=activity.getApplicationContext().getContentResolver();WeakReference<RobotAssetsPanel> owner=new WeakReference<>(this);RobotAssetStore destination=store;try{io.execute(()->transfer(destination,resolver,uri,expected,owner,token));}catch(RejectedExecutionException e){finish("Importação ocupada. Tente novamente.");}return true;}
 private static void transfer(RobotAssetStore store,ContentResolver resolver,Uri uri,RobotDescription expected,WeakReference<RobotAssetsPanel> owner,long token){RobotAssetBundle value=null;String message;
  try{byte[] bytes;try(InputStream in=resolver.openInputStream(uri)){bytes=RobotAssetStore.read(in);}RobotAssetsPanel panel=owner.get();if(panel==null||!panel.current(token))return;value=store.save(bytes,expected);message="Pacote importado · "+value.name+" · "+value.meshCount()+" malha(s) locais";}
  catch(Exception e){message="Falha ao importar pacote. Confira ZIP, vínculo com a descrição e espaço local.";}
  complete(owner,token,value,message);
 }
 private static void complete(WeakReference<RobotAssetsPanel> owner,long token,RobotAssetBundle value,String message){new Handler(Looper.getMainLooper()).post(()->{RobotAssetsPanel panel=owner.get();if(panel!=null&&panel.current(token)){boolean initial=message.startsWith("Pacote local carregado");RobotDescription model=panel.selection.get();if(value!=null&&(!initial||!panel.store.hasAdditional(model))){if(!initial)panel.preferredFingerprint=value.descriptionHash;panel.accepted.accept(value);}panel.finish(message);}});}
 private void finish(String message){if(closed)return;revision++;busy=false;waiting=false;pending=null;status.setText(message);setCanImport(canImport);additionalRequested="";selected(selection.get());}
 void dispose(){closed=true;main.removeCallbacksAndMessages(null);revision++;pending=null;waiting=false;button.setEnabled(false);}
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
