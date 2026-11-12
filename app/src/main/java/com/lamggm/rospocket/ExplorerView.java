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
 private final boolean wide;
 private final LinearLayout listPane,detailPane;
 private final Button selectedRead;
 public ExplorerView(Context context,Listener listener) {
  super(context);setOrientation(VERTICAL);this.listener=listener;wide=getResources().getConfiguration().screenWidthDp>=600;
  prefs=context.getSharedPreferences("explorer",Context.MODE_PRIVATE);
  favorites=new java.util.HashSet<>(prefs.getStringSet("favorites",java.util.Collections.emptySet()));
  search=field("Buscar tópico, tipo ou nó",R.id.explorer_search);
  namespace=field("Namespace (ex.: /robot)",R.id.explorer_namespace);
  type=field("Filtro de tipo (ex.: std_msgs/msg)",R.id.explorer_type);
  favoritesOnly=new CheckBox(context);favoritesOnly.setId(R.id.explorer_favorites);favoritesOnly.setText("Somente favoritos locais");favoritesOnly.setTextColor(UiTheme.color(getContext(),0xffe6edf3));addView(favoritesOnly);
  favoritesOnly.setOnCheckedChangeListener((b,on)->render());
  summary=new TextView(context);summary.setTextColor(UiTheme.color(getContext(),0xff9aa9bc));addView(summary);
  rows=new LinearLayout(context);rows.setOrientation(VERTICAL);rows.setId(R.id.explorer_rows);addView(rows);
  removeView(rows);listPane=new LinearLayout(context);listPane.setOrientation(VERTICAL);listPane.setTag("explorer-list-pane");
  ScrollView listScroll=new ScrollView(context);listScroll.setFillViewport(true);listScroll.addView(rows);listPane.addView(listScroll,new LinearLayout.LayoutParams(-1,wide?-1:(int)(460*getResources().getDisplayMetrics().density)));
  detailPane=new LinearLayout(context);detailPane.setOrientation(VERTICAL);detailPane.setPadding(18,12,18,12);detailPane.setTag("explorer-detail-pane");
  Button back=new Button(context);back.setTag("explorer-back");back.setText("Voltar aos tópicos");back.setVisibility(wide?GONE:VISIBLE);back.setOnClickListener(v->{listPane.setVisibility(VISIBLE);detailPane.setVisibility(GONE);});detailPane.addView(back);
  TextView heading=new TextView(context);heading.setText("Inspecionar tópico");heading.setTextSize(22);heading.setTextColor(UiTheme.color(getContext(),0xffe6edf3));detailPane.addView(heading);
  TextView hint=new TextView(context);hint.setText("Toque seleciona metadados. Monitorar ou Visualizar inicia leitura explicitamente.");hint.setTextColor(UiTheme.color(getContext(),0xff9aa9bc));detailPane.addView(hint);
  selectedRead=new VisualButton(context,"primary","add");selectedRead.setTag("explorer-selected-read");selectedRead.setText("Selecione um tópico");selectedRead.setEnabled(false);selectedRead.setOnClickListener(v->readSelected());detailPane.addView(selectedRead);
  details=new TextView(context);details.setId(R.id.explorer_details);details.setTextColor(UiTheme.color(getContext(),0xffe6edf3));details.setTextIsSelectable(true);details.setText("Detalhes: selecione um tópico.");
  ScrollView detailScroll=new ScrollView(context);detailScroll.setFillViewport(true);detailScroll.addView(details);detailPane.addView(detailScroll,new LinearLayout.LayoutParams(-1,wide?0:-2,wide?1:0));
  LinearLayout split=new LinearLayout(context);split.setTag("explorer-split");split.setOrientation(wide?HORIZONTAL:VERTICAL);addView(split,new LinearLayout.LayoutParams(-1,wide?(int)(540*getResources().getDisplayMetrics().density):-2));
  split.addView(listPane,new LinearLayout.LayoutParams(wide?0:-1,wide?-1:-2,wide?1:0));split.addView(detailPane,new LinearLayout.LayoutParams(wide?0:-1,wide?-1:-2,wide?1.2f:0));if(!wide)detailPane.setVisibility(GONE);
  LinearLayout advanced=new LinearLayout(context);advanced.setOrientation(VERTICAL);advanced.setTag("explorer-advanced-filters");advanced.setVisibility(GONE);removeView(namespace);removeView(type);advanced.addView(namespace);advanced.addView(type);
  Button filters=new VisualButton(context,"secondary","explore");filters.setText("Filtros");filters.setContentDescription("Filtros de namespace e tipo");filters.setOnClickListener(v->advanced.setVisibility(advanced.getVisibility()==GONE?VISIBLE:GONE));LinearLayout tools=new LinearLayout(context);tools.setGravity(android.view.Gravity.CENTER_VERTICAL);tools.addView(filters,new LinearLayout.LayoutParams(dp(110),dp(48)));removeView(favoritesOnly);favoritesOnly.setText("Favoritos");favoritesOnly.setTextSize(12);tools.addView(favoritesOnly);addView(tools,1);addView(advanced,2);summary.setTextSize(12);summary.setPadding(0,dp(12),0,dp(12));UiTheme.apply(this);
  TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){} public void onTextChanged(CharSequence s,int start,int before,int count){render();} public void afterTextChanged(Editable e){}};
  search.addTextChangedListener(watcher);namespace.addTextChangedListener(watcher);type.addTextChangedListener(watcher);
 }
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private EditText field(String hint,int id){EditText e=new EditText(getContext());e.setId(id);e.setTag("design-input");e.setHint(hint);e.setSingleLine(true);e.setTextSize(14);e.setPadding(dp(14),dp(10),dp(14),dp(10));e.setMinimumHeight(dp(48));e.setTextColor(UiTheme.color(getContext(),0xffe6edf3));e.setHintTextColor(UiTheme.color(getContext(),0xff9aa9bc));addView(e);return e;}
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
   visible++;if(visible>200)continue;LinearLayout card=new LinearLayout(getContext());card.setOrientation(HORIZONTAL);card.setGravity(android.view.Gravity.CENTER_VERTICAL);card.setTag("explorer-topic:"+key);card.setSelected(key.equals(detailKey));card.setPadding(dp(14),dp(12),dp(6),dp(12));card.setBackground(UiTheme.surface(getContext(),key.equals(detailKey)));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,0,0,dp(10));rows.addView(card,cp);TextView text=new TextView(getContext());text.setText(name+"\n"+(kind.isEmpty()?"Tipo desconhecido":kind)+"\n"+topic.optString("publishers","—")+" pub · "+topic.optString("subscribers","—")+" sub");text.setTextSize(13);text.setMaxLines(3);text.setEllipsize(android.text.TextUtils.TruncateAt.END);text.setTextColor(UiTheme.color(getContext(),0xffe6edf3));card.addView(text,new LinearLayout.LayoutParams(0,-2,1));card.setOnClickListener(v->inspect(key));
   CheckBox star=new CheckBox(getContext());star.setTag("favorite:"+key);star.setText("");star.setContentDescription("Favorito "+name);star.setButtonDrawable(new PocketIcons("star",VisualButton.muted(getContext()),dp(22)));star.setTextColor(UiTheme.color(getContext(),0xffe6edf3));star.setChecked(favorites.contains(key));card.addView(star,new LinearLayout.LayoutParams(dp(48),dp(48)));
   Button more=new VisualButton(getContext(),"icon","chevron");more.setTag("details:"+key);more.setText("Detalhes / QoS · "+name);more.setContentDescription("Inspecionar "+name);card.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));
   more.setOnClickListener(v->inspect(key));
   boolean visual=SensorPacket.supports(topic.optString("wireType"));
   Button read=new VisualButton(getContext(),"icon","add");read.setTag("read:"+key);read.setText(visual?"Visualizar · "+name:topic.optBoolean("supported")?"Ler · "+name:"Sem decodificador · "+name);read.setContentDescription(read.getText());read.setEnabled(visual||topic.optBoolean("supported"));card.addView(read,new LinearLayout.LayoutParams(dp(48),dp(48)));
   read.setOnClickListener(v->listener.read(topic));
   text.setContentDescription("Tópico "+name);text.setOnClickListener(v->inspect(key));
   star.setOnCheckedChangeListener((b,on)->{
    if(on&&favorites.size()>=256){b.setChecked(false);Toast.makeText(getContext(),"Limite de 256 favoritos locais",Toast.LENGTH_SHORT).show();return;}
    if(on)favorites.add(key);else favorites.remove(key);
    prefs.edit().putStringSet("favorites",new java.util.HashSet<>(favorites)).apply();render();
   });
  }
  summary.setText(visible==0?"Nenhum tópico corresponde aos filtros (ou descoberta vazia).":Math.min(visible,200)+" de "+visible+" tópicos correspondentes"+(visible>200?" · refine a busca para acessar os demais":""));
  renderDetails();UiTheme.apply(this);
 }
 private void inspect(String key){detailKey=key;for(int i=0;i<rows.getChildCount();i++){android.view.View row=rows.getChildAt(i);boolean selected=("explorer-topic:"+key).equals(row.getTag());row.setSelected(selected);row.setBackground(UiTheme.surface(getContext(),selected));}renderDetails();if(!wide){listPane.setVisibility(GONE);detailPane.setVisibility(VISIBLE);}UiTheme.apply(this);}
 private void readSelected(){for(int i=0;i<topics.length();i++){JSONObject t=topics.optJSONObject(i);if(t!=null&&identity(t).equals(detailKey)&&(t.optBoolean("supported")||SensorPacket.supports(t.optString("wireType")))){listener.read(t);return;}}}
 boolean containsTopic(String wire,String kind){for(int i=0;i<topics.length();i++){JSONObject t=topics.optJSONObject(i);if(t!=null&&wire.equals(t.optString("wireName"))&&kind.equals(t.optString("wireType")))return true;}return false;}
 private void renderDetails(){
  if(detailKey.isEmpty()){selectedRead.setEnabled(false);return;}
  JSONObject topic=null;
  for(int i=0;i<topics.length();i++){JSONObject candidate=topics.optJSONObject(i);if(candidate!=null&&identity(candidate).equals(detailKey)){topic=candidate;break;}}
  if(topic==null){selectedRead.setEnabled(false);details.setText("Tópico não está anunciado nesta sessão. Aguardando redescoberta.");return;}
  boolean visual=SensorPacket.supports(topic.optString("wireType"));selectedRead.setEnabled(visual||topic.optBoolean("supported"));selectedRead.setText(visual?"Visualizar":"Monitorar");
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
