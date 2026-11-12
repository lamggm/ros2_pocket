package com.lamggm.rospocket;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Touch-first, read-only topology. UI-thread confined; no DDS operations. */
final class RosGraphPanel extends LinearLayout {
 static final int BG=0xff0d1117,FG=0xffe6edf3,MUTED=0xff9aa9bc,PUB=0xff48d7bb,SUB=0xff82b8ff,TOPIC=0xffffc477;
 final Stage stage;
 final GraphPhysics physics=new GraphPhysics();
 final CheckBox physicsToggle;
 final LinearLayout physicsBox,filterBox,graphWorkspace,inspector;
 final EditText namespace;final CheckBox onlyFocus;final Button pinButton;final TextView physicsState;
 final Set<String> pinned=new LinkedHashSet<>();android.app.AlertDialog drawer;
 final Set<String> pinnedNamespaces=new LinkedHashSet<>();
 boolean framePosted;long lastFrameNs;
 private final Choreographer.FrameCallback physicsFrame=now->{framePosted=false;if(!physicsCanRun())return;if(lastFrameNs==0||now-lastFrameNs>=33_000_000L){lastFrameNs=now;advancePhysics();}schedulePhysics();};
 final TextView summary,selection,details;
 final EditText search;
 final CheckBox infrastructure;
 final Map<String,JSONObject> vertices=new LinkedHashMap<>(),edges=new LinkedHashMap<>();
 final Map<String,RectF> positions=new LinkedHashMap<>();
 final Set<String> focused=new HashSet<>(),focusedEdges=new HashSet<>();
 String selected="",signature="",graphState="inactive";
 boolean truncated;
 RosGraphPanel(Context c){
  super(c);setOrientation(VERTICAL);setTag("ros-graph-panel");stage=new Stage(c);
  LinearLayout heading=horizontal();LinearLayout identity=vertical();identity.addView(labelView("Grafo",27,FG));identity.addView(labelView("Topologia anunciada · somente leitura",12,MUTED));heading.addView(identity,new LayoutParams(0,-2,1));Button physicsMenu=action("Física");physicsMenu.setTag("graph-physics-menu");heading.addView(physicsMenu);addGap(heading,14);
  LinearLayout toolbar=horizontal();search=new EditText(c);search.setId(R.id.graph_search);search.setSingleLine(true);search.setTextSize(14);search.setHint("Buscar nó ou tópico · Enter para enquadrar");search.setTag("design-input");search.setPadding(dp(14),0,dp(14),0);toolbar.addView(search,new LayoutParams(0,dp(50),1));Button filtersMenu=action("Filtros");filtersMenu.setTag("graph-filters-menu");LayoutParams fp=new LayoutParams(-2,dp(50));fp.leftMargin=dp(10);toolbar.addView(filtersMenu,fp);addGap(toolbar,12);
  filterBox=vertical();filterBox.setTag("graph-filters");filterBox.setVisibility(GONE);filterBox.setPadding(dp(12),0,dp(12),dp(12));infrastructure=new CheckBox(c);infrastructure.setId(R.id.graph_infrastructure);infrastructure.setText("Mostrar infraestrutura (/rosout, /parameter_events)");infrastructure.setTextSize(13);filterBox.addView(infrastructure);namespace=new EditText(c);namespace.setTag("graph-namespace");namespace.setHint("Namespace, por exemplo /camera");namespace.setSingleLine(true);namespace.setTextSize(13);filterBox.addView(namespace,new LayoutParams(-1,dp(48)));onlyFocus=new CheckBox(c);onlyFocus.setTag("graph-only-focus");onlyFocus.setText("Mostrar só as conexões do selecionado");onlyFocus.setTextSize(13);filterBox.addView(onlyFocus);addView(filterBox);
  physicsBox=vertical();physicsBox.setTag("graph-physics-settings");physicsBox.setVisibility(GONE);physicsBox.setPadding(dp(12),0,dp(12),dp(12));physicsToggle=new CheckBox(c);physicsToggle.setTag("graph-physics-toggle");physicsToggle.setText("Física do layout · não altera a descoberta ROS");physicsToggle.setTextSize(13);physicsToggle.setChecked(true);physicsBox.addView(physicsToggle);physicsToggle.setOnCheckedChangeListener((b,on)->{physics.enabled=on;if(on)schedulePhysics();else stopFrames();updatePhysicsState();});
  slider("Centralização",25,v->physics.centering=v*.00004);slider("Repulsão",20,v->physics.repulsion=v*.05);slider("Força das conexões",31,v->physics.linkStrength=v*.0008);slider("Distância das conexões",33,v->physics.linkDistance=140+v*6);TextView help=labelView("Toque abre detalhes. Arraste um elemento para movê-lo; arraste o fundo para navegar. Pinça ajusta o zoom. A simulação desacelera até parar.",12,MUTED);help.setPadding(0,dp(6),0,dp(12));physicsBox.addView(help);addView(physicsBox);
  LinearLayout legend=horizontal();legend.addView(labelView("○ NS   ● Nó   ◉ Tópico   → Pub/Sub",12,MUTED),new LayoutParams(0,dp(30),1));physicsState=labelView("",11,PUB);physicsState.setTag("graph-physics-state");legend.addView(physicsState);addGap(legend,8);
  graphWorkspace=horizontal();graphWorkspace.setTag("graph-workspace");graphWorkspace.setGravity(Gravity.TOP);FrameLayout canvas=new FrameLayout(c);canvas.setTag("graph-canvas");canvas.addView(stage,new FrameLayout.LayoutParams(-1,-1));graphWorkspace.addView(canvas,new LayoutParams(0,-1,1));stage.setId(R.id.graph_stage);stage.setTag("ros-graph-stage");LinearLayout tools=horizontal();button(tools,"Enquadrar",R.id.graph_fit,()->stage.fit(!selected.isEmpty()));button(tools,"−",R.id.graph_zoom_out,()->stage.zoom(.8f));button(tools,"+",R.id.graph_zoom_in,()->stage.zoom(1.25f));button(tools,"Limpar",R.id.graph_clear,()->select(""));FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(dp(300),dp(48),Gravity.TOP|Gravity.RIGHT);tp.setMargins(dp(8),dp(8),dp(8),0);canvas.addView(tools,tp);
  inspector=vertical();inspector.setTag("graph-inspector");inspector.setVisibility(GONE);inspector.setPadding(dp(16),dp(10),dp(16),dp(12));LinearLayout ih=horizontal();selection=labelView("Selecione um elemento",14,FG);selection.setId(R.id.graph_selection);selection.setMaxLines(2);selection.setEllipsize(TextUtils.TruncateAt.END);ih.addView(selection,new LayoutParams(0,dp(56),1));Button close=action("Fechar");close.setTag("graph-inspector-close");close.setOnClickListener(v->select(""));ih.addView(close);inspector.addView(ih);
  ScrollView scroll=new ScrollView(c);scroll.setFillViewport(true);details=labelView("Setas indicam associações anunciadas, não tráfego nem compatibilidade QoS.",12,FG);details.setId(R.id.graph_details);details.setTag("ros-graph-details");details.setTypeface(Typeface.MONOSPACE);details.setGravity(Gravity.TOP|Gravity.START);details.setTextIsSelectable(true);details.setPadding(0,dp(12),0,dp(16));scroll.addView(details);inspector.addView(scroll,new LayoutParams(-1,0,1));pinButton=action("Fixar posição");pinButton.setTag("graph-pin");pinButton.setOnClickListener(v->togglePin());inspector.addView(pinButton,new LayoutParams(-1,dp(48)));LayoutParams ip=new LayoutParams(dp(330),-1);ip.leftMargin=dp(16);graphWorkspace.addView(inspector,ip);int reserved=getResources().getConfiguration().screenWidthDp<600?430:350;addView(graphWorkspace,new LayoutParams(-1,dp(Math.max(300,Math.min(640,getResources().getConfiguration().screenHeightDp-reserved)))));
  summary=labelView("Desconectado · nenhuma topologia atual",12,MUTED);summary.setId(R.id.graph_summary);summary.setPadding(0,dp(12),0,0);addView(summary);
  filtersMenu.setOnClickListener(v->{filterBox.setVisibility(filterBox.getVisibility()==GONE?VISIBLE:GONE);filtersMenu.setSelected(filterBox.getVisibility()==VISIBLE);});physicsMenu.setOnClickListener(v->{physicsBox.setVisibility(physicsBox.getVisibility()==GONE?VISIBLE:GONE);physicsMenu.setSelected(physicsBox.getVisibility()==VISIBLE);});
  infrastructure.setOnCheckedChangeListener((b,on)->filterChanged());onlyFocus.setOnCheckedChangeListener((b,on)->{select(selected);stage.invalidate();});namespace.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int d){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int before,int count){filterChanged();}});
  search.setOnEditorActionListener((v,action,event)->{find();((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();return true;});applyAppearance();updatePhysicsState();
 }
 LinearLayout horizontal(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(HORIZONTAL);v.setBaselineAligned(false);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 LinearLayout vertical(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(VERTICAL);return v;}
 TextView labelView(String value,int size,int color){TextView v=new TextView(getContext());v.setText(value);v.setTextSize(size);v.setTextColor(UiTheme.color(getContext(),color));v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 Button action(String title){Button b=new VisualButton(getContext(),"secondary",null);b.setText(title);return b;}
 void addGap(View v,int bottom){LayoutParams p=new LayoutParams(-1,-2);p.bottomMargin=dp(bottom);addView(v,p);}
 void slider(String title,int initial,java.util.function.IntConsumer setter){TextView label=labelView(title,12,MUTED);physicsBox.addView(label);SeekBar seek=new SeekBar(getContext());seek.setTag("graph-force:"+title);seek.setMax(100);seek.setProgress(initial);physicsBox.addView(seek,new LayoutParams(-1,dp(48)));seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int value,boolean user){setter.accept(value);label.setText(title+" · "+value+"%");physics.wake();schedulePhysics();updatePhysicsState();}});}
 void updatePhysicsState(){if(physicsState!=null){String text=!physics.enabled?"Física pausada":physics.awake?"Acomodando…":"Em repouso";if(!text.contentEquals(physicsState.getText()))physicsState.setText(text);}}
 void filterChanged(){select(selectionExists(selected)?selected:"");syncPhysics();schedulePhysics();stage.fit(false);}
 void refreshPin(){if(pinButton!=null){boolean group=selectedNamespace(selected)!=null,indirect=!group&&!pinned.contains(selected)&&pinnedNamespaces.contains("ns:"+namespaceOfVertex(selected));boolean fixed=group?pinnedNamespaces.contains(selected):pinned.contains(selected);pinButton.setEnabled((vertices.containsKey(selected)||group)&&!indirect);pinButton.setText(indirect?"Fixado pelo namespace":fixed?"Soltar posição":"Fixar posição");pinButton.setSelected(fixed||indirect);}}
 void applyPins(){for(Map.Entry<String,GraphPhysics.Body> e:physics.bodies.entrySet())e.getValue().pinned=pinned.contains(e.getKey())||pinnedNamespaces.contains("ns:"+namespaceOfVertex(e.getKey()));}
 void togglePin(){if(selectedNamespace(selected)!=null){if(!pinnedNamespaces.add(selected))pinnedNamespaces.remove(selected);}else if(vertices.containsKey(selected)){if(!pinned.add(selected))pinned.remove(selected);}else return;applyPins();physics.wake();refreshPin();schedulePhysics();stage.invalidate();}
 void showInspector(){if(inspector==null)return;if(!graphDisplayed()){closeInspector();return;}if(selected.isEmpty()){closeInspector();return;}refreshPin();boolean wide=(getWidth()>0?getWidth():dp(getResources().getConfiguration().screenWidthDp-136))>=dp(900)&&getResources().getConfiguration().fontScale<1.3f;
  if(wide||!(getContext() instanceof android.app.Activity)){if(drawer!=null){drawer.setOnDismissListener(null);drawer.dismiss();drawer=null;}if(inspector.getParent()!=graphWorkspace){if(inspector.getParent() instanceof ViewGroup)((ViewGroup)inspector.getParent()).removeView(inspector);LayoutParams ip=new LayoutParams(dp(330),-1);ip.leftMargin=dp(16);graphWorkspace.addView(inspector,ip);}inspector.setVisibility(VISIBLE);}
  else {if(drawer!=null&&drawer.isShowing())return;if(inspector.getParent() instanceof ViewGroup)((ViewGroup)inspector.getParent()).removeView(inspector);inspector.setVisibility(VISIBLE);drawer=new android.app.AlertDialog.Builder(getContext()).setView(inspector).create();drawer.setOnDismissListener(d->{if(drawer!=d)return;drawer=null;select("");});drawer.show();UiTheme.dialog(drawer);Window w=drawer.getWindow();if(w!=null){w.setGravity(Gravity.RIGHT);w.setLayout(Math.min(dp(390),getResources().getDisplayMetrics().widthPixels),-1);}}
 }
 void closeInspector(){if(drawer!=null){drawer.setOnDismissListener(null);drawer.dismiss();drawer=null;}if(inspector!=null)inspector.setVisibility(GONE);}
 void applyAppearance(){setBackgroundColor(VisualButton.bg(getContext()));if(stage!=null)stage.setBackgroundColor(VisualButton.bg(getContext()));if(inspector!=null){android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(VisualButton.surface(getContext()));g.setCornerRadius(dp(16));g.setStroke(dp(1),VisualButton.border(getContext()));inspector.setBackground(g);}if(drawer!=null&&drawer.isShowing())UiTheme.dialog(drawer);}

 int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 TextView text(String s,int size,int color){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(UiTheme.color(getContext(),color));addView(v);return v;}
 void button(LinearLayout row,String title,int id,Runnable action){Button b=new Button(getContext());b.setId(id);b.setText(title);b.setAllCaps(false);b.setTextSize(13);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(4),0,dp(4),0);b.setContentDescription(title.equals("+")?"Ampliar grafo":title.equals("−")?"Reduzir grafo":title);b.setOnClickListener(v->action.run());row.addView(b,new LayoutParams(0,dp(48),title.equals("Enquadrar")?1.8f:1));}
 void update(JSONObject snapshot){
  try{
   JSONObject graph=snapshot.optJSONObject("graph");graphState=graph==null?"unconfirmed":graph.optString("state","unconfirmed");
   JSONObject projected=RosGraphModel.project(snapshot);String next=projected.toString()+graphState;
   if(next.equals(signature))return;signature=next;
   vertices.clear();edges.clear();JSONArray vs=projected.getJSONArray("vertices"),es=projected.getJSONArray("edges");truncated=projected.optBoolean("truncated");
   // Bounded drawing; omitted relationships are disclosed, never accepted as a full graph.
   for(int i=0;i<Math.min(160,vs.length());i++){JSONObject v=vs.getJSONObject(i);vertices.put(v.getString("id"),v);}
   for(int i=0;i<Math.min(400,es.length());i++){JSONObject e=es.getJSONObject(i);if(vertices.containsKey(e.getString("from"))&&vertices.containsKey(e.getString("to")))edges.put(e.getString("id"),e);}
   positions.keySet().retainAll(vertices.keySet());pinned.retainAll(vertices.keySet());Set<String> pubs=new HashSet<>();for(JSONObject e:edges.values())if("Publica".equals(e.optString("role")))pubs.add(e.optString("from"));
   for(JSONObject v:vertices.values()){
    String id=v.optString("id");if(positions.containsKey(id))continue;
    float x="topic".equals(v.optString("kind"))?380:pubs.contains(id)?40:720;int row=0;
    while(occupied(x,72+row*100))row++;
    if(physics.enabled){double angle=positions.size()*2.399963229728653;float cx=520,cy=280;int neighbors=0;float sx=0,sy=0;for(JSONObject e:edges.values()){String other=id.equals(e.optString("from"))?e.optString("to"):id.equals(e.optString("to"))?e.optString("from"):"";RectF anchor=positions.get(other);if(anchor!=null){sx+=anchor.centerX();sy+=anchor.centerY();neighbors++;}}double radius=neighbors>0?260:140*Math.sqrt(positions.size());if(neighbors>0){cx=sx/neighbors;cy=sy/neighbors;}float nx=cx+(float)(Math.cos(angle)*radius),ny=cy+(float)(Math.sin(angle)*radius);positions.put(id,new RectF(nx-130,ny-34,nx+130,ny+34));}
    else positions.put(id,new RectF(x,72+row*100,x+260,140+row*100));
   }
   Set<String> liveNamespaces=new HashSet<>();for(String ns:namespaceMembership(vertices.keySet()).values())liveNamespaces.add("ns:"+ns);pinnedNamespaces.retainAll(liveNamespaces);
   syncPhysics();schedulePhysics();boolean first=stage.needsFit;select(selectionExists(selected)?selected:"");if(first)stage.fit(false);
  }catch(JSONException ex){clear("Grafo indisponível · resposta inválida");}
 }
 String namespaceOfVertex(String id){JSONObject v=vertices.get(id);if(v==null||!("node".equals(v.optString("kind"))||"topic".equals(v.optString("kind"))))return "";return GraphNamespaces.namespaceOf(v.optString("label"));}
 Map<String,String> namespaceMembership(Set<String> ids){Map<String,String> result=new LinkedHashMap<>();for(String id:ids){String ns=namespaceOfVertex(id);if(!ns.isEmpty())result.put(id,ns);}return result;}
 Map<String,GraphNamespaces.Circle> namespaceCircles(Set<String> ids){Map<String,double[]> points=new LinkedHashMap<>();for(String id:ids){RectF r=positions.get(id);if(r!=null)points.put(id,new double[]{r.centerX(),r.centerY(),120});}return GraphNamespaces.circles(points,namespaceMembership(ids));}
 GraphNamespaces.Circle selectedNamespace(String key){return key.startsWith("ns:")?namespaceCircles(baseVisibleVertices()).get(key.substring(3)):null;}
 boolean selectionExists(String key){return baseVisibleVertices().contains(key)||edges.containsKey(key)||selectedNamespace(key)!=null;}
 void syncPhysics(){Map<String,double[]> seeds=new LinkedHashMap<>();Set<String> ids=baseVisibleVertices();for(String id:ids){RectF r=positions.get(id);if(r!=null)seeds.put(id,new double[]{r.centerX(),r.centerY()});}List<String[]> connections=new ArrayList<>();for(JSONObject e:edges.values())if(edgeVisible(e,ids))connections.add(new String[]{e.optString("from"),e.optString("to")});physics.sync(seeds,connections);physics.setNamespaces(namespaceMembership(ids));applyPins();}
 void advancePhysics(){physics.step();for(Map.Entry<String,GraphPhysics.Body> e:physics.bodies.entrySet()){RectF r=positions.get(e.getKey());if(r!=null){GraphPhysics.Body b=e.getValue();r.offsetTo((float)b.x-r.width()/2,(float)b.y-r.height()/2);}}stage.invalidate();updatePhysicsState();}
 boolean graphDisplayed(){boolean attached=isAttachedToWindow();View v=this;while(v!=null){if(v.getVisibility()==GONE||(attached&&v.getVisibility()!=VISIBLE))return false;ViewParent p=v.getParent();v=p instanceof View?(View)p:null;}return !attached||getWindowVisibility()==VISIBLE;}
 boolean physicsCanRun(){return physics.enabled&&physics.awake&&!physics.bodies.isEmpty()&&isAttachedToWindow()&&isShown()&&getWindowVisibility()==VISIBLE;}
 void schedulePhysics(){if(!framePosted&&physicsCanRun()){framePosted=true;Choreographer.getInstance().postFrameCallback(physicsFrame);}}
 void stopFrames(){if(framePosted){Choreographer.getInstance().removeFrameCallback(physicsFrame);framePosted=false;}lastFrameNs=0;}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();schedulePhysics();}
 @Override protected void onDetachedFromWindow(){stopFrames();closeInspector();super.onDetachedFromWindow();}
 @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(physics!=null&&stage!=null){if(!graphDisplayed())closeInspector();if(physicsCanRun())schedulePhysics();else stopFrames();}}
 @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(physics!=null&&stage!=null){if(!graphDisplayed())closeInspector();if(physicsCanRun())schedulePhysics();else stopFrames();}}
 boolean occupied(float x,float y){for(RectF r:positions.values())if(r.left==x&&r.top==y)return true;return false;}
 void clear(String reason){stopFrames();physics.clear();stage.releaseDrag();pinned.clear();pinnedNamespaces.clear();closeInspector();vertices.clear();edges.clear();positions.clear();signature="";graphState="inactive";selected="";focused.clear();focusedEdges.clear();stage.needsFit=true;stage.invalidate();summary.setText(reason);selection.setText("Sem topologia atual");details.setText("Nenhuma associação retida de uma sessão anterior.");stage.changed();}
 Set<String> visibleVertices(){Set<String> ids=baseVisibleVertices();if(onlyFocus!=null&&onlyFocus.isChecked()&&!selected.isEmpty())ids.retainAll(focused);return ids;}
 Set<String> baseVisibleVertices(){
  Set<String> ids=new LinkedHashSet<>(vertices.keySet());
  if(!infrastructure.isChecked()){
   Set<String> hidden=new HashSet<>();for(JSONObject v:vertices.values())if("topic".equals(v.optString("kind"))&&Arrays.asList("/rosout","/parameter_events").contains(v.optString("label")))hidden.add(v.optString("id"));ids.removeAll(hidden);
   for(JSONObject v:vertices.values())if(!"topic".equals(v.optString("kind"))){boolean any=false,shown=false;for(JSONObject e:edges.values())if(v.optString("id").equals(e.optString("from"))||v.optString("id").equals(e.optString("to"))){any=true;if(!hidden.contains(e.optString("topic")))shown=true;}if(any&&!shown)ids.remove(v.optString("id"));}
  }
  String ns=namespace==null?"":namespace.getText().toString().trim();if(!ns.isEmpty())ids.removeIf(id->!label(id).startsWith(ns));return ids;
 }
 boolean edgeVisible(JSONObject e,Set<String> ids){return ids.contains(e.optString("from"))&&ids.contains(e.optString("to"));}
 void select(String id){
  selected=id;focused.clear();focusedEdges.clear();Set<String> topics=new HashSet<>(),visible=baseVisibleVertices();
  JSONObject selectedEdge=edges.get(id);
  GraphNamespaces.Circle group=selectedNamespace(id);
  if(group!=null){focused.addAll(group.members);for(JSONObject e:edges.values())if(group.members.contains(e.optString("from"))||group.members.contains(e.optString("to")))topics.add(e.optString("topic"));}
  else if(selectedEdge!=null)topics.add(selectedEdge.optString("topic"));
  else if(vertices.containsKey(id)){
   if("topic".equals(vertices.get(id).optString("kind")))topics.add(id);
   else for(JSONObject e:edges.values())if(e.optString("from").equals(id)||e.optString("to").equals(id))topics.add(e.optString("topic"));
   focused.add(id);
  }
  for(JSONObject e:edges.values())if(topics.contains(e.optString("topic"))&&edgeVisible(e,visible)){focused.add(e.optString("from"));focused.add(e.optString("to"));focusedEdges.add(e.optString("id"));}
  String title=group!=null?"Namespace · "+id.substring(3):selectedEdge!=null?"Conexão · "+selectedEdge.optString("role"):vertices.containsKey(id)?kind(vertices.get(id))+" · "+vertices.get(id).optString("label"):"Toque para explorar · nó → tópico → nó";
  selection.setText(title);StringBuilder d=new StringBuilder();
  if(!id.isEmpty()){
   if(group!=null)d.append("Namespace ").append(id.substring(3)).append("\nAgrupamento visual por nomes confirmados; não é um nó ou endpoint DDS.\n").append(group.members.size()).append(" elementos visíveis.\nPacote de origem não informado pelo backend; nenhum pacote é deduzido do namespace.\n");
   JSONObject v=vertices.get(id);if(v!=null){d.append(v.optString("label")).append('\n');if(v.has("type"))d.append(v.optString("type")).append('\n');if(!v.optString("participant").isEmpty())d.append("Participante DDS: ").append(v.optString("participant")).append('\n');}
   int count=0;for(JSONObject e:edges.values())if(focusedEdges.contains(e.optString("id"))){
    if(count++>=40){d.append("Mais conexões neste foco; refine a seleção.\n");break;}
    d.append(label(e.optString("from"))).append(" → ").append(label(e.optString("to"))).append(" · ").append(e.optString("role")).append('\n');
   }
   if(selectedEdge!=null){JSONArray eps=selectedEdge.optJSONArray("endpoints");if(eps!=null){d.append("Mostrando ").append(eps.length()).append(" de ").append(selectedEdge.optInt("endpointCount",eps.length())).append(" endpoints\n");for(int i=0;i<eps.length();i++){JSONObject ep=eps.optJSONObject(i);if(ep!=null)d.append("Endpoint ").append(ep.optString("id")).append(" · QoS anunciado: ").append(ep.optJSONObject("qos")).append('\n');}}}
   if(count==0)d.append("Sem conexões confirmadas visíveis.\n");
  }
  d.append("Setas = associações anunciadas; não comprovam tráfego nem compatibilidade QoS.");details.setText(d);
  int visibleEdges=0;for(JSONObject e:edges.values())if(edgeVisible(e,visible))visibleEdges++;
  summary.setText(visible.size()+" elementos · "+visibleEdges+" conexões"+(infrastructure.isChecked()?"":" · infraestrutura oculta")+("received".equals(graphState)?"":" · grafo "+graphState+"; nós não confirmados")+(truncated?" · VISÃO PARCIAL (limite 160 elementos / 400 conexões)":""));
  showInspector();stage.invalidate();stage.changed();
 }
 String label(String id){JSONObject v=vertices.get(id);return v==null?"desconhecido":v.optString("label");}
 float radius(JSONObject v){return "package".equals(v.optString("kind"))?30:"topic".equals(v.optString("kind"))?14:18;}
 String shortLabel(String value){int slash=value.lastIndexOf('/');return slash>=0&&slash<value.length()-1?value.substring(slash+1):value;}
 int namespaceColor(String name){int[] colors=UiTheme.dark(getContext())?new int[]{0xff91e9c4,0xff82b8ff,0xffc5a5f7,0xffffc477,0xffffaaa2,0xff88d7de}:new int[]{0xff087f67,0xff245fa8,0xff7452ab,0xff956100,0xffa2453d,0xff287b87};return colors[Math.floorMod(name.hashCode(),colors.length)];}
 String kind(JSONObject v){return "package".equals(v.optString("kind"))?"Pacote (metadado explícito)":"topic".equals(v.optString("kind"))?"Tópico":"unknown".equals(v.optString("kind"))?"Endpoint sem nó confirmado":"Nó";}
 void find(){String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);if(q.isEmpty()){select("");return;}String candidate="";for(String id:visibleVertices()){String name=label(id).toLowerCase(Locale.ROOT);if(name.equals(q)){candidate=id;break;}if(candidate.isEmpty()&&name.contains(q))candidate=id;}if(candidate.isEmpty()){select("");selection.setText("Nenhum nó ou tópico visível corresponde à busca");return;}select(candidate);stage.fit(true);}

 final class Stage extends View {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);final ScaleGestureDetector pinch;
  float scale=1,tx=0,ty=0,downX,downY,lastX,lastY;boolean moved,scaling,needsFit=true;String dragId="";
  final Set<String> heldIds=new LinkedHashSet<>();
  final Map<String,RectF> labelRects=new LinkedHashMap<>();final List<RectF> glyphObstacles=new ArrayList<>();
  final Map<String,Integer> virtualIds=new LinkedHashMap<>();int nextVirtual=1,accessibilityFocus=-1,hovered=-1;
  float glyphRadius(JSONObject v){return Math.max(radius(v),dp("package".equals(v.optString("kind"))?11:"topic".equals(v.optString("kind"))?6:7)/scale);}
  Stage(Context c){super(c);setBackgroundColor(BG);setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("Topologia ROS interativa. Toque para destacar conexões.");pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float old=scale;scale=Math.max(.02f,Math.min(4,scale*d.getScaleFactor()));tx=d.getFocusX()-(d.getFocusX()-tx)*scale/old;ty=d.getFocusY()-(d.getFocusY()-ty)*scale/old;scaling=true;moved=true;invalidate();return true;}});}
  void virtualEvent(int id,int type){if(getParent()==null)return;AccessibilityEvent event=AccessibilityEvent.obtain(type);event.setSource(this,id);event.setPackageName(getContext().getPackageName());event.setClassName("android.widget.Button");getParent().requestSendAccessibilityEvent(this,event);}
  Set<String> interactiveKeys(boolean all){Set<String> ids=all?new LinkedHashSet<>(vertices.keySet()):visibleVertices();Set<String> keys=new LinkedHashSet<>(ids);Collection<String> namespaces=all?new LinkedHashSet<>(namespaceMembership(ids).values()):namespaceCircles(ids).keySet();for(String ns:namespaces)keys.add("ns:"+ns);return keys;}
  boolean accessibilityFocused(String key){Integer id=virtualIds.get(key);return id!=null&&id==accessibilityFocus;}
  void changed(){Set<String> visible=interactiveKeys(false),all=interactiveKeys(true);boolean keepFocus=false,keepHover=false;for(String k:visible){Integer id=virtualIds.get(k);if(id!=null&&id==accessibilityFocus)keepFocus=true;if(id!=null&&id==hovered)keepHover=true;}if(!keepFocus&&accessibilityFocus!=-1){virtualEvent(accessibilityFocus,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);accessibilityFocus=-1;}if(!keepHover&&hovered!=-1){virtualEvent(hovered,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);hovered=-1;}virtualIds.keySet().retainAll(all);for(String id:all)if(!virtualIds.containsKey(id))virtualIds.put(id,nextVirtual++);sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
  @Override public boolean dispatchHoverEvent(MotionEvent e){AccessibilityManager manager=(AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);if(!manager.isEnabled()||!manager.isTouchExplorationEnabled())return super.dispatchHoverEvent(e);if(e.getActionMasked()!=MotionEvent.ACTION_HOVER_ENTER&&e.getActionMasked()!=MotionEvent.ACTION_HOVER_MOVE&&e.getActionMasked()!=MotionEvent.ACTION_HOVER_EXIT)return super.dispatchHoverEvent(e);String key=e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT?"":hit(e.getX(),e.getY());int next=virtualIds.containsKey(key)?virtualIds.get(key):-1;if(next!=hovered){if(hovered!=-1)virtualEvent(hovered,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);hovered=next;if(hovered!=-1)virtualEvent(hovered,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);}return next!=-1||e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT;}
  RectF screenRect(String id){if(id.startsWith("ns:")){GraphNamespaces.Circle g=namespaceCircles(visibleVertices()).get(id.substring(3));if(g==null)return new RectF();return new RectF((float)(g.x-g.r)*scale+tx,(float)(g.y-g.r)*scale+ty,(float)(g.x+g.r)*scale+tx,(float)(g.y+g.r)*scale+ty);}RectF r=positions.get(id);if(r==null)return new RectF();float size=Math.max(dp(24),radius(vertices.get(id))*scale),x=r.centerX()*scale+tx,y=r.centerY()*scale+ty;return new RectF(x-size,y-size,x+size,y+size);}
  void fit(boolean focus){if(getWidth()==0||getHeight()==0){needsFit=true;return;}Set<String> ids=new LinkedHashSet<>(visibleVertices());if(focus)ids.retainAll(focused);if(ids.isEmpty()){needsFit=true;invalidate();return;}Map<String,GraphNamespaces.Circle> groups=namespaceCircles(ids);GraphNamespaces.Circle root=sceneCircle(ids,groups);RectF box=new RectF((float)(root.x-root.r-50),(float)(root.y-root.r-70),(float)(root.x+root.r+50),(float)(root.y+root.r+50));scale=Math.max(.02f,Math.min(2.2f,Math.min((getWidth()-dp(28))/Math.max(1,box.width()),(getHeight()-dp(116))/Math.max(1,box.height()))));tx=getWidth()/2f-box.centerX()*scale;ty=(getHeight()+dp(60))/2f-box.centerY()*scale;needsFit=false;invalidate();}
  GraphNamespaces.Circle sceneCircle(Set<String> ids,Map<String,GraphNamespaces.Circle> groups){RectF bounds=new RectF();boolean any=false;for(String id:ids){RectF r=positions.get(id);if(r==null)continue;RectF box=new RectF(r.centerX()-150,r.centerY()-150,r.centerX()+150,r.centerY()+150);if(!any){bounds.set(box);any=true;}else bounds.union(box);}for(GraphNamespaces.Circle g:groups.values())bounds.union((float)(g.x-g.r-54),(float)(g.y-g.r-54),(float)(g.x+g.r+54),(float)(g.y+g.r+54));double x=bounds.centerX(),y=bounds.centerY(),radius=0;for(String id:ids){RectF r=positions.get(id);if(r!=null)radius=Math.max(radius,Math.hypot(r.centerX()-x,r.centerY()-y)+150);}for(GraphNamespaces.Circle g:groups.values())radius=Math.max(radius,Math.hypot(g.x-x,g.y-y)+g.r+54);return new GraphNamespaces.Circle(x,y,radius+24,Collections.emptySet());}
  boolean drawLabel(Canvas c,String key,String text,float x,float y,float radius,boolean above){
   String title=TextUtils.ellipsize(text,new android.text.TextPaint(pen),dp(key.startsWith("ns:")?170:112)/scale,TextUtils.TruncateAt.MIDDLE).toString();float width=pen.measureText(title),gap=dp(4)/scale;Paint.FontMetrics fm=pen.getFontMetrics();float below=y+radius+gap-fm.ascent,top=y-radius-gap-fm.descent,middle=y-(fm.ascent+fm.descent)/2;
   float[][] candidates={{x-width/2,above?top:below},{x-width/2,above?below:top},{x+radius+gap,middle},{x-radius-gap-width,middle},{x-width/2,top-(fm.descent-fm.ascent)-gap},{x-width/2,below+(fm.descent-fm.ascent)+gap}};
   RectF tools=new RectF((getWidth()-dp(310)-tx)/scale,(dp(4)-ty)/scale,(getWidth()-tx)/scale,(dp(60)-ty)/scale);
   for(float[] candidate:candidates){RectF box=new RectF(candidate[0],candidate[1]+fm.ascent,candidate[0]+width,candidate[1]+fm.descent);RectF screen=new RectF(box.left*scale+tx,box.top*scale+ty,box.right*scale+tx,box.bottom*scale+ty);if(screen.left<dp(3)||screen.top<dp(3)||screen.right>getWidth()-dp(3)||screen.bottom>getHeight()-dp(3))continue;RectF padded=new RectF(box);padded.inset(-dp(2)/scale,-dp(2)/scale);boolean blocked=RectF.intersects(padded,tools);if(!blocked)for(RectF other:glyphObstacles)if(RectF.intersects(padded,other)){blocked=true;break;}if(!blocked)for(RectF other:labelRects.values())if(RectF.intersects(padded,other)){blocked=true;break;}if(blocked)continue;
    labelRects.put(key,padded);pen.setShadowLayer(dp(2)/scale,0,0,VisualButton.bg(getContext()));c.drawText(title,candidate[0],candidate[1],pen);pen.clearShadowLayer();return true;
   }
   return false;
  }
  void drawNamespaces(Canvas c,Set<String> ids){Map<String,GraphNamespaces.Circle> groups=namespaceCircles(ids);if(groups.isEmpty())return;pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(dp(1)/scale);if(groups.size()>1){GraphNamespaces.Circle root=sceneCircle(ids,groups);pen.setColor(VisualButton.border(getContext()));pen.setPathEffect(new DashPathEffect(new float[]{12,9},0));c.drawCircle((float)root.x,(float)root.y,(float)root.r,pen);pen.setPathEffect(null);pen.setStyle(Paint.Style.FILL);pen.setColor(VisualButton.muted(getContext()));pen.setTextSize(Math.max(18,12*getResources().getDisplayMetrics().scaledDensity/scale));drawLabel(c,"layout-root","Topologia ROS",(float)root.x,(float)root.y,(float)root.r,true);}
   for(Map.Entry<String,GraphNamespaces.Circle> e:groups.entrySet()){GraphNamespaces.Circle g=e.getValue();int color=namespaceColor(e.getKey());boolean active=selected.isEmpty()||selected.equals("ns:"+e.getKey())||!Collections.disjoint(g.members,focused);pen.setStyle(Paint.Style.FILL);pen.setColor(color);pen.setAlpha(active?9:4);c.drawCircle((float)g.x,(float)g.y,(float)g.r,pen);pen.setStyle(Paint.Style.STROKE);pen.setColor(color);pen.setAlpha(active?110:45);pen.setStrokeWidth(Math.max(selected.equals("ns:"+e.getKey())?3:1,dp(1)/scale));c.drawCircle((float)g.x,(float)g.y,(float)g.r,pen);pen.setAlpha(255);pen.setStyle(Paint.Style.FILL);pen.setColor(UiTheme.color(getContext(),active?color:MUTED));pen.setTextSize(Math.max(17,12*getResources().getDisplayMetrics().scaledDensity/scale));drawLabel(c,"ns:"+e.getKey(),e.getKey(),(float)g.x,(float)g.y,(float)g.r,true);}
   pen.setAlpha(255);pen.setPathEffect(null);
  }
  void zoom(float factor){float old=scale;scale=Math.max(.02f,Math.min(4,scale*factor));tx=getWidth()/2f-(getWidth()/2f-tx)*scale/old;ty=getHeight()/2f-(getHeight()/2f-ty)*scale/old;invalidate();}
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){if(needsFit)fit(false);else invalidate();}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);labelRects.clear();glyphObstacles.clear();Set<String> visible=visibleVertices();if(visible.isEmpty()){pen.setColor(UiTheme.color(getContext(),MUTED));pen.setTextSize(dp(15));c.drawText(vertices.isEmpty()?"Aguardando topologia anunciada…":"Nenhum elemento corresponde aos filtros",dp(18),getHeight()/2f,pen);return;}for(String id:visible){RectF r=positions.get(id);float radius=glyphRadius(vertices.get(id))+dp(2)/scale;glyphObstacles.add(new RectF(r.centerX()-radius,r.centerY()-radius,r.centerX()+radius,r.centerY()+radius));}
   c.save();c.translate(tx,ty);c.scale(scale,scale);
   drawNamespaces(c,visible);
   for(JSONObject e:edges.values())if(edgeVisible(e,visible)){
    boolean lit=selected.isEmpty()||focusedEdges.contains(e.optString("id"));int color="Publica".equals(e.optString("role"))?PUB:SUB;
    PointF[] ends=ends(e);PointF a=ends[0],b=ends[1];pen.setColor(UiTheme.color(getContext(),lit?color:0xff435063));pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(Math.max(e.optString("id").equals(selected)?4:2,dp(1.4f)/scale));
    c.drawLine(a.x,a.y,b.x,b.y,pen);float angle=(float)Math.atan2(b.y-a.y,b.x-a.x);float arrowSize=Math.min(Math.max(12,dp(6)/scale),(float)Math.hypot(b.x-a.x,b.y-a.y)*.22f);Path arrow=new Path();arrow.moveTo(b.x,b.y);arrow.lineTo(b.x-arrowSize*(float)Math.cos(angle-.45),b.y-arrowSize*(float)Math.sin(angle-.45));arrow.moveTo(b.x,b.y);arrow.lineTo(b.x-arrowSize*(float)Math.cos(angle+.45),b.y-arrowSize*(float)Math.sin(angle+.45));c.drawPath(arrow,pen);
    if(e.optString("id").equals(selected)){pen.setStyle(Paint.Style.FILL);pen.setTextSize(Math.max(12,dp(11)/scale));drawLabel(c,e.optString("id"),e.optString("role"),(a.x+b.x)/2,(a.y+b.y)/2,dp(3)/scale,true);}
   }
   for(String id:visible){JSONObject v=vertices.get(id);RectF r=positions.get(id);boolean lit=selected.isEmpty()||focused.contains(id);String type=v.optString("kind"),ns=namespaceOfVertex(id);int color="topic".equals(type)?TOPIC:"unknown".equals(type)?MUTED:ns.isEmpty()?PUB:namespaceColor(ns);float radius=glyphRadius(v);
    pen.setStyle(Paint.Style.FILL);pen.setColor(UiTheme.color(getContext(),lit?color:0xff435063));
    if("node".equals(type))c.drawCircle(r.centerX(),r.centerY(),radius,pen);
    pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(Math.max(selected.equals(id)||accessibilityFocused(id)?3:1.5f,dp(1)/scale));pen.setColor(UiTheme.color(getContext(),selected.equals(id)||accessibilityFocused(id)?FG:lit?color:0xff435063));c.drawCircle(r.centerX(),r.centerY(),radius,pen);
    if("package".equals(type))c.drawCircle(r.centerX(),r.centerY(),radius-5,pen);
   }
   List<String> labelOrder=new ArrayList<>(visible);labelOrder.sort(Comparator.comparingInt(id->id.equals(selected)?0:focused.contains(id)?1:2));for(String id:labelOrder){JSONObject v=vertices.get(id);RectF r=positions.get(id);boolean lit=selected.isEmpty()||focused.contains(id);pen.setStyle(Paint.Style.FILL);pen.setColor(UiTheme.color(getContext(),lit?FG:0xff6a788b));pen.setTextSize(Math.max(14,12*getResources().getDisplayMetrics().scaledDensity/scale));pen.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL));drawLabel(c,id,shortLabel(v.optString("label")),r.centerX(),r.centerY(),glyphRadius(v),false);pen.setTypeface(Typeface.DEFAULT);}
   c.restore();
  }
  PointF[] ends(JSONObject e){RectF a=positions.get(e.optString("from")),b=positions.get(e.optString("to"));float dx=b.centerX()-a.centerX(),dy=b.centerY()-a.centerY();if(Math.abs(dx)+Math.abs(dy)<.001f)dx=1;float d=(float)Math.hypot(dx,dy),ux=dx/d,uy=dy/d,ra=glyphRadius(vertices.get(e.optString("from"))),rb=glyphRadius(vertices.get(e.optString("to")));boolean reverse=false;for(JSONObject other:edges.values())if(other.optString("from").equals(e.optString("to"))&&other.optString("to").equals(e.optString("from"))){reverse=true;break;}float offset=reverse?Math.min(8,Math.min(ra,rb)*.5f):0,ox=-uy*offset,oy=ux*offset,aa=(float)Math.sqrt(ra*ra-offset*offset),bb=(float)Math.sqrt(rb*rb-offset*offset);return new PointF[]{new PointF(a.centerX()+ux*aa+ox,a.centerY()+uy*aa+oy),new PointF(b.centerX()-ux*bb+ox,b.centerY()-uy*bb+oy)};}
  String hit(float x,float y){Set<String> visible=visibleVertices();float nearest=Float.MAX_VALUE;String body="";for(String id:visible){RectF r=screenRect(id);float d=(float)Math.hypot(x-r.centerX(),y-r.centerY());if(d<=r.width()/2&&d<nearest){nearest=d;body=id;}}if(!body.isEmpty())return body;float best=dp(14);String found="";for(JSONObject e:edges.values())if(edgeVisible(e,visible)){PointF[] p=ends(e);float ax=p[0].x*scale+tx,ay=p[0].y*scale+ty,bx=p[1].x*scale+tx,by=p[1].y*scale+ty;float dx=bx-ax,dy=by-ay;float t=Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/Math.max(1,dx*dx+dy*dy)));float d=(float)Math.hypot(x-ax-t*dx,y-ay-t*dy);if(d<best){best=d;found=e.optString("id");}}if(!found.isEmpty())return found;for(Map.Entry<String,GraphNamespaces.Circle> e:namespaceCircles(visible).entrySet()){GraphNamespaces.Circle g=e.getValue();float distance=(float)Math.hypot(x-(g.x*scale+tx),y-(g.y*scale+ty));float top=(float)(g.y-g.r-18)*scale+ty;if(Math.abs(distance-g.r*scale)<=dp(12)||(Math.abs(x-(g.x*scale+tx))<=g.r*scale&&y>=top-dp(18)&&y<=top+dp(5)))return "ns:"+e.getKey();}return "";}
  void releaseDrag(){for(String id:heldIds)physics.hold(id,false);heldIds.clear();dragId="";}
  void beginDrag(String key){releaseDrag();if(vertices.containsKey(key)){dragId=key;heldIds.add(key);}else{GraphNamespaces.Circle g=selectedNamespace(key);if(g!=null){dragId=key;for(String id:g.members)if(!pinned.contains(id))heldIds.add(id);}}for(String id:heldIds)physics.hold(id,true);}
  @Override public boolean onTouchEvent(MotionEvent e){
   pinch.onTouchEvent(e);switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:downX=lastX=e.getX();downY=lastY=e.getY();moved=scaling=false;beginDrag(hit(downX,downY));getParent().requestDisallowInterceptTouchEvent(true);return true;
    case MotionEvent.ACTION_MOVE:if(e.getPointerCount()==1&&!scaling){float x=e.getX(),y=e.getY();if(Math.hypot(x-downX,y-downY)>dp(7))moved=true;if(moved){if(!dragId.isEmpty()){for(String id:heldIds){RectF r=positions.get(id);if(r!=null){r.offset((x-lastX)/scale,(y-lastY)/scale);physics.move(id,r.centerX(),r.centerY());}}schedulePhysics();}else{tx+=x-lastX;ty+=y-lastY;}lastX=x;lastY=y;invalidate();}}return true;
    case MotionEvent.ACTION_POINTER_DOWN:releaseDrag();moved=true;return true;
    case MotionEvent.ACTION_UP:releaseDrag();if(!moved&&!scaling){select(hit(e.getX(),e.getY()));performClick();}getParent().requestDisallowInterceptTouchEvent(false);return true;
    case MotionEvent.ACTION_CANCEL:releaseDrag();getParent().requestDisallowInterceptTouchEvent(false);return true;
    default:return true;
   }
  }
  @Override public boolean performClick(){super.performClick();return true;}
  @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return new AccessibilityNodeProvider(){
   String key(int id){for(Map.Entry<String,Integer> e:virtualIds.entrySet())if(e.getValue()==id)return e.getKey();return null;}
   @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
    if(id==View.NO_ID){AccessibilityNodeInfo root=AccessibilityNodeInfo.obtain(Stage.this);onInitializeAccessibilityNodeInfo(root);for(String k:interactiveKeys(false))root.addChild(Stage.this,virtualIds.get(k));return root;}
    String k=key(id);if(k==null||!interactiveKeys(false).contains(k))return null;JSONObject v=vertices.get(k);AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain();n.setSource(Stage.this,id);n.setParent(Stage.this);n.setPackageName(getContext().getPackageName());n.setClassName("android.widget.Button");n.setContentDescription(v==null?"Namespace "+k.substring(3):kind(v)+" "+v.optString("label"));n.setClickable(true);n.setEnabled(true);n.setFocusable(true);n.setSelected(k.equals(selected));n.addAction(AccessibilityNodeInfo.ACTION_CLICK);n.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);n.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);n.setAccessibilityFocused(accessibilityFocus==id);Rect r=new Rect();screenRect(k).roundOut(r);n.setBoundsInParent(r);int[] location=new int[2];getLocationOnScreen(location);r.offset(location[0],location[1]);n.setBoundsInScreen(r);n.setVisibleToUser(isShown()&&RectF.intersects(screenRect(k),new RectF(0,0,getWidth(),getHeight())));return n;
   }
   @Override public boolean performAction(int id,int action,Bundle args){String k=key(id);if(k==null||!interactiveKeys(false).contains(k))return false;if(action==AccessibilityNodeInfo.ACTION_CLICK){select(k);virtualEvent(id,AccessibilityEvent.TYPE_VIEW_CLICKED);return true;}if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){if(accessibilityFocus==id)return false;if(accessibilityFocus!=-1)virtualEvent(accessibilityFocus,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);accessibilityFocus=id;virtualEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);return true;}if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS&&accessibilityFocus==id){accessibilityFocus=-1;virtualEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);return true;}return false;}
  };}
 }
}
