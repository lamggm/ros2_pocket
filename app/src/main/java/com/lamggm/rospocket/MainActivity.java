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
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.*;

/** Foreground, read-only DDS inspector. No publisher, command or robot-control API. */
public final class MainActivity extends Activity {
 private static final int BG=0xff0d1117,CARD=0xff161d27,FG=0xffe6edf3,MUTED=0xff9aa9bc,ACCENT=0xff48d7bb;
 NativeBridge.Api bridge=new NativeBridge.JniApi();
 private EditText domain,peer;
 private Button connect;
 private CheckBox reliable,transientLocal;
 private TextView status,diagnostics,nodes,message,counts,topicTitle,selectedTitle;
 private static final int WARNING=0xffffa97d;
 private final ConnectionPolicy policy=new ConnectionPolicy();
 private ExplorerView topicList;
 private SharedPreferences prefs;
 private static final ScheduledExecutorService DDS_WORKER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"pocket-dds");t.setDaemon(true);return t;});
 // Foreground identity is read across UI/worker threads; nativeOwner is confined to DDS_WORKER.
 private static volatile WeakReference<MainActivity> foregroundActivity=new WeakReference<>(null);
 private static WeakReference<MainActivity> nativeOwner=new WeakReference<>(null);
 private ScheduledExecutorService worker;
 private ScheduledFuture<?> ticker;
 private WifiManager.MulticastLock multicast;
 private volatile boolean resumed=false,connectWanted=false;
 private volatile Config requested;
 private volatile String selectedWire="",selectedType="";
 private volatile boolean reliableValue=false,transientValue=false;
 private volatile int generation=0;
 private String lastNodes="",wifiDisplay="";
 // Confined to the single DDS executor:
 private boolean nativeRunning=false;
 private Config workerConfig;
 private String workerAddress="",workerInterface="";
 private Network workerNetwork;
 private long lastUi=0,appliedReaderEpoch=-1;
 private String subscriptionFailure="",binding="";
 private static final class Config { final int domain;final String peer;Config(int d,String p){domain=d;peer=p;} }

 @Override public void onCreate(Bundle saved) {
  super.onCreate(saved);
  prefs=getSharedPreferences("connection",MODE_PRIVATE);
  connectWanted=prefs.getBoolean("wanted",false);
  reliableValue=prefs.getBoolean("reliable",false);transientValue=prefs.getBoolean("transient",false);
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
  LinearLayout diagnosticCard=card();diagnosticCard.addView(label("CONEXÃO",14,ACCENT));
  diagnostics=label("Rede indisponível · DDS peers: — · Nós ROS: —",13,MUTED);diagnostics.setId(R.id.diagnostics);diagnosticCard.addView(diagnostics);body.addView(diagnosticCard);
  body.addView(label("",1,MUTED),new LinearLayout.LayoutParams(-1,dp(12)));
  LinearLayout graphCard=card();topicTitle=label("TÓPICOS · 0",14,ACCENT);graphCard.addView(topicTitle);
  topicList=new ExplorerView(this,this::select);topicList.setId(R.id.topics);graphCard.addView(topicList);body.addView(graphCard);
  TextView spacer=label("",1,MUTED);body.addView(spacer,new LinearLayout.LayoutParams(-1,dp(12)));
  LinearLayout echo=card();selectedTitle=label("LEITURA · selecione um tópico acima",16,ACCENT);echo.addView(selectedTitle);
  TextView supported=label("Decodificadores: std_msgs String, Bool, Int32, Int64, Float32 e Float64. Outros tipos aparecem na lista, mas ainda não são decodificados.",12,MUTED);supported.setPadding(0,dp(8),0,dp(8));echo.addView(supported);
  LinearLayout qos=new LinearLayout(this);qos.setOrientation(LinearLayout.HORIZONTAL);
  reliable=new CheckBox(this);reliable.setId(R.id.qos_reliable);reliable.setText("Reliable");reliable.setTextColor(FG);qos.addView(reliable);
  transientLocal=new CheckBox(this);transientLocal.setId(R.id.qos_transient);transientLocal.setText("Transient local");transientLocal.setTextColor(FG);qos.addView(transientLocal);echo.addView(qos);
  reliable.setChecked(reliableValue);transientLocal.setChecked(transientValue);
  reliable.setOnCheckedChangeListener((b,on)->{reliableValue=on;prefs.edit().putBoolean("reliable",on).apply();resubscribe();});
  transientLocal.setOnCheckedChangeListener((b,on)->{transientValue=on;prefs.edit().putBoolean("transient",on).apply();resubscribe();});
  TextView qoshint=label("Padrão: best effort / volatile — recebe publicadores reliable e best effort. Hz = taxa recebida, não garantia da taxa de origem.",12,MUTED);echo.addView(qoshint);
  counts=label("Sem assinatura ativa",14,MUTED);counts.setId(R.id.counts);counts.setPadding(0,dp(12),0,dp(8));echo.addView(counts);
  message=label("Nenhuma mensagem recebida.",17,FG);message.setId(R.id.message);message.setTypeface(Typeface.MONOSPACE);message.setTextIsSelectable(true);message.setMinHeight(dp(90));echo.addView(message);body.addView(echo);
  body.addView(label("",1,MUTED),new LinearLayout.LayoutParams(-1,dp(12)));
  LinearLayout nodeCard=card();nodeCard.addView(label("NÓS ROS 2",14,ACCENT));nodes=label("Aguardando ros_discovery_info…",14,MUTED);nodes.setId(R.id.nodes);nodes.setTypeface(Typeface.MONOSPACE);nodes.setPadding(0,dp(8),0,0);nodeCard.addView(nodes);body.addView(nodeCard);
  TextView footer=label("Sem rosbridge · sem servidor auxiliar · sem comandos de movimento.\nBloqueio de multicast, isolamento do AP e firewall podem impedir descoberta.",12,MUTED);footer.setPadding(0,dp(12),0,dp(8));body.addView(footer);
  setContentView(root);
 }
 @Override protected void onStart(){
  super.onStart();foregroundActivity=new WeakReference<>(this);resumed=true;generation++;
  worker=DDS_WORKER;
  final int token=generation;
  if(connectWanted)requestConnection(false);
  ticker=worker.scheduleWithFixedDelay(()->tick(token),0,500,TimeUnit.MILLISECONDS);
 }
 private void requestConnection(boolean resetSelection){
  try{
   int d=InputRules.domain(domain.getText().toString());String p=peer.getText().toString().trim();
   if(!NetworkRules.validPeer(p))throw new IllegalArgumentException("Peer opcional: IPv4 sem porta.");
   ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(domain.getWindowToken(),0);
   domain.clearFocus();peer.clearFocus();
   foregroundActivity=new WeakReference<>(this);policy.reset();
   if(resetSelection){selectedWire="";selectedType="";topicList.update(new JSONArray());}
   requested=new Config(d,p);connectWanted=true;
   inactive("RECONECTANDO · aguardando dados desta sessão");
   prefs.edit().putString("domain",Integer.toString(d)).putString("peer",p).putBoolean("wanted",true).apply();
   domain.setEnabled(false);peer.setEnabled(false);connect.setText("Desconectar");
   status.setText("Iniciando DDS no domínio "+d+"…");status.setTextColor(MUTED);
  }catch(Exception ex){
   disconnect();status.setText(String.valueOf(ex.getMessage()));status.setTextColor(WARNING);
  }
 }
 private Network wifiNetwork(){
  ConnectivityManager c=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
  for(Network n:c.getAllNetworks()){
   NetworkCapabilities caps=c.getNetworkCapabilities(n);if(caps==null||!NetworkRules.selectableWifi(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)))continue;
   if(!wifiAddress(n).isEmpty())return n;
  }
  return null;
 }
 private String wifiAddress(Network network){
  LinkProperties links=wifiLinks(network);if(links==null)return "";
  for(LinkAddress a:links.getLinkAddresses())if(a.getAddress() instanceof Inet4Address&&!a.getAddress().isLoopbackAddress())return a.getAddress().getHostAddress();
  return "";
 }
 private LinkProperties wifiLinks(Network network){
  return network==null?null:((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).getLinkProperties(network);
 }
 private void acquireMulticast(){
  if(multicast==null){
   WifiManager w=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
   multicast=w.createMulticastLock("ros-pocket-discovery");multicast.setReferenceCounted(false);
  }
  if(!multicast.isHeld())multicast.acquire();
 }
 // All acquisition/teardown uses the same process-wide queue, including across Activity instances.
 private void stopSession(){
  final boolean owns=nativeOwner.get()==this;
  try{if(owns)bridge.stop();}finally{
   nativeRunning=false;workerConfig=null;workerAddress="";
   try{if(owns){nativeOwner=new WeakReference<>(null);((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).bindProcessToNetwork(null);}}
   finally{workerNetwork=null;WifiManager.MulticastLock old=multicast;multicast=null;if(old!=null&&old.isHeld())old.release();}
  }
 }
 private void queueStop(){
  DDS_WORKER.execute(()->{try{stopSession();}catch(Throwable ex){android.util.Log.w("RosPocket","DDS teardown: "+ex.getClass().getSimpleName());}});
 }
 private void ui(int token,long epoch,Runnable action){
  runOnUiThread(()->{if(foregroundActivity.get()==this&&resumed&&generation==token&&policy.accepts(epoch))action.run();});
 }
 private void inactive(String state){
  counts.setText(state);counts.setTextColor(WARNING);message.setTextColor(MUTED);
  message.setText("Aguardando nova mensagem desta sessão.");
 }
 private void unavailable(String state,String error){
  inactive(state);status.setText(error);status.setTextColor(WARNING);
  diagnostics.setText("Rede indisponível · DDS peers: — · Nós ROS: —");
  topicList.update(new JSONArray());topicTitle.setText("TÓPICOS · 0");nodes.setText("Grafo indisponível nesta conexão");lastNodes="";
 }
 private static long monotonicMillis(){return System.nanoTime()/1_000_000L;}
 private void tick(int token){
  if(foregroundActivity.get()!=this||!resumed||generation!=token||!connectWanted||requested==null)return;
  final Config config=requested;
  String details="Rede indisponível · domínio "+config.domain;
  try{
   Network network=wifiNetwork();String address=wifiAddress(network);
   LinkProperties links=wifiLinks(network);String iface=links==null?"":String.valueOf(links.getInterfaceName());
   details="Interface: "+iface+" · IPv4: "+address+"\nRede: Wi-Fi · domínio "+config.domain;
   policy.identity(network+"/"+iface+"/"+address);
   final long epoch=policy.generation();
   if(address.isEmpty()){
    if(nativeRunning||workerNetwork!=null||multicast!=null)stopSession();
    ui(token,epoch,()->unavailable("OFFLINE · último valor não é telemetria atual","Sem IPv4 Wi-Fi. Aguardando reconexão…"));return;
   }
   if(ConnectionPolicy.needsStart(nativeRunning,workerConfig!=config,!address.equals(workerAddress)||!network.equals(workerNetwork)||!iface.equals(workerInterface))){
    if(!policy.due(monotonicMillis()))return;
    ui(token,epoch,()->unavailable("RECONECTANDO · aguardando dados desta sessão",policy.error().isEmpty()?"Iniciando DDS no domínio "+config.domain+"…":policy.error()));
    MainActivity previous=nativeOwner.get();if(previous!=null&&previous!=this)previous.stopSession();
    stopSession();nativeOwner=new WeakReference<>(this);
    acquireMulticast();
    ConnectivityManager manager=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
    boolean bound=manager.bindProcessToNetwork(network);
    // Some VPNs refuse physical-network binding. Preserve the existing LAN-routing fallback.
    if(!bound)manager.bindProcessToNetwork(null);
    workerNetwork=network;
    JSONObject result=NativeBridge.Api.json(bridge.start(config.domain,address,config.peer));
    if(!result.optBoolean("ok"))throw new IllegalStateException(result.optString("error","Falha no DDS"));
    workerConfig=config;workerAddress=address;workerInterface=iface;nativeRunning=true;binding=bound?"Wi-Fi vinculada":"rota padrão (vínculo recusado)";
    subscriptionFailure="";lastUi=0;
    if(!selectedWire.isEmpty()&&policy.accepts(epoch))subscribeSelected(selectedWire,selectedType,reliableValue,transientValue,epoch);
   }
   // Peer absence or a failed reader never tears down/rebuilds this healthy participant.
   long capturedNanos=System.nanoTime();
   JSONObject snapshot=NativeBridge.Api.json(bridge.poll());
   if(!snapshot.optBoolean("running"))throw new IllegalStateException(snapshot.optString("error","Sessão DDS inativa"));
   policy.recovered();
   final String failure=subscriptionFailure;
   final boolean pendingReader=!selectedWire.isEmpty()&&appliedReaderEpoch!=epoch;
   final String networkDisplay="Interface: "+iface+" · IPv4: "+address+"\nRede: Wi-Fi · "+binding+" · domínio "+config.domain;
   long paint=System.nanoTime();
   if(lastUi==0||paint-lastUi>=1_500_000_000L){lastUi=paint;ui(token,epoch,()->{if(connectWanted&&requested==config){wifiDisplay=address;render(snapshot,config,networkDisplay,failure,capturedNanos,pendingReader);}});}
  }catch(Throwable ex){
   String e=ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage());
   try{stopSession();}catch(Throwable cleanup){android.util.Log.w("RosPocket","DDS teardown: "+cleanup.getClass().getSimpleName());}
   if(foregroundActivity.get()!=this||!resumed||generation!=token||!connectWanted||requested!=config)return;
   policy.invalidate();long epoch=policy.generation();policy.failed(monotonicMillis(),e);
   final String failedNetwork=details;
   ui(token,epoch,()->{if(connectWanted&&requested==config){unavailable("ERRO · dados indisponíveis","Falha DDS: "+e+" · nova tentativa em até "+policy.delayMillis()/1000+" s");diagnostics.setText(failedNetwork+"\nDDS peers: — · Nós ROS: —");}});
  }
 }
 private void subscribeSelected(String wire,String type,boolean r,boolean t,long epoch){
  try{
   JSONObject result=NativeBridge.Api.json(bridge.subscribe(wire,type,r,t));
   subscriptionFailure=result.optBoolean("ok")?"":result.optString("error","Falha na assinatura");
  }catch(Throwable ex){subscriptionFailure="Falha na assinatura: "+ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage());}
  appliedReaderEpoch=epoch;lastUi=0;
 }
 private void render(JSONObject snapshot,Config config,String networkDisplay,String pendingError,long capturedNanos,boolean pendingReader){
  JSONArray topics=snapshot.optJSONArray("topics");if(topics==null)topics=new JSONArray();
  topicList.update(topics);topicTitle.setText("TÓPICOS · "+topics.length());
  String connection=snapshot.optString("connectionState","offline");
  String connectionLabel="connected".equals(connection)?"CONECTADO":("reconnecting".equals(connection)?"RECONECTANDO":("discovering".equals(connection)?"DESCOBERTA":"ERRO/OFFLINE"));
  status.setText(connectionLabel+" · domínio "+config.domain+" · Wi-Fi "+wifiDisplay+("discovering".equals(connection)?" · Descoberta em andamento; mesma rede não garante multicast.":""));
  status.setTextColor("connected".equals(connection)?ACCENT:MUTED);
  diagnostics.setText(networkDisplay+"\nDDS peers: "+snapshot.optInt("ddsPeers")+" · Nós ROS: "+snapshot.optInt("rosNodes")+"\nDescoberta DDS não garante assinatura compatível ou mensagens.");
  JSONArray ns=snapshot.optJSONArray("nodes");String nodeSignature=ns==null?"[]":ns.toString();
  if(!nodeSignature.equals(lastNodes)){
   lastNodes=nodeSignature;StringBuilder text=new StringBuilder();
   if(ns!=null)for(int i=0;i<ns.length();i++)text.append(ns.optString(i)).append('\n');
   nodes.setText(text.length()==0?"Sem nós anunciados em ros_discovery_info.\nDescoberta de tópicos e grafo de nós são mecanismos distintos.":text.toString().trim());
  }
  String failure=pendingError.isEmpty()?snapshot.optString("error"):pendingError;
  String subscription=snapshot.optString("subscription");
  String subscriptionState=snapshot.optString("subscriptionState","inactive");
  double age=snapshot.optDouble("age",-1);
  double paintDelay=Math.max(0,System.nanoTime()-capturedNanos)/1_000_000_000.0;
  boolean fresh=failure.isEmpty()&&snapshot.optBoolean("readerActive")&&snapshot.optLong("count")>0&&snapshot.optInt("matched")>0&&"receiving".equals(subscriptionState)&&ConnectionPolicy.fresh(snapshot.optBoolean("fresh"),age,capturedNanos,System.nanoTime());
  if(!failure.isEmpty()){
   inactive("ERRO DE ASSINATURA/SESSÃO · último valor não é telemetria atual");
   selectedTitle.setText("LEITURA · "+(snapshot.optBoolean("readerActive")?subscription+" · leitor anterior preservado":"sem assinatura ativa"));
   status.setText(failure);status.setTextColor(WARNING);
  }else if(pendingReader){
   inactive("RECONECTANDO · aguardando nova assinatura");selectedTitle.setText("LEITURA · assinatura solicitada pendente");
  }else if(!subscription.isEmpty()&&snapshot.optBoolean("readerActive")){
   selectedTitle.setText("LEITURA · "+subscription);
   if(age>=0)age+=paintDelay;long count=snapshot.optLong("count");int matched=snapshot.optInt("matched");
   String state=fresh?"RECEBENDO":(count==0?"AGUARDANDO":"SEM DADOS RECENTES");
   counts.setText(String.format(Locale.ROOT,"%s · %d msgs · %.1f Hz recebidos\n%d publicador(es) compatível(is) · idade %s\n%s",state,count,snapshot.optDouble("hz"),matched,age<0?"—":String.format(Locale.ROOT,"%.1f s",age),snapshot.optString("qos")));
   counts.setTextColor(fresh?ACCENT:WARNING);message.setTextColor(fresh?FG:MUTED);
   message.setText(count>0?snapshot.optString("last"):"Nenhuma mensagem recebida. Confira publicadores e QoS.");
  }else{
   selectedTitle.setText("LEITURA · sem assinatura ativa");
   counts.setText("Sem assinatura ativa");counts.setTextColor(MUTED);message.setTextColor(MUTED);
   message.setText("Nenhuma mensagem recebida.");
  }
 }
 private void select(JSONObject topic){
  if(!connectWanted)return;
  if(!topic.optBoolean("supported")){Toast.makeText(this,"Tipo descoberto, mas ainda sem decodificador: "+topic.optString("type"),Toast.LENGTH_LONG).show();return;}
  selectedWire=topic.optString("wireName");selectedType=topic.optString("wireType");resubscribe();
 }
 private void resubscribe(){
  if(worker==null||!connectWanted||selectedWire.isEmpty())return;
  policy.invalidate();long epoch=policy.generation();inactive("RECONECTANDO · aguardando nova assinatura");
  final String wire=selectedWire,type=selectedType;final Config expected=requested;final int token=generation;
  final boolean r=reliableValue,t=transientValue;
  worker.execute(()->{
   if(foregroundActivity.get()!=this||!resumed||generation!=token||!policy.accepts(epoch)||!connectWanted||requested!=expected||!nativeRunning||workerConfig!=expected)return;
   try{subscribeSelected(wire,type,r,t,epoch);
    if(!subscriptionFailure.isEmpty()){String error=subscriptionFailure;ui(token,epoch,()->{inactive("ERRO DE ASSINATURA · aguardando recuperação");status.setText(error);status.setTextColor(WARNING);});}
   }catch(Throwable ex){subscriptionFailure="Falha na assinatura: "+ex.getClass().getSimpleName();String error=subscriptionFailure;ui(token,epoch,()->{inactive("ERRO DE ASSINATURA");status.setText(error);status.setTextColor(WARNING);});}
  });
 }
 private void disconnect(){
  policy.reset();connectWanted=false;requested=null;prefs.edit().putBoolean("wanted",false).apply();
  connect.setText("Conectar");domain.setEnabled(true);peer.setEnabled(true);status.setText("Desconectado · nenhum leitor DDS ativo");status.setTextColor(MUTED);
  topicList.update(new JSONArray());topicTitle.setText("TÓPICOS · 0");nodes.setText("Desconectado");lastNodes="";selectedWire="";selectedType="";selectedTitle.setText("LEITURA · selecione um tópico acima");counts.setText("Sem assinatura ativa");counts.setTextColor(MUTED);message.setText("Nenhuma mensagem recebida.");message.setTextColor(MUTED);
  diagnostics.setText("Desconectado · DDS peers: — · Nós ROS: —");queueStop();
 }
 @Override protected void onStop(){
  if(foregroundActivity.get()==this)foregroundActivity=new WeakReference<>(null);
  resumed=false;generation++;policy.invalidate();
  inactive("OFFLINE · aplicativo fora do primeiro plano");
  if(ticker!=null){ticker.cancel(false);ticker=null;}
  queueStop();worker=null;
  super.onStop();
 }
}
