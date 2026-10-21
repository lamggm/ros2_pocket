package com.lamggm.rospocket;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import org.json.*;
import java.util.Locale;

/** Local projection only: filtering never calls JNI or changes a DDS session. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic-only view with an explicit read listener.
public final class ExplorerView extends LinearLayout {
 public interface Listener { void read(JSONObject topic); }
 private final EditText search,namespace,type;
 private final CheckBox favoritesOnly;
 private final android.content.SharedPreferences prefs;
 private final java.util.Set<String> favorites;
 private final LinearLayout rows;
 private final TextView details,summary;
 private String lastSnapshot="";
 private final Listener listener;
 private String detailKey="";
 private JSONArray topics=new JSONArray();
 private int visible;
 public ExplorerView(Context context,Listener listener) {
  super(context);setOrientation(VERTICAL);this.listener=listener;
  prefs=context.getSharedPreferences("explorer",Context.MODE_PRIVATE);
  favorites=new java.util.HashSet<>(prefs.getStringSet("favorites",java.util.Collections.emptySet()));
  search=field("Buscar tópico, tipo ou nó",R.id.explorer_search);
  namespace=field("Namespace (ex.: /robot)",R.id.explorer_namespace);
  type=field("Filtro de tipo (ex.: std_msgs/msg)",R.id.explorer_type);
  favoritesOnly=new CheckBox(context);favoritesOnly.setId(R.id.explorer_favorites);favoritesOnly.setText("Somente favoritos locais");favoritesOnly.setTextColor(0xffe6edf3);addView(favoritesOnly);
  favoritesOnly.setOnCheckedChangeListener((b,on)->render());
  summary=new TextView(context);summary.setTextColor(0xff9aa9bc);addView(summary);
  rows=new LinearLayout(context);rows.setOrientation(VERTICAL);rows.setId(R.id.explorer_rows);addView(rows);
  details=new TextView(context);details.setId(R.id.explorer_details);details.setTextColor(0xffe6edf3);details.setTextIsSelectable(true);details.setText("Detalhes: selecione um tópico.");addView(details,indexOfChild(rows));
  TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){} public void onTextChanged(CharSequence s,int start,int before,int count){render();} public void afterTextChanged(Editable e){}};
  search.addTextChangedListener(watcher);namespace.addTextChangedListener(watcher);type.addTextChangedListener(watcher);
 }
 private EditText field(String hint,int id){EditText e=new EditText(getContext());e.setId(id);e.setHint(hint);e.setSingleLine(true);e.setTextColor(0xffe6edf3);e.setHintTextColor(0xff9aa9bc);addView(e);return e;}
 private static String identity(JSONObject t){return t.optString("wireName")+"\n"+t.optString("wireType");}
 private static String fold(String s){return s.toLowerCase(Locale.ROOT);}
 private static String searchable(JSONObject topic){
  StringBuilder text=new StringBuilder(topic.optString("name")).append(' ').append(topic.optString("type"));
  JSONArray endpoints=topic.optJSONArray("endpoints");
  if(endpoints!=null)for(int i=0;i<endpoints.length();i++){
   JSONObject endpoint=endpoints.optJSONObject(i);if(endpoint==null)continue;
   JSONArray nodes=endpoint.optJSONArray("nodes");
   if(nodes!=null)for(int j=0;j<nodes.length();j++)text.append(' ').append(nodes.optString(j));
  }
  return fold(text.toString());
 }
 public void update(JSONArray value){String signature=value==null?"[]":value.toString();if(signature.equals(lastSnapshot))return;lastSnapshot=signature;topics=value==null?new JSONArray():value;render();}
 public int visibleCount(){return visible;}
 private void render(){
  rows.removeAllViews();visible=0;
  String q=fold(search.getText().toString().trim()),ns=namespace.getText().toString().trim(),t=fold(type.getText().toString().trim());
  while(ns.endsWith("/")&&ns.length()>1)ns=ns.substring(0,ns.length()-1);
  for(int i=0;i<topics.length();i++){
   JSONObject topic=topics.optJSONObject(i);if(topic==null)continue;
   String name=topic.optString("name"),kind=topic.optString("type");
   if(!ns.isEmpty()&&!ns.equals("/")&&!name.equals(ns)&&!name.startsWith(ns+"/"))continue;
   if(!fold(kind).contains(t)||!searchable(topic).contains(q))continue;
   String key=identity(topic);if(favoritesOnly.isChecked()&&!favorites.contains(key))continue;
   visible++;if(visible>200)continue;TextView text=new TextView(getContext());text.setText(name+"\n"+kind+"\n"+topic.optInt("publishers")+" pub · "+topic.optInt("subscribers")+" sub");text.setTextColor(0xffe6edf3);rows.addView(text);
   CheckBox star=new CheckBox(getContext());star.setTag("favorite:"+key);star.setText("Favorito "+name);star.setTextColor(0xffe6edf3);star.setChecked(favorites.contains(key));rows.addView(star);
   Button more=new Button(getContext());more.setTag("details:"+key);more.setText("Detalhes / QoS · "+name);rows.addView(more);
   more.setOnClickListener(v->{detailKey=key;renderDetails();});
   Button read=new Button(getContext());read.setTag("read:"+key);read.setText(topic.optBoolean("supported")?"Ler · "+name:"Sem decodificador · "+name);read.setEnabled(topic.optBoolean("supported"));rows.addView(read);
   read.setOnClickListener(v->listener.read(topic));
   text.setContentDescription("Tópico "+name);text.setOnClickListener(v->{detailKey=key;renderDetails();if(topic.optBoolean("supported"))listener.read(topic);});
   star.setOnCheckedChangeListener((b,on)->{
    if(on&&favorites.size()>=256){b.setChecked(false);Toast.makeText(getContext(),"Limite de 256 favoritos locais",Toast.LENGTH_SHORT).show();return;}
    if(on)favorites.add(key);else favorites.remove(key);
    prefs.edit().putStringSet("favorites",new java.util.HashSet<>(favorites)).apply();render();
   });
  }
  summary.setText(visible==0?"Nenhum tópico corresponde aos filtros (ou descoberta vazia).":Math.min(visible,200)+" de "+visible+" tópicos correspondentes"+(visible>200?" · refine a busca para acessar os demais":""));
  renderDetails();
 }
 private void renderDetails(){
  if(detailKey.isEmpty())return;
  JSONObject topic=null;
  for(int i=0;i<topics.length();i++){JSONObject candidate=topics.optJSONObject(i);if(candidate!=null&&identity(candidate).equals(detailKey)){topic=candidate;break;}}
  if(topic==null){details.setText("Tópico não está anunciado nesta sessão. Aguardando redescoberta.");return;}
  StringBuilder text=new StringBuilder(topic.optString("name")).append("\n").append(topic.optString("type"));
  text.append("\nQoS anunciado: oferecido pelo publicador / solicitado pelo assinante.");
  JSONArray endpoints=topic.optJSONArray("endpoints");
  if(endpoints==null||endpoints.length()==0)text.append("\nSem detalhes de endpoints anunciados.");
  else for(int i=0;i<Math.min(128,endpoints.length());i++){
   JSONObject e=endpoints.optJSONObject(i);if(e==null)continue;
   text.append("\n\n").append(e.optBoolean("writer")?"PUBLICADOR (oferecido)":"ASSINANTE (solicitado)");
   text.append("\nGID: ").append(e.optString("id","desconhecido"));
   text.append("\nParticipante DDS (não é nó): ").append(e.optString("participant","desconhecido"));
   JSONArray names=e.optJSONArray("nodes");text.append("\nNó(s) por GID: ");
   if(names==null||names.length()==0)text.append("desconhecido — grafo sem associação confirmada");
   else for(int j=0;j<names.length();j++){if(j>0)text.append(", ");text.append(names.optString(j));}
   JSONObject qos=e.optJSONObject("qos");
   if(qos!=null){java.util.Iterator<String> keys=qos.keys();while(keys.hasNext()){String key=keys.next();text.append("\n").append(key).append(": ").append(qos.opt(key));}}
   else text.append("\nQoS desconhecido");
   JSONObject compatibility=e.optJSONObject("compatibility");
   if(e.optBoolean("writer")){
    text.append("\nOferta × perfil do Watch 1 (não do watch selecionado; padrão best_effort / volatile até assinar; depois, último perfil enviado ao criar leitor, mesmo se falhar): ").append(compatibility==null?"unknown":compatibility.optString("state","unknown"));
    if(compatibility!=null)text.append(" ").append(compatibility.optJSONArray("reasons"));
   }else text.append("\nQoS solicitado por este assinante não é uma oferta para o app.");
  }
  if(endpoints!=null&&endpoints.length()>128)text.append("\nExibindo 128 de ").append(endpoints.length()).append(" endpoints; refine a inspeção na bancada.");
  text.append("\n\nunknown = sem incompatibilidade comprovada, NÃO garantia de entrega. Reliability não resolve deadline, liveliness, ownership, partições, tipo ou rede. Durações em ns; -1 = infinito; -2 = desconhecido (não anunciado).");
  details.setText(text.toString());
 }
}
