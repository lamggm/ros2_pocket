package com.lamggm.rospocket;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.widget.*;
import android.view.View;
import java.io.File;
import java.util.*;

/** Explicit local operations, separate from connection/session management. */
final class ProfilePanel extends LinearLayout {
 interface Actions {
  BenchProfiles.Profile capture(String id,String name);
  boolean connected();
  void apply(BenchProfiles.Profile profile);
 }
 private final ProfileStore store;private final Actions actions;
 private final Spinner select;private final EditText name;private final TextView status;private final ProfileTransferPanel transfer;
 private BenchProfiles book=new BenchProfiles();private String selected="";private boolean refreshing,failed,exists;
 ProfilePanel(Context context,Actions actions){
  super(context);this.actions=actions;setOrientation(VERTICAL);setPadding(0,16,0,16);
  File file=new File(context.getFilesDir(),"bench-profiles-v1.json");exists=file.exists()||new File(file.getPath()+".bak").exists();store=new ProfileStore(file);
  TextView title=new TextView(context);title.setText("PERFIS LOCAIS · até 16");title.setTextColor(0xff48d7bb);addView(title);
  TextView hint=new TextView(context);hint.setText("Salvar guarda a configuração atual; selecionar não altera a sessão. Aplicar exige desconexão. Conectar continua uma ação separada. Após reinício, o último perfil aplicado volta desconectado.");hint.setTextColor(0xff9aa9bc);addView(hint);
  select=new Spinner(context);select.setId(R.id.profile_select);select.setContentDescription("Selecionar perfil local");addView(select);
  name=new EditText(context);name.setId(R.id.profile_name);name.setHint("Nome do perfil (até 48 caracteres / 128 bytes)");name.setTextColor(0xffe6edf3);name.setHintTextColor(0xff9aa9bc);name.setSingleLine(true);addView(name);
  status=new TextView(context);status.setId(R.id.profile_status);status.setTextColor(0xff9aa9bc);
  button("Novo perfil",R.id.profile_new,()->refresh(""));
  button("Salvar configuração atual",R.id.profile_save,()->{BenchProfiles.Profile p=actions.capture(selected.isEmpty()?UUID.randomUUID().toString():selected,name.getText().toString());persist(book.save(p));refresh(p.id);notice("Perfil salvo; não aplicado.");});
  button("Renomear selecionado",R.id.profile_rename,()->{BenchProfiles.Profile p=chosen().rename(name.getText().toString());persist(book.save(p));refresh(p.id);notice("Perfil renomeado; configuração salva preservada.");});
  button("Excluir selecionado",R.id.profile_delete,()->{BenchProfiles.Profile p=chosen();String restore=book.applied!=null&&book.applied.id.equals(p.id)?"\nEste perfil também deixará de ser restaurado no próximo reinício.":"";new AlertDialog.Builder(context).setTitle("Excluir perfil local?").setMessage(p.name+"\nA configuração/sessão atual não será modificada."+restore).setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->attempt(()->{persist(book.delete(p.id));refresh("");notice("Perfil excluído.");})).show();});
  button("Aplicar (desconectado)",R.id.profile_apply,()->{if(actions.connected())throw new IllegalStateException("Desconecte explicitamente antes de aplicar um perfil.");BenchProfiles.Profile p=chosen();persist(book.apply(p));actions.apply(p);notice("Aplicado: "+p.name+" · desconectado. Use Conectar; watches aguardam dados novos.");});
  transfer=new ProfileTransferPanel((android.app.Activity)context,()->{if(failed)throw new IllegalStateException("Catálogo indisponível");return chosen();},(p,n)->{
   if(failed)throw new IllegalStateException("Catálogo indisponível; nada foi alterado");
   if(book.find(p.id)!=null)throw new IllegalArgumentException("Identidade de importação duplicada");
   BenchProfiles.Profile imported=p.rename(n);persist(book.save(imported));refresh(imported.id);notice("Perfil importado; não aplicado. Conectar continua uma ação separada.");
  });addView(transfer);
  addView(status);
  select.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
   public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long id){if(refreshing||position!=select.getSelectedItemPosition())return;selected=position==0?"":book.profiles.get(position-1).id;name.setText(position==0?"":book.profiles.get(position-1).name);}
   public void onNothingSelected(android.widget.AdapterView<?> parent){}
  });
  try{book=store.load();refresh(book.applied==null?"":book.applied.id);notice("Perfis locais: "+book.profiles.size()+". Selecionar não aplica nem conecta.");}
  catch(Exception ex){failed=true;transfer.dispose();for(int i=0;i<getChildCount();i++){View child=getChildAt(i);if(child instanceof Button||child instanceof EditText||child instanceof Spinner)child.setEnabled(false);}notice("Perfis indisponíveis: "+ex.getMessage()+". Nada foi apagado; conexão automática bloqueada.");}
 }
 boolean result(int request,int code,android.content.Intent data){return transfer.result(request,code,data);}
 void dispose(){transfer.dispose();}
 boolean catalogExists(){return exists||failed;}
 BenchProfiles.Profile applied(){return book.applied;}
 private BenchProfiles.Profile chosen(){BenchProfiles.Profile p=book.find(selected);if(p==null)throw new IllegalStateException("Selecione um perfil salvo.");return p;}
 private interface Operation {void run()throws Exception;}
 private void attempt(Operation operation){try{if(failed)throw new IllegalStateException("Reabra o app para recarregar os perfis");operation.run();}catch(Exception ex){notice("Perfil não alterado/aplicado: "+ex.getMessage());}}
 private void button(String caption,int id,Operation operation){Button button=new Button(getContext());button.setId(id);button.setText(caption);button.setAllCaps(false);button.setOnClickListener(v->attempt(operation));addView(button);}
 private void persist(BenchProfiles next)throws Exception{store.commit(next);book=next;exists=true;}
 private void notice(String message){status.setText(message);}
 private void refresh(String id){
  refreshing=true;selected=id;List<String> labels=new ArrayList<>();labels.add("— Novo perfil —");int position=0;
  for(BenchProfiles.Profile p:book.profiles){labels.add(p.name);if(p.id.equals(id))position=labels.size()-1;}
  ArrayAdapter<String> adapter=new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);select.setAdapter(adapter);select.setSelection(position);name.setText(position==0?"":book.profiles.get(position-1).name);if(position==0)selected="";refreshing=false;
 }
}
