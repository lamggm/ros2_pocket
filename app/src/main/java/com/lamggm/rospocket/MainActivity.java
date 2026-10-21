package com.lamggm.rospocket;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.text.InputType;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import org.json.*;
import java.net.Inet4Address;
import java.util.Locale;
import java.util.concurrent.*;

/** Foreground, read-only DDS inspector. No publisher, command or robot-control API. */
public final class MainActivity extends Activity {
 private static final int BG=0xff0d1117,CARD=0xff161d27,FG=0xffe6edf3,MUTED=0xff9aa9bc,ACCENT=0xff48d7bb;
 private EditText domain,peer;
 private Button connect;
 private CheckBox reliable,transientLocal;
 private TextView status,nodes,message,counts,topicTitle,selectedTitle;
 private ExplorerView topicList;
 private SharedPreferences prefs;
 private static final ScheduledExecutorService DDS_WORKER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"pocket-dds");t.setDaemon(true);return t;});
 private ScheduledExecutorService worker;
 private ScheduledFuture<?> ticker;
 private WifiManager.MulticastLock multicast;
 private volatile boolean resumed=false,connectWanted=false;
 private volatile Config requested;
 private volatile String selectedWire="",selectedType="";
 private volatile boolean reliableValue=false,transientValue=false;
 private int generation=0;
 private String lastNodes="",wifiDisplay="";
 // Confined to the single DDS executor:
 private boolean nativeRunning=false;
 private Config workerConfig;
 private String workerAddress="";
 private Network workerNetwork;
 private long lastAttempt=0,lastUi=0;
 private static final class Config { final int domain;final String peer;Config(int d,String p){domain=d;peer=p;} }

 @Override public void onCreate(Bundle saved) {
  super.onCreate(saved);
  prefs=getSharedPreferences("connection",MODE_PRIVATE);
  connectWanted=prefs.getBoolean("wanted",false);
  makeUi();
 }
 private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
 private TextView label(String text,int sp,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(sp);t.setTextColor(color);return t;}
 private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
 private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
 private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(16),dp(14),dp(16),dp(14));l.setBackground(shape(CARD));return l;}
 private EditText input(String hint,String value,int id,int type){
  EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setId(id);e.setSingleLine(true);e.setTextColor(FG);e.setHintTextColor(MUTED);e.setTextSize(16);e.setInputType(type);e.setSelectAllOnFocus(true);return e;
 }
 private void makeUi(){
  LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(18),dp(18),dp(12));
  root.setOnApplyWindowInsetsListener((v,insets)->{
   if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(18)+i.left,dp(12)+i.top,dp(18)+i.right,dp(12)+i.bottom);}
   else v.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),dp(12)+insets.getSystemWindowInsetTop(),dp(18)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());
   return insets;
  });
  root.addView(label("ROS Pocket",29,FG));
  TextView subtitle=label("v0.1 · DDS direto · SOMENTE LEITURA",13,ACCENT);subtitle.setPadding(0,0,0,dp(12));root.addView(subtitle);
  LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);
  row.addView(label("Domain ID",15,MUTED));
  domain=input("0–101",prefs.getString("domain","0"),R.id.domain,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
  LinearLayout.LayoutParams domainParams=new LinearLayout.LayoutParams(dp(100),dp(54));domainParams.setMargins(dp(12),0,dp(12),0);row.addView(domain,domainParams);
  connect=new Button(this);connect.setId(R.id.connect);connect.setText("Conectar");connect.setTextColor(BG);connect.setBackgroundTintList(ColorStateList.valueOf(ACCENT));row.addView(connect,new LinearLayout.LayoutParams(dp(154),dp(52)));
  connect.setOnClickListener(v->{if(connectWanted)disconnect();else requestConnection(true);});root.addView(row);
  peer=input("Peer IPv4 opcional — deixe vazio para descoberta automática",prefs.getString("peer",""),R.id.peer,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);peer.setTextSize(14);root.addView(peer,new LinearLayout.LayoutParams(-1,dp(48)));
  status=label("Desconectado. Use o mesmo Domain ID e Wi-Fi do ROS 2.",14,MUTED);status.setId(R.id.status);status.setPadding(0,dp(6),0,dp(12));root.addView(status);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout body=column();scroll.addView(body);
  LinearLayout graphCard=card();topicTitle=label("TÓPICOS · 0",14,ACCENT);graphCard.addView(topicTitle);
  topicList=new ExplorerView(this,this::select);topicList.setId(R.id.topics);graphCard.addView(topicList);body.addView(graphCard);
  TextView spacer=label("",1,MUTED);body.addView(spacer,new LinearLayout.LayoutParams(-1,dp(12)));
  LinearLayout echo=card();selectedTitle=label("LEITURA · selecione um tópico acima",16,ACCENT);echo.addView(selectedTitle);
  TextView supported=label("Decodificadores: std_msgs String, Bool, Int32, Int64, Float32 e Float64. Outros tipos aparecem na lista, mas ainda não são decodificados.",12,MUTED);supported.setPadding(0,dp(8),0,dp(8));echo.addView(supported);
  LinearLayout qos=new LinearLayout(this);qos.setOrientation(LinearLayout.HORIZONTAL);
  reliable=new CheckBox(this);reliable.setId(R.id.qos_reliable);reliable.setText("Reliable");reliable.setTextColor(FG);qos.addView(reliable);
  transientLocal=new CheckBox(this);transientLocal.setId(R.id.qos_transient);transientLocal.setText("Transient local");transientLocal.setTextColor(FG);qos.addView(transientLocal);echo.addView(qos);
  reliable.setOnCheckedChangeListener((b,on)->{reliableValue=on;resubscribe();});
  transientLocal.setOnCheckedChangeListener((b,on)->{transientValue=on;resubscribe();});
  TextView qoshint=label("Padrão: best effort / volatile — recebe publicadores reliable e best effort. Hz = taxa recebida, não garantia da taxa de origem.",12,MUTED);echo.addView(qoshint);
  counts=label("Sem assinatura ativa",14,MUTED);counts.setId(R.id.counts);counts.setPadding(0,dp(12),0,dp(8));echo.addView(counts);
  message=label("Nenhuma mensagem recebida.",17,FG);message.setId(R.id.message);message.setTypeface(Typeface.MONOSPACE);message.setTextIsSelectable(true);message.setMinHeight(dp(90));echo.addView(message);body.addView(echo);
  body.addView(label("",1,MUTED),new LinearLayout.LayoutParams(-1,dp(12)));
  LinearLayout nodeCard=card();nodeCard.addView(label("NÓS ROS 2",14,ACCENT));nodes=label("Aguardando ros_discovery_info…",14,MUTED);nodes.setId(R.id.nodes);nodes.setTypeface(Typeface.MONOSPACE);nodes.setPadding(0,dp(8),0,0);nodeCard.addView(nodes);body.addView(nodeCard);
  TextView footer=label("Sem rosbridge · sem servidor auxiliar · sem comandos de movimento.\nBloqueio de multicast, isolamento do AP e firewall podem impedir descoberta.",12,MUTED);footer.setPadding(0,dp(12),0,dp(8));body.addView(footer);
  setContentView(root);
 }
 @Override protected void onStart(){
  super.onStart();resumed=true;generation++;
  worker=DDS_WORKER;
  final int token=generation;
  ticker=worker.scheduleWithFixedDelay(()->tick(token),0,500,TimeUnit.MILLISECONDS);
  if(connectWanted)requestConnection(false);
 }
 private void requestConnection(boolean resetSelection){
  try{
   int d=InputRules.domain(domain.getText().toString());String p=peer.getText().toString().trim();
   ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(domain.getWindowToken(),0);
   domain.clearFocus();peer.clearFocus();
   if(!p.isEmpty()&&!p.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IllegalArgumentException("Peer opcional: IPv4 sem porta.");
   if(resetSelection){selectedWire="";selectedType="";topicList.update(new JSONArray());message.setText("Nenhuma mensagem recebida.");}
   requested=new Config(d,p);connectWanted=true;
   counts.setText("RECONECTANDO · aguardando dados desta sessão");counts.setTextColor(MUTED);
   message.setText("Aguardando nova mensagem desta sessão.");
   prefs.edit().putString("domain",Integer.toString(d)).putString("peer",p).putBoolean("wanted",true).apply();
   domain.setEnabled(false);peer.setEnabled(false);connect.setText("Desconectar");
   status.setText("Iniciando DDS no domínio "+d+"…");status.setTextColor(MUTED);
   if(multicast==null){WifiManager w=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);multicast=w.createMulticastLock("ros-pocket-discovery");multicast.setReferenceCounted(false);multicast.acquire();}
  }catch(Exception ex){connectWanted=false;status.setText(ex.getMessage());status.setTextColor(0xffffa97d);}
 }
 private Network wifiNetwork(){
  ConnectivityManager c=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
  for(Network n:c.getAllNetworks()){
   NetworkCapabilities caps=c.getNetworkCapabilities(n);if(caps==null||!NetworkRules.selectableWifi(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)))continue;
   LinkProperties links=c.getLinkProperties(n);if(links==null)continue;
   for(LinkAddress a:links.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress())return n;
  }
  return null;
 }
 private String wifiAddress(Network network){
  if(network==null)return "";
  ConnectivityManager c=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
  LinkProperties links=c.getLinkProperties(network);if(links==null)return "";
  for(LinkAddress a:links.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress())return a.getAddress().getHostAddress();
  return "";
 }
 private void releaseNetwork(){((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).bindProcessToNetwork(null);workerNetwork=null;}
 private void ui(int token,Runnable action){runOnUiThread(()->{if(resumed&&generation==token)action.run();});}
 private void tick(int token){
  if(!resumed||generation!=token||!connectWanted||requested==null)return;
  Config config=requested;
  try{
   Network network=wifiNetwork();String address=wifiAddress(network);
   if(address.isEmpty()){
    if(nativeRunning){NativeBridge.stop();releaseNetwork();nativeRunning=false;}
    ui(token,()->{status.setText("Sem IPv4 Wi-Fi. Aguardando reconexão…");status.setTextColor(0xffffa97d);counts.setText("OFFLINE · último valor não é telemetria atual");});return;
   }
   if(!nativeRunning||workerConfig!=config||!address.equals(workerAddress)||!network.equals(workerNetwork)){
    long now=System.nanoTime();if(now-lastAttempt<2_000_000_000L)return;lastAttempt=now;
    NativeBridge.stop();nativeRunning=false;
    ConnectivityManager manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
    boolean bound=manager.bindProcessToNetwork(network);
    android.util.Log.i("RosPocket","Wi-Fi process binding="+bound+"; interface="+address+"; domain="+config.domain);
    // Some VPNs disallow explicit process bypass. Keep DDS interface selection
    // and allow ordinary LAN routes; never change VPN policy behind the user.
    if(!bound)manager.bindProcessToNetwork(null);
    workerNetwork=network;
    JSONObject result=NativeBridge.json(NativeBridge.start(config.domain,address,config.peer));
    if(!result.optBoolean("ok")){releaseNetwork();String e=result.optString("error","Falha no DDS");ui(token,()->{status.setText(e);status.setTextColor(0xffffa97d);});return;}
    workerConfig=config;workerAddress=address;nativeRunning=true;
    if(!selectedWire.isEmpty()){
     JSONObject subscription=NativeBridge.json(NativeBridge.subscribe(selectedWire,selectedType,reliableValue,transientValue));
     if(!subscription.optBoolean("ok")){String e=subscription.optString("error","Falha na assinatura");ui(token,()->{status.setText(e);status.setTextColor(0xffffa97d);});}
    }
   }
   JSONObject snapshot=NativeBridge.json(NativeBridge.poll());
   long paint=System.nanoTime();
   if(paint-lastUi>=1_500_000_000L){lastUi=paint;ui(token,()->{if(connectWanted&&requested==config){wifiDisplay=address;render(snapshot,config);}});}
  }catch(Throwable ex){
   String e=ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage());
   ui(token,()->{status.setText("Falha DDS: "+e);status.setTextColor(0xffffa97d);});
  }
 }
 private void render(JSONObject snapshot,Config config){
  JSONArray topics=snapshot.optJSONArray("topics");if(topics==null)topics=new JSONArray();
  topicList.update(topics);topicTitle.setText("TÓPICOS · "+topics.length());
  status.setText("DDS ativo · domínio "+config.domain+" · Wi-Fi "+wifiDisplay+(topics.length()==0?"\nDescoberta em andamento; mesma rede não garante multicast.":" · "+topics.length()+" tópicos"));status.setTextColor(topics.length()>0?ACCENT:MUTED);
  JSONArray ns=snapshot.optJSONArray("nodes");String nodeSignature=ns==null?"[]":ns.toString();
  if(!nodeSignature.equals(lastNodes)){
   lastNodes=nodeSignature;StringBuilder text=new StringBuilder();
   if(ns!=null)for(int i=0;i<ns.length();i++)text.append(ns.optString(i)).append('\n');
   nodes.setText(text.length()==0?"Sem nós anunciados em ros_discovery_info.\nDescoberta de tópicos e grafo de nós são mecanismos distintos.":text.toString().trim());
  }
  String subscription=snapshot.optString("subscription");
  if(!subscription.isEmpty()){
   selectedTitle.setText("LEITURA · "+subscription);
   double age=snapshot.optDouble("age",-1);long count=snapshot.optLong("count");int matched=snapshot.optInt("matched");
   String state=count==0?"AGUARDANDO":(age>3?"SEM DADOS RECENTES":"RECEBENDO");
   counts.setText(String.format(Locale.ROOT,"%s · %d msgs · %.1f Hz recebidos\n%d publicador(es) compatível(is) · idade %s\n%s",state,count,snapshot.optDouble("hz"),matched,age<0?"—":String.format(Locale.ROOT,"%.1f s",age),snapshot.optString("qos")));
   counts.setTextColor(count>0&&age<=3?ACCENT:0xffffa97d);
   message.setText(count>0?snapshot.optString("last"):"Nenhuma mensagem recebida. Confira publicadores e QoS.");
  }else{
   selectedTitle.setText("LEITURA · sem assinatura ativa");
   counts.setText("Sem assinatura ativa");counts.setTextColor(MUTED);
   message.setText("Nenhuma mensagem recebida.");
  }
  String e=snapshot.optString("error");if(!e.isEmpty()){status.setText(e);status.setTextColor(0xffffa97d);}
 }
 private void select(JSONObject topic){
  if(!connectWanted)return;
  if(!topic.optBoolean("supported")){Toast.makeText(this,"Tipo descoberto, mas ainda sem decodificador: "+topic.optString("type"),Toast.LENGTH_LONG).show();return;}
  selectedWire=topic.optString("wireName");selectedType=topic.optString("wireType");resubscribe();
 }
 private void resubscribe(){
  if(worker==null||!connectWanted||selectedWire.isEmpty())return;
  final String wire=selectedWire,type=selectedType;final Config expected=requested;final int token=generation;
  final boolean r=reliableValue,t=transientValue;
  worker.execute(()->{
   if(!resumed||!connectWanted||requested!=expected||!nativeRunning)return;
   try{JSONObject result=NativeBridge.json(NativeBridge.subscribe(wire,type,r,t));
    if(!result.optBoolean("ok")){String e=result.optString("error");ui(token,()->{status.setText(e);status.setTextColor(0xffffa97d);});}
   }catch(Throwable ex){ui(token,()->status.setText("Falha na assinatura: "+ex.getClass().getSimpleName()));}
  });
 }
 private void disconnect(){
  connectWanted=false;requested=null;prefs.edit().putBoolean("wanted",false).apply();
  connect.setText("Conectar");domain.setEnabled(true);peer.setEnabled(true);status.setText("Desconectado · nenhum leitor DDS ativo");status.setTextColor(MUTED);
  topicList.update(new JSONArray());topicTitle.setText("TÓPICOS · 0");nodes.setText("Desconectado");lastNodes="";selectedWire="";selectedType="";selectedTitle.setText("LEITURA · selecione um tópico acima");counts.setText("Sem assinatura ativa");message.setText("Nenhuma mensagem recebida.");
  WifiManager.MulticastLock old=multicast;multicast=null;
  if(worker!=null)worker.execute(()->{try{NativeBridge.stop();}finally{releaseNetwork();nativeRunning=false;workerConfig=null;if(old!=null&&old.isHeld())old.release();}});
 }
 @Override protected void onStop(){
  resumed=false;generation++;
  WifiManager.MulticastLock old=multicast;multicast=null;
  if(ticker!=null){ticker.cancel(false);ticker=null;}
  if(worker!=null){worker.execute(()->{try{NativeBridge.stop();}finally{releaseNetwork();nativeRunning=false;workerConfig=null;if(old!=null&&old.isHeld())old.release();}});worker=null;}
  super.onStop();
 }
}
