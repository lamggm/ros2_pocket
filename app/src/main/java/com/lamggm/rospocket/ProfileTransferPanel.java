package com.lamggm.rospocket;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;

/** Bounded document-provider I/O, independent of the DDS executor. */
final class ProfileTransferPanel extends LinearLayout {
 interface Selection {BenchProfiles.Profile get();}
 interface Importer {void add(BenchProfiles.Profile profile,String name)throws Exception;}
 static final int EXPORT=6006,IMPORT=6007;
 private static final Executor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"pocket-profiles-io");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private final Activity activity;private final Selection selection;private final Importer importer;
 private final Button export,importButton;private final TextView status;
 private BenchProfiles.Profile pending;private boolean busy,closed;private int waiting;private long revision;private AlertDialog dialog;
 ProfileTransferPanel(Activity activity,Selection selection,Importer importer){
  super(activity);this.activity=activity;this.selection=selection;this.importer=importer;setOrientation(VERTICAL);
  export=new Button(activity);export.setId(R.id.profile_export);export.setText("Exportar perfil selecionado (JSON)");export.setAllCaps(false);export.setOnClickListener(v->beginExport());addView(export);
  importButton=new Button(activity);importButton.setId(R.id.profile_import);importButton.setText("Importar perfil (JSON)");importButton.setAllCaps(false);importButton.setOnClickListener(v->beginImport());addView(importButton);
  status=new TextView(activity);status.setId(R.id.profile_transfer_status);status.setTextColor(0xff9aa9bc);status.setText("JSON de configuração, sem amostras/imagens. Pode conter peer/IP e nomes de tópicos.");addView(status);
 }
 private boolean begin(){if(busy||closed)return false;busy=true;revision++;export.setEnabled(false);importButton.setEnabled(false);return true;}
 private boolean current(long token){return !closed&&busy&&revision==token;}
 private void beginExport(){
  if(busy||closed)return;
  try{pending=selection.get();}catch(RuntimeException ex){status.setText("Selecione um perfil salvo e válido antes de exportar.");return;}
  begin();final long token=revision;
  dialog=new AlertDialog.Builder(activity).setTitle("Exportar configuração?").setMessage("O JSON contém peer/IP e nomes de tópicos da bancada. Confira antes de compartilhar. Não contém amostras nem imagens.")
   .setNegativeButton("Cancelar",(d,w)->{if(current(token))finish("Exportação cancelada.");})
   .setPositiveButton("Continuar",(d,w)->{if(current(token))openPicker(EXPORT);})
   .setOnCancelListener(d->{if(current(token))finish("Exportação cancelada.");}).create();dialog.show();
 }
 private void beginImport(){if(begin())openPicker(IMPORT);}
 private void openPicker(int request){
  Intent picker=new Intent(request==EXPORT?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);
  if(request==EXPORT)picker.putExtra(Intent.EXTRA_TITLE,"ros-pocket-profile.json");
  try{waiting=request;activity.startActivityForResult(picker,request);status.setText(request==EXPORT?"Perfil salvo congelado. Escolha o destino JSON.":"Escolha um perfil JSON. Nada será aplicado ou conectado.");}
  catch(RuntimeException ex){finish("Não foi possível abrir o seletor de arquivos.");}
 }
 boolean result(int request,int code,Intent data){
  if(request!=EXPORT&&request!=IMPORT)return false;
  if(closed||!busy||waiting!=request)return true;
  waiting=0;BenchProfiles.Profile snapshot=pending;pending=null;long token=revision;
  if(code!=Activity.RESULT_OK){finish("Transferência cancelada.");return true;}
  if(request==EXPORT&&snapshot==null){finish("Perfil indisponível após recriação. Exporte novamente.");return true;}
  Uri uri=data==null?null:data.getData();if(uri==null||!"content".equals(uri.getScheme())){finish("Destino/origem inválido. Use o seletor de documentos.");return true;}
  ContentResolver resolver=activity.getApplicationContext().getContentResolver();WeakReference<ProfileTransferPanel> owner=new WeakReference<>(this);
  status.setText(request==EXPORT?"Exportando perfil…":"Lendo perfil…");
  try{IO.execute(()->transfer(resolver,uri,request,snapshot,owner,token));}
  catch(RejectedExecutionException ex){finish("Outra transferência está sendo concluída. Tente novamente.");}
  return true;
 }
 private static void transfer(ContentResolver resolver,Uri uri,int request,BenchProfiles.Profile snapshot,WeakReference<ProfileTransferPanel> owner,long token){
  String message;BenchProfiles.Profile imported=null;
  try{
   if(request==EXPORT){
    byte[] bytes=BenchProfiles.exportProfile(snapshot);
    try(OutputStream out=resolver.openOutputStream(uri,"wt")){if(out==null)throw new IOException("No output");out.write(bytes);out.flush();}
    message="Perfil exportado · "+bytes.length+" bytes";
   }else{
    byte[] bytes;
    try(InputStream in=resolver.openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
     if(in==null)throw new IOException("No input");byte[] buffer=new byte[4096];int n;
     while((n=in.read(buffer))!=-1){if(n==0||out.size()+n>BenchProfiles.MAX_BYTES)throw new IOException("Invalid/oversize input");out.write(buffer,0,n);}bytes=out.toByteArray();
    }
    imported=BenchProfiles.importProfile(bytes);message="Perfil lido; confirme a adição. Nada foi aplicado.";
   }
  }catch(Exception ex){message=request==EXPORT?"Falha ao exportar perfil. O destino pode conter arquivo vazio/parcial.":"Falha ao ler perfil. Confira JSON, schema e limite de 128 KiB. Catálogo preservado.";}
  final String outcome=message;final BenchProfiles.Profile received=imported;
  new Handler(Looper.getMainLooper()).post(()->{ProfileTransferPanel panel=owner.get();if(panel!=null&&panel.current(token)){if(received!=null)panel.confirm(received,token);else panel.finish(outcome);}});
 }
 private void confirm(BenchProfiles.Profile received,long token){
  status.setText("Perfil lido; confirme a adição. Nada foi aplicado.");
  EditText name=new EditText(activity);name.setId(R.id.profile_import_name);name.setSingleLine(true);name.setText(received.name);name.setHint("Nome único para o perfil importado");
  dialog=new AlertDialog.Builder(activity).setTitle("Adicionar perfil importado?")
   .setMessage("Domínio "+received.domain+" · modo "+received.visual.mode+". Pode conter peer/IP e tópicos. Um nome duplicado exige outro nome; não substitui perfis nem aplica/conecta.")
   .setView(name).setNegativeButton("Cancelar",(d,w)->{if(current(token))finish("Importação cancelada; catálogo preservado.");})
   .setPositiveButton("Adicionar",null).setOnCancelListener(d->{if(current(token))finish("Importação cancelada; catálogo preservado.");}).create();
  dialog.show();AlertDialog own=dialog;
  own.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v->{
   if(!current(token))return;
   try{importer.add(received,name.getText().toString());own.dismiss();finish("Perfil importado; não aplicado. Use Aplicar e depois Conectar.");}
   catch(Exception ex){name.setError("Nome inválido/duplicado, catálogo cheio ou gravação recusada. Nada foi substituído.");}
  });
 }
 private void finish(String message){if(closed)return;revision++;pending=null;waiting=0;busy=false;export.setEnabled(true);importButton.setEnabled(true);status.setText(message);}
 void dispose(){closed=true;revision++;pending=null;waiting=0;export.setEnabled(false);importButton.setEnabled(false);if(dialog!=null)dialog.dismiss();}
}
