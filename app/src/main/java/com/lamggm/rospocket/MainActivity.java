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
 private final PlotController plots=new PlotController();
 private final QaSnapshot qa=new QaSnapshot();
 private PlotView plotView;
 private StructuredMessageView structured;
 private final StructuredMessageView[] watchStructured=new StructuredMessageView[4];
 private RosoutConsoleView rosoutConsole;
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
 private volatile long workerRosoutEpoch=0;
 private volatile long rosoutEpoch=0;
 private String subscriptionFailure="",binding="";
 private final WatchSlots watchSlots=new WatchSlots();
 private final TextView[] watchText=new TextView[4];
 private int activeWatch=0;
 private boolean switchingWatch=false;
 private TextView watchEditorTitle;
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
 private final LinearLayout[] sections=new LinearLayout[4];
 private final Button[] sectionButtons=new Button[4];
 private void showSection(int selected){
  for(int i=0;i<sections.length;i++){
   sections[i].setVisibility(i==selected?View.VISIBLE:View.GONE);
   sectionButtons[i].setSelected(i==selected);
  }
 }
 private void makeUi(){
  LinearLayout root=column();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(18),dp(18),dp(12));
  root.setOnApplyWindowInsetsListener((v,insets)->{
   if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(18)+i.left,dp(12)+i.top,dp(18)+i.right,dp(12)+i.bottom);}
   else v.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),dp(12)+insets.getSystemWindowInsetTop(),dp(18)+insets.getSystemWindowInsetRight(),dp(12)+insets.getSystemWindowInsetBottom());
   return insets;
  });
  root.addView(label("ROS Pocket",29,FG));
  TextView subtitle=label("v"+BuildConfig.VERSION_NAME+" · DDS direto · SOMENTE LEITURA",13,ACCENT);subtitle.setPadding(0,0,0,dp(8));root.addView(subtitle);
  String[] sectionNames={"connection","topics","monitor","logs"};String[] sectionLabels={"Conexão","Tópicos","Monitor","Logs"};
  LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
  for(int i=0;i<sectionNames.length;i++){
   final int selected=i;Button button=new Button(this);button.setId(new int[]{R.id.nav_connection,R.id.nav_topics,R.id.nav_monitor,R.id.nav_logs}[i]);button.setText(sectionLabels[i]);button.setAllCaps(false);button.setContentDescription("Abrir seção "+sectionLabels[i]);button.setOnClickListener(v->showSection(selected));sectionButtons[i]=button;nav.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));
  }
  root.addView(nav);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout body=column();scroll.addView(body);
  for(int i=0;i<sectionNames.length;i++){
   sections[i]=column();sections[i].setId(new int[]{R.id.section_connection,R.id.section_topics,R.id.section_monitor,R.id.section_logs}[i]);body.addView(sections[i]);
  }
  LinearLayout connection=sections[0];
  LinearLayout row=new LinearLayout(this);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.addView(label("Domain ID",15,MUTED));
  domain=input("0–101",prefs.getString("domain","0"),R.id.domain,InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
  LinearLayout.LayoutParams domainParams=new LinearLayout.LayoutParams(dp(100),dp(54));domainParams.setMargins(dp(12),0,dp(12),0);row.addView(domain,domainParams);
  connect=new Button(this);connect.setId(R.id.connect);connect.setText("Conectar");connect.setTextColor(BG);connect.setBackgroundTintList(ColorStateList.valueOf(ACCENT));row.addView(connect,new LinearLayout.LayoutParams(dp(154),dp(52)));
  connect.setOnClickListener(v->{if(connectWanted)disconnect();else requestConnection(true);});connection.addView(row);
  peer=input("Peer IPv4 opcional — deixe vazio para descoberta automática",prefs.getString("peer",""),R.id.peer,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);peer.setTextSize(14);connection.addView(peer,new LinearLayout.LayoutParams(-1,dp(48)));
  status=label("Desconectado. Use o mesmo Domain ID e Wi-Fi do ROS 2.",14,MUTED);status.setId(R.id.status);status.setPadding(0,dp(6),0,dp(12));connection.addView(status);
  LinearLayout diagnosticCard=card();diagnosticCard.addView(label("CONEXÃO",14,ACCENT));
  diagnostics=label("Rede indisponível · DDS peers: — · Nós ROS: —",13,MUTED);diagnostics.setId(R.id.diagnostics);diagnosticCard.addView(diagnostics);connection.addView(diagnosticCard);
  LinearLayout nodeCard=card();nodeCard.addView(label("NÓS ROS 2",14,ACCENT));nodes=label("Aguardando ros_discovery_info…",14,MUTED);nodes.setId(R.id.nodes);nodes.setTypeface(Typeface.MONOSPACE);nodes.setPadding(0,dp(8),0,0);nodeCard.addView(nodes);connection.addView(nodeCard);
  TextView footer=label("Sem rosbridge · sem servidor auxiliar · sem comandos de movimento.\nBloqueio de multicast, isolamento do AP e firewall podem impedir descoberta.",12,MUTED);footer.setPadding(0,dp(12),0,dp(8));connection.addView(footer);
  LinearLayout graphCard=card();topicTitle=label("TÓPICOS · 0",14,ACCENT);graphCard.addView(topicTitle);
  topicList=new ExplorerView(this,this::select);topicList.setId(R.id.topics);graphCard.addView(topicList);sections[1].addView(graphCard);
  LinearLayout echo=card();selectedTitle=label("LEITURA · Watch 1 · selecione um tópico acima",16,ACCENT);echo.addView(selectedTitle);
  echo.addView(label("WATCHES · até quatro · escolha o slot e depois um tópico no Explorer",13,ACCENT));
  LinearLayout slots=new LinearLayout(this);
  for(int i=0;i<4;i++){
   final int slot=i;Button button=new Button(this);button.setId(new int[]{R.id.watch_select_0,R.id.watch_select_1,R.id.watch_select_2,R.id.watch_select_3}[i]);button.setTag("watch-select:"+i);button.setText("Watch "+(i+1));
   button.setContentDescription("Selecionar Watch "+(i+1));button.setOnClickListener(v->chooseWatch(slot));slots.addView(button,new LinearLayout.LayoutParams(0,dp(52),1));
  }
  echo.addView(slots);
  watchEditorTitle=label("Editando Watch 1 · QoS e seleção abaixo/no Explorer",14,ACCENT);watchEditorTitle.setId(R.id.watch_editor);watchEditorTitle.setTag("watch-editor");echo.addView(watchEditorTitle);
  Button remove=new Button(this);remove.setId(R.id.watch_remove);remove.setTag("watch-remove");remove.setText("Remover watch selecionado");remove.setOnClickListener(v->removeSelectedWatch());echo.addView(remove);
  TextView supported=label("Decodificadores: std_msgs String, Bool, Int32, Int64, Float32 e Float64; geometry_msgs Vector3, Quaternion, Twist e Pose; nav_msgs Odometry. Campos numéricos selecionáveis abaixo. Outros tipos permanecem não suportados.",12,MUTED);supported.setPadding(0,dp(8),0,dp(8));echo.addView(supported);
  LinearLayout qos=new LinearLayout(this);qos.setOrientation(LinearLayout.HORIZONTAL);
  reliable=new CheckBox(this);reliable.setId(R.id.qos_reliable);reliable.setText("Reliable");reliable.setTextColor(FG);qos.addView(reliable);
  transientLocal=new CheckBox(this);transientLocal.setId(R.id.qos_transient);transientLocal.setText("Transient local");transientLocal.setTextColor(FG);qos.addView(transientLocal);echo.addView(qos);
  reliable.setChecked(reliableValue);transientLocal.setChecked(transientValue);
  reliable.setOnCheckedChangeListener((b,on)->{if(switchingWatch)return;if(activeWatch>0){updateExtraQos();return;}reliableValue=on;prefs.edit().putBoolean("reliable",on).apply();resubscribe();});
  transientLocal.setOnCheckedChangeListener((b,on)->{if(switchingWatch)return;if(activeWatch>0){updateExtraQos();return;}transientValue=on;prefs.edit().putBoolean("transient",on).apply();resubscribe();});
  TextView qoshint=label("Padrão: best effort / volatile — recebe publicadores reliable e best effort. Hz = taxa recebida, não garantia da taxa de origem.",12,MUTED);echo.addView(qoshint);
  counts=label("Sem assinatura ativa",14,MUTED);counts.setId(R.id.counts);counts.setTag("watch-state:0");counts.setPadding(0,dp(12),0,dp(8));echo.addView(counts);
  message=label("Nenhuma mensagem recebida.",17,FG);message.setId(R.id.message);message.setTypeface(Typeface.MONOSPACE);message.setTextIsSelectable(true);message.setMinHeight(dp(90));echo.addView(message);structured=new StructuredMessageView(this);watchStructured[0]=structured;structured.setTag("structuredMessage");echo.addView(structured);
  for(int i=1;i<4;i++){
   watchText[i]=label("Watch "+(i+1)+" · sem assinatura",14,MUTED);watchText[i].setId(new int[]{R.id.watch_state_1,R.id.watch_state_2,R.id.watch_state_3}[i-1]);watchText[i].setTag("watch-state:"+i);watchText[i].setPadding(0,dp(8),0,dp(8));echo.addView(watchText[i]);
   watchStructured[i]=new StructuredMessageView(this);watchStructured[i].setTag("structuredMessage:"+i);echo.addView(watchStructured[i]);
  }
  sections[2].addView(echo);
  plotView=new PlotView(this,plots);sections[2].addView(plotView);
  LinearLayout logCard=card();rosoutConsole=new RosoutConsoleView(this,this::clearRosout);logCard.addView(rosoutConsole);sections[3].addView(logCard);
  showSection(0);
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
  DeviceTestHooks.invalidate();
  try{
   int d=InputRules.domain(domain.getText().toString());String p=peer.getText().toString().trim();
   if(!NetworkRules.validPeer(p))throw new IllegalArgumentException("Peer opcional: IPv4 sem porta.");
   ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(domain.getWindowToken(),0);
   domain.clearFocus();peer.clearFocus();
   foregroundActivity=new WeakReference<>(this);policy.reset();
   qa.invalidate();
   if(resetSelection){selectedWire="";selectedType="";watchSlots.request(0,"","",reliableValue,transientValue);topicList.update(new JSONArray());}
   requested=new Config(d,p);connectWanted=true;plots.reset(policy.generation());
   inactive("RECONECTANDO · aguardando dados desta sessão");
   extrasInactive("RECONECTANDO · aguardando dados desta sessão");
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
  DeviceTestHooks.cancelOwner(this);DeviceTestHooks.invalidate();
  qa.invalidate();
  plots.reset(policy.generation());
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
  if(plotView!=null)plotView.refresh();
  structured.update(null,false);
  counts.setText(state);counts.setTextColor(WARNING);message.setTextColor(MUTED);
  message.setText("Aguardando nova mensagem desta sessão.");
 }
 private void extrasInactive(String state){
  for(int i=1;i<4;i++)if(watchText[i]!=null){watchStructured[i].update(null,false);watchText[i].setText("Watch "+(i+1)+" · "+state);watchText[i].setTextColor(WARNING);}
 }
 private void unavailable(String state,String error){
  extrasInactive(state);
  inactive(state);status.setText(error);status.setTextColor(WARNING);
  rosoutConsole.clearNow();
  diagnostics.setText("Rede indisponível · DDS peers: — · Nós ROS: —");
  topicList.update(new JSONArray());topicTitle.setText("TÓPICOS · 0");nodes.setText("Grafo indisponível nesta conexão");lastNodes="";
 }
 private static long monotonicMillis(){return System.nanoTime()/1_000_000L;}
 private void tick(int token){
  if(foregroundActivity.get()!=this||!resumed||generation!=token||!connectWanted||requested==null)return;
  final Config config=requested;
  // Fence desired requests before identity() can block on a UI policy change.
  final WatchSlots.Frame[] beforeIdentity=watchSlots.frames();
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
    workerConfig=config;workerAddress=address;workerInterface=iface;nativeRunning=true;workerRosoutEpoch=rosoutEpoch;binding=bound?"Wi-Fi vinculada":"rota padrão (vínculo recusado)";
    subscriptionFailure="";lastUi=0;watchSlots.resetApplied();
    if(!selectedWire.isEmpty()&&policy.accepts(epoch))subscribeSelected(watchSlots.get(0),epoch);
    else if(selectedWire.isEmpty())appliedReaderEpoch=epoch; // New session has no primary reader to await.
    for(int i=1;i<4;i++){WatchSlots.Request r=watchSlots.get(i);if(!r.wire.isEmpty())applyExtra(r);}
   }
   // Peer absence or a failed reader never tears down/rebuilds this healthy participant.
   // Tag the snapshot with the clear actually applied on this DDS worker,
   // not a newer UI request whose native clear may still be queued.
   final long consoleEpoch=workerRosoutEpoch;
   long capturedNanos=System.nanoTime();
   final WatchSlots.Frame[] frames=watchSlots.capture(beforeIdentity);
   final boolean pendingReader=appliedReaderEpoch!=epoch;
   final String failure=subscriptionFailure;
   final long diagnosticRevision=DeviceTestHooks.revision();
   JSONObject snapshot=NativeBridge.Api.json(bridge.poll());
   JSONObject nativeDiagnostic=NativeBridge.Api.json(bridge.diagnosticState());
   if(!snapshot.optBoolean("running"))throw new IllegalStateException(snapshot.optString("error","Sessão DDS inativa"));
   policy.recovered();
   final String networkDisplay="Interface: "+iface+" · IPv4: "+address+"\nRede: Wi-Fi · "+binding+" · domínio "+config.domain;
   // Intake runs for every real poll, regardless of display throttling/pause.
   // Serialize the final epoch check against UI reader replacement/teardown.
   synchronized(policy){
    if(policy.accepts(epoch)&&resumed&&generation==token&&connectWanted&&requested==config){
     synchronized(watchSlots){
      plots.acceptNative(snapshot,epoch,watchSlots,frames);
      if(BuildConfig.DEBUG){qa.publish(snapshot,watchSlots,frames,plots,token,epoch,capturedNanos);
       DeviceTestHooks.publish(diagnosticRevision,nativeDiagnostic,snapshot,capturedNanos,token,epoch,frames,this,config);}

     }
    }
   }
   long paint=System.nanoTime();
   if(lastUi==0||paint-lastUi>=1_500_000_000L){lastUi=paint;ui(token,epoch,()->{if(connectWanted&&requested==config){wifiDisplay=address;render(snapshot,config,networkDisplay,failure,capturedNanos,pendingReader);renderExtras(snapshot,frames,capturedNanos);if(consoleEpoch==rosoutEpoch&&consoleEpoch==workerRosoutEpoch)rosoutConsole.update(snapshot.optJSONObject("rosout"));}});}
  }catch(Throwable ex){
   String e=ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage());
   try{stopSession();}catch(Throwable cleanup){android.util.Log.w("RosPocket","DDS teardown: "+cleanup.getClass().getSimpleName());}
   if(foregroundActivity.get()!=this||!resumed||generation!=token||!connectWanted||requested!=config)return;
   policy.invalidate();long epoch=policy.generation();policy.failed(monotonicMillis(),e);
   final String failedNetwork=details;
   ui(token,epoch,()->{if(connectWanted&&requested==config){unavailable("ERRO · dados indisponíveis","Falha DDS: "+e+" · nova tentativa em até "+policy.delayMillis()/1000+" s");diagnostics.setText(failedNetwork+"\nDDS peers: — · Nós ROS: —");}});
  }
 }
 @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter writer,String[] args){
  if(args!=null&&args.length==1&&"--pocket-qa-v1".equals(args[0])){
   if(!BuildConfig.DEBUG){writer.println("ROS Pocket QA unavailable");return;}
   try{
    long now=System.nanoTime();
    JSONObject normal=qa.export(now),diagnostic=DeviceTestHooks.export(now);
    if(diagnostic.opt("valid")==Boolean.TRUE){
     for(String[] keys:new String[][]{{"capturedMonotonicNs","capturedMonotonicNs"},{"sessionGeneration","sessionGeneration"},{"lifecycleGeneration","lifecycle"},{"policyGeneration","policy"}})
      if(!(normal.opt(keys[0]) instanceof Number)||normal.optLong(keys[0],-1)!=diagnostic.optLong(keys[1],-2))diagnostic.put("valid",false);
    }
    JSONObject out=normal.put("plotDisplay",plotView.qaState(now)).put("foreground",resumed&&foregroundActivity.get()==this)
     .put("testHooks",diagnostic)
     .put("debuggable",(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0).put("applicationId",BuildConfig.APPLICATION_ID)
     .put("versionName",BuildConfig.VERSION_NAME).put("versionCode",BuildConfig.VERSION_CODE)
     .put("pid",android.os.Process.myPid());
    String data=out.toString();
    if(data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>QaSnapshot.MAX_JSON_BYTES){writer.println("ROS Pocket QA unavailable");return;}
    writer.println("ROS_POCKET_QA_V1_BEGIN");writer.println(data);writer.println("ROS_POCKET_QA_V1_END");
   }catch(JSONException ex){writer.println("ROS Pocket QA unavailable");}
   return;
  }
  super.dump(prefix,fd,writer,args);
 }
 private void clearRosout(){
  final long clearEpoch=++rosoutEpoch;final Config clearConfig=requested;final int clearGeneration=generation;
  rosoutConsole.clearNow();
  if(worker!=null)worker.execute(()->{
   if(foregroundActivity.get()!=this||!resumed||generation!=clearGeneration||requested!=clearConfig)return;
   if(nativeRunning&&workerConfig==clearConfig)bridge.clearRosout();
   else if(nativeRunning)return;
   workerRosoutEpoch=clearEpoch;
  });
 }
 private void subscribeSelected(WatchSlots.Request request,long epoch){subscribeSelected(request,epoch,null);}
 private void subscribeSelected(WatchSlots.Request request,long epoch,DeviceTestHooks.Ticket ticket){
  String failure;
  DeviceTestHooks.Acquisition acquired=null;
  try{
   synchronized(policy){synchronized(watchSlots){if(ticket!=null&&(!watchSlots.current(request)||!resumed||foregroundActivity.get()!=this||nativeOwner.get()!=this))throw new IllegalStateException("stale native owner");acquired=DeviceTestHooks.before(ticket,this,request,generation,requested,policy.generation());}}
   JSONObject result=NativeBridge.Api.json(bridge.subscribe(request.wire,request.type,request.reliable,request.transientLocal));
   failure=result.optBoolean("ok")?"":result.optString("error","Falha na assinatura");
  }catch(Throwable ex){failure="Falha na assinatura: "+ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage());}
  finally{DeviceTestHooks.after(acquired);}
  synchronized(watchSlots){
   watchSlots.complete(request,failure);
   if(watchSlots.current(request)){subscriptionFailure=failure;appliedReaderEpoch=epoch;}
  }
  lastUi=0;
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
  plotView.refresh();
  String failure=pendingError.isEmpty()?snapshot.optString("error"):pendingError;
  String subscription=snapshot.optString("subscription");
  String subscriptionState=snapshot.optString("subscriptionState","inactive");
  double age=snapshot.optDouble("age",-1);
  double paintDelay=Math.max(0,System.nanoTime()-capturedNanos)/1_000_000_000.0;
  boolean fresh=failure.isEmpty()&&snapshot.optBoolean("readerActive")&&snapshot.optLong("count")>0&&snapshot.optInt("matched")>0&&"receiving".equals(subscriptionState)&&ConnectionPolicy.fresh(snapshot.optBoolean("fresh"),age,capturedNanos,System.nanoTime());
  if(!failure.isEmpty()){
   inactive("ERRO DE ASSINATURA/SESSÃO · último valor não é telemetria atual");
   selectedTitle.setText("LEITURA · Watch 1 · "+(snapshot.optBoolean("readerActive")?subscription+" · leitor anterior preservado":"sem assinatura ativa"));
   status.setText(failure);status.setTextColor(WARNING);
  }else if(pendingReader){
   inactive("RECONECTANDO · aguardando nova assinatura");selectedTitle.setText("LEITURA · Watch 1 · assinatura solicitada pendente");
  }else if(!subscription.isEmpty()&&snapshot.optBoolean("readerActive")){
   selectedTitle.setText("LEITURA · Watch 1 · geração "+snapshot.optLong("readerGeneration")+" · "+subscription);
   if(age>=0)age+=paintDelay;long count=snapshot.optLong("count");int matched=snapshot.optInt("matched");
   String state=fresh?"RECEBENDO":(count==0?"AGUARDANDO":"SEM DADOS RECENTES");
   counts.setText(String.format(Locale.ROOT,"%s · %d msgs · %.1f Hz recebidos\n%d publicador(es) compatível(is) · idade %s\n%s",state,count,snapshot.optDouble("hz"),matched,age<0?"—":String.format(Locale.ROOT,"%.1f s",age),snapshot.optString("qos")));
   counts.setTextColor(fresh?ACCENT:WARNING);message.setTextColor(fresh?FG:MUTED);
   message.setText(count>0?snapshot.optString("last"):"Nenhuma mensagem recebida. Confira publicadores e QoS.");
   structured.update(count>0?snapshot.optJSONObject("message"):null,fresh);
  }else{
   structured.update(null,false);
   selectedTitle.setText("LEITURA · Watch 1 · sem assinatura ativa");
   counts.setText("Sem assinatura ativa");counts.setTextColor(MUTED);message.setTextColor(MUTED);
   message.setText("Nenhuma mensagem recebida.");
  }
 }
 private void select(JSONObject topic){
  if(!connectWanted)return;
  if(!topic.optBoolean("supported")){Toast.makeText(this,"Tipo descoberto, mas ainda sem decodificador: "+topic.optString("type"),Toast.LENGTH_LONG).show();return;}
  if(activeWatch>0){requestExtra(activeWatch,topic.optString("wireName"),topic.optString("wireType"),reliable.isChecked(),transientLocal.isChecked());return;}
  selectedWire=topic.optString("wireName");selectedType=topic.optString("wireType");resubscribe();
 }
 private void resubscribe(){resubscribe(null,null);}
 private DeviceTestHooks.Ticket resubscribe(String stage,Object lease){
  DeviceTestHooks.invalidate();
  if(worker==null||!connectWanted||selectedWire.isEmpty())return null;
  final WatchSlots.Request primary;
  synchronized(watchSlots){primary=watchSlots.request(0,selectedWire,selectedType,reliableValue,transientValue);plots.requestSlot(0);}
  qa.invalidate();
  policy.invalidate();long epoch=policy.generation();inactive("RECONECTANDO · aguardando nova assinatura");
  final Config expected=requested;final int token=generation;
  final DeviceTestHooks.Ticket ticket=DeviceTestHooks.ticket(lease,this,primary,token,expected,epoch,stage);
  worker.execute(()->{
   if(foregroundActivity.get()!=this||!resumed||generation!=token||!policy.accepts(epoch)||!connectWanted||requested!=expected||!nativeRunning||workerConfig!=expected){DeviceTestHooks.reject(ticket);return;}
   try{subscribeSelected(primary,epoch,ticket);
    if(!subscriptionFailure.isEmpty()){String error=subscriptionFailure;ui(token,epoch,()->{inactive("ERRO DE ASSINATURA · aguardando recuperação");status.setText(error);status.setTextColor(WARNING);});}
   }catch(Throwable ex){subscriptionFailure="Falha na assinatura: "+ex.getClass().getSimpleName();String error=subscriptionFailure;ui(token,epoch,()->{inactive("ERRO DE ASSINATURA");status.setText(error);status.setTextColor(WARNING);});}
  });
  return ticket;
 }
 private void disconnect(){
  DeviceTestHooks.cancelOwner(this);DeviceTestHooks.invalidate();
  qa.invalidate();
  extrasInactive("Desconectado · sem assinatura ativa");
  for(int i=0;i<4;i++)watchSlots.request(i,"","",false,false);
  structured.update(null,false);
  policy.reset();plots.reset(policy.generation());plotView.refresh();connectWanted=false;requested=null;prefs.edit().putBoolean("wanted",false).apply();
  // A poll already holding policy/slots may publish after the early invalidation.
  // policy.reset() fences that poll; clear once more before returning to the UI.
  qa.invalidate();
  connect.setText("Conectar");domain.setEnabled(true);peer.setEnabled(true);status.setText("Desconectado · nenhum leitor DDS ativo");status.setTextColor(MUTED);
  topicList.update(new JSONArray());topicTitle.setText("TÓPICOS · 0");nodes.setText("Desconectado");lastNodes="";selectedWire="";selectedType="";selectedTitle.setText("LEITURA · Watch 1 · selecione um tópico acima");counts.setText("Sem assinatura ativa");counts.setTextColor(MUTED);message.setText("Nenhuma mensagem recebida.");message.setTextColor(MUTED);
  diagnostics.setText("Desconectado · DDS peers: — · Nós ROS: —");queueStop();
  rosoutEpoch++;rosoutConsole.clearNow();
 }
 @Override protected void onStop(){
  DeviceTestHooks.cancelOwner(this);DeviceTestHooks.invalidate();
  if(foregroundActivity.get()==this)foregroundActivity=new WeakReference<>(null);
  resumed=false;generation++;policy.invalidate();plots.reset(policy.generation());
  qa.invalidate();
  inactive("OFFLINE · aplicativo fora do primeiro plano");
  extrasInactive("OFFLINE · aplicativo fora do primeiro plano");
  if(ticker!=null){ticker.cancel(false);ticker=null;}
  queueStop();worker=null;
  rosoutEpoch++;rosoutConsole.clearNow();
  super.onStop();
 }
 private void chooseWatch(int slot){
  activeWatch=slot;switchingWatch=true;
  WatchSlots.Request r=watchSlots.get(slot);
  reliable.setChecked(slot==0?reliableValue:r.reliable);transientLocal.setChecked(slot==0?transientValue:r.transientLocal);
  switchingWatch=false;watchEditorTitle.setText("Editando Watch "+(slot+1)+" · QoS e seleção abaixo/no Explorer");
 }
 private void updateExtraQos(){WatchSlots.Request r=watchSlots.get(activeWatch);requestExtra(activeWatch,r.wire,r.type,reliable.isChecked(),transientLocal.isChecked());}
 private void requestExtra(int slot,String wire,String type,boolean r,boolean t){requestExtra(slot,wire,type,r,t,null,null);}
 private DeviceTestHooks.Ticket requestExtra(int slot,String wire,String type,boolean r,boolean t,String stage,Object lease){
  DeviceTestHooks.invalidate();
  WatchSlots.Request next;
  synchronized(watchSlots){next=watchSlots.request(slot,wire,type,r,t);plots.requestSlot(slot);}
  qa.invalidate();
  plotView.refresh();
  watchStructured[slot].update(null,false);
  watchText[slot].setText("Watch "+(slot+1)+" · assinatura pendente");watchText[slot].setTextColor(WARNING);
  final Config expected=requested;final int token=generation;
  final long epoch=policy.generation();
  final DeviceTestHooks.Ticket ticket=DeviceTestHooks.ticket(lease,this,next,token,expected,epoch,stage);
  if(worker!=null)worker.execute(()->{
   if(foregroundActivity.get()==this&&resumed&&generation==token&&connectWanted&&requested==expected&&nativeRunning&&workerConfig==expected&&watchSlots.current(next)&&(ticket==null||policy.accepts(epoch)))applyExtra(next,ticket,epoch);else DeviceTestHooks.reject(ticket);
  });
  return ticket;
 }
 private void applyExtra(WatchSlots.Request r){applyExtra(r,null,policy.generation());}
 private void applyExtra(WatchSlots.Request r,DeviceTestHooks.Ticket ticket,long epoch){
  String failure="";
  DeviceTestHooks.Acquisition acquired=null;
  try{
   synchronized(policy){synchronized(watchSlots){if(ticket!=null&&(!watchSlots.current(r)||!resumed||foregroundActivity.get()!=this||nativeOwner.get()!=this))throw new IllegalStateException("stale native owner");acquired=DeviceTestHooks.before(ticket,this,r,generation,requested,policy.generation());}}
   JSONObject result=NativeBridge.Api.json(r.wire.isEmpty()?bridge.removeWatch(r.slot):bridge.watch(r.slot,r.wire,r.type,r.reliable,r.transientLocal));if(!result.optBoolean("ok"))failure=result.optString("error","Falha na assinatura");}
  catch(Throwable ex){failure="Falha na assinatura: "+ex.getClass().getSimpleName();}
  finally{DeviceTestHooks.after(acquired);}
  watchSlots.complete(r,failure);lastUi=0;
 }
 static ScheduledExecutorService diagnosticWorker(){return DDS_WORKER;}
 DeviceTestHooks.Ticket diagnosticRetry(int slot,String stage,Object lease){
  if(!BuildConfig.DEBUG||!(bridge instanceof NativeBridge.JniApi)||android.os.Looper.myLooper()!=android.os.Looper.getMainLooper()||slot<0||slot>3)throw new IllegalStateException("diagnostic retry unavailable");
  if(slot==0)return resubscribe(stage,lease);
  WatchSlots.Request r=watchSlots.get(slot);if(r.wire.isEmpty())throw new IllegalStateException("no live selection");
  return requestExtra(slot,r.wire,r.type,r.reliable,r.transientLocal,stage,lease);
 }
 private void removeSelectedWatch(){
  DeviceTestHooks.invalidate();
  if(activeWatch>0){requestExtra(activeWatch,"","",false,false);return;}
  selectedWire="";selectedType="";
  final WatchSlots.Request removal;
  synchronized(watchSlots){removal=watchSlots.request(0,"","",false,false);plots.requestSlot(0);}
  qa.invalidate();
  policy.invalidate();long epoch=policy.generation();plotView.refresh();
  structured.update(null,false);
  counts.setText("Sem assinatura ativa");message.setText("Nenhuma mensagem recebida.");
  final int token=generation;final Config expected=requested;
  if(worker!=null)worker.execute(()->{if(foregroundActivity.get()==this&&resumed&&generation==token&&connectWanted&&requested==expected&&policy.accepts(epoch)&&nativeRunning){bridge.removeWatch(0);subscriptionFailure="";watchSlots.complete(removal,"");appliedReaderEpoch=epoch;lastUi=0;}});
 }
 private void renderExtras(JSONObject snapshot,WatchSlots.Frame[] frames,long capturedNanos){
  JSONArray rows=snapshot.optJSONArray("watches");
  for(int i=1;i<4;i++){
   WatchSlots.Frame f=frames[i];if(!watchSlots.current(f.request))continue;
   JSONObject w=rows==null?null:rows.optJSONObject(i);
   String text;boolean fresh=false;
   if(f.pending)text="assinatura pendente";
   else if(!f.error.isEmpty())text="ERRO · "+f.error;
   else if(f.request.wire.isEmpty())text="sem assinatura";
   else if(w==null)text="aguardando snapshot multiwatch";
   else if(!w.optString("error").isEmpty())text="ERRO · "+w.optString("error");
   else{
    double age=w.optDouble("age",-1);fresh=w.optBoolean("readerActive")&&w.optLong("count")>0&&w.optInt("matched")>0&&ConnectionPolicy.fresh(w.optBoolean("fresh"),age,capturedNanos,System.nanoTime());
    if(age>=0)age+=Math.max(0,System.nanoTime()-capturedNanos)/1e9;
    String state=w.optString("subscriptionState","inactive");
    if(!fresh&&"receiving".equals(state))state="SEM DADOS RECENTES";
    text=String.format(Locale.ROOT,"%s · %s\n%d msgs · %.1f Hz recebidos · idade %s · %d compatíveis\n%s\n%s",f.request.wire.substring(2),fresh?"RECEBENDO":state,w.optLong("count"),w.optDouble("hz"),age<0?"—":String.format(Locale.ROOT,"%.1f s",age),w.optInt("matched"),w.optString("qos"),w.optString("last"));
   }
   boolean treeVisible=!f.pending&&f.error.isEmpty()&&!f.request.wire.isEmpty()&&w!=null&&w.optString("error").isEmpty()&&w.optBoolean("readerActive")&&w.optLong("count")>0;
   watchStructured[i].update(treeVisible?w.optJSONObject("message"):null,fresh);
   watchText[i].setText("Watch "+(i+1)+" · geração "+(w==null?"—":Long.toString(w.optLong("generation")))+" · "+text);watchText[i].setTextColor(fresh?ACCENT:WARNING);
  }
 }
}
