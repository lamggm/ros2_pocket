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
 final TextView summary,selection,details;
 final EditText search;
 final CheckBox infrastructure;
 final Map<String,JSONObject> vertices=new LinkedHashMap<>(),edges=new LinkedHashMap<>();
 final Map<String,RectF> positions=new LinkedHashMap<>();
 final Set<String> focused=new HashSet<>(),focusedEdges=new HashSet<>();
 String selected="",signature="",graphState="inactive";
 boolean truncated;
 RosGraphPanel(Context c){
  super(c);setOrientation(VERTICAL);setTag("ros-graph-panel");
  text("ROS GRAPH",22,PUB);
  text("Toque para destacar conexões · arraste para mover · pinça para zoom.\nSetas saindo do nó = publica; entrando = assina.",13,MUTED);
  search=new EditText(c);search.setId(R.id.graph_search);search.setSingleLine(true);search.setTextColor(FG);search.setHintTextColor(MUTED);search.setTextSize(15);search.setHint("Buscar nó ou tópico · Enter para enquadrar");addView(search,new LayoutParams(-1,dp(48)));
  stage=new Stage(c);
  LinearLayout actions=new LinearLayout(c);addView(actions);
  button(actions,"Enquadrar",R.id.graph_fit,()->stage.fit(!selected.isEmpty()));
  button(actions,"−",R.id.graph_zoom_out,()->stage.zoom(.8f));button(actions,"+",R.id.graph_zoom_in,()->stage.zoom(1.25f));
  button(actions,"Limpar",R.id.graph_clear,()->select(""));
  infrastructure=new CheckBox(c);infrastructure.setId(R.id.graph_infrastructure);infrastructure.setText("Mostrar infraestrutura (/rosout, /parameter_events)");infrastructure.setTextColor(MUTED);infrastructure.setTextSize(13);addView(infrastructure);
  summary=text("Desconectado · nenhuma topologia atual",13,MUTED);summary.setId(R.id.graph_summary);
  selection=text("Toque para explorar · nó → tópico → nó",16,FG);selection.setId(R.id.graph_selection);
  stage.setId(R.id.graph_stage);stage.setTag("ros-graph-stage");addView(stage,new LayoutParams(-1,dp(340)));
  details=text("Setas = associações anunciadas, não tráfego ou garantia QoS. Nós arredondados; tópicos retangulares.",13,MUTED);details.setId(R.id.graph_details);details.setTag("ros-graph-details");details.setPadding(0,dp(10),0,dp(16));details.setTextIsSelectable(true);
  infrastructure.setOnCheckedChangeListener((b,on)->{select(visibleVertices().contains(selected)?selected:"");stage.fit(false);});
  search.setOnEditorActionListener((v,action,event)->{find();((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();return true;});
 }
 int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 TextView text(String s,int size,int color){TextView v=new TextView(getContext());v.setText(s);v.setTextSize(size);v.setTextColor(color);addView(v);return v;}
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
   positions.keySet().retainAll(vertices.keySet());Set<String> pubs=new HashSet<>();for(JSONObject e:edges.values())if("Publica".equals(e.optString("role")))pubs.add(e.optString("from"));
   for(JSONObject v:vertices.values()){
    String id=v.optString("id");if(positions.containsKey(id))continue;
    float x="topic".equals(v.optString("kind"))?380:pubs.contains(id)?40:720;int row=0;
    while(occupied(x,72+row*100))row++;
    positions.put(id,new RectF(x,72+row*100,x+260,140+row*100));
   }
   boolean first=stage.needsFit;select(visibleVertices().contains(selected)||edges.containsKey(selected)?selected:"");if(first)stage.fit(false);
  }catch(JSONException ex){clear("Grafo indisponível · resposta inválida");}
 }
 boolean occupied(float x,float y){for(RectF r:positions.values())if(r.left==x&&r.top==y)return true;return false;}
 void clear(String reason){vertices.clear();edges.clear();positions.clear();signature="";graphState="inactive";selected="";focused.clear();focusedEdges.clear();stage.needsFit=true;stage.invalidate();summary.setText(reason);selection.setText("Sem topologia atual");details.setText("Nenhuma associação retida de uma sessão anterior.");stage.changed();}
 Set<String> visibleVertices(){
  Set<String> ids=new LinkedHashSet<>(vertices.keySet());
  if(!infrastructure.isChecked()){
   Set<String> hidden=new HashSet<>();for(JSONObject v:vertices.values())if("topic".equals(v.optString("kind"))&&Arrays.asList("/rosout","/parameter_events").contains(v.optString("label")))hidden.add(v.optString("id"));ids.removeAll(hidden);
   for(JSONObject v:vertices.values())if(!"topic".equals(v.optString("kind"))){boolean any=false,shown=false;for(JSONObject e:edges.values())if(v.optString("id").equals(e.optString("from"))||v.optString("id").equals(e.optString("to"))){any=true;if(!hidden.contains(e.optString("topic")))shown=true;}if(any&&!shown)ids.remove(v.optString("id"));}
  }
  return ids;
 }
 boolean edgeVisible(JSONObject e,Set<String> ids){return ids.contains(e.optString("from"))&&ids.contains(e.optString("to"));}
 void select(String id){
  selected=id;focused.clear();focusedEdges.clear();Set<String> topics=new HashSet<>(),visible=visibleVertices();
  JSONObject selectedEdge=edges.get(id);
  if(selectedEdge!=null)topics.add(selectedEdge.optString("topic"));
  else if(vertices.containsKey(id)){
   if("topic".equals(vertices.get(id).optString("kind")))topics.add(id);
   else for(JSONObject e:edges.values())if(e.optString("from").equals(id)||e.optString("to").equals(id))topics.add(e.optString("topic"));
   focused.add(id);
  }
  for(JSONObject e:edges.values())if(topics.contains(e.optString("topic"))&&edgeVisible(e,visible)){focused.add(e.optString("from"));focused.add(e.optString("to"));focusedEdges.add(e.optString("id"));}
  String title=selectedEdge!=null?"Conexão · "+selectedEdge.optString("role"):vertices.containsKey(id)?kind(vertices.get(id))+" · "+vertices.get(id).optString("label"):"Toque para explorar · nó → tópico → nó";
  selection.setText(title);StringBuilder d=new StringBuilder();
  if(!id.isEmpty()){
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
  stage.invalidate();stage.changed();
 }
 String label(String id){JSONObject v=vertices.get(id);return v==null?"desconhecido":v.optString("label");}
 String kind(JSONObject v){return "topic".equals(v.optString("kind"))?"Tópico":"unknown".equals(v.optString("kind"))?"Endpoint sem nó confirmado":"Nó";}
 void find(){String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);if(q.isEmpty()){select("");return;}String candidate="";for(String id:visibleVertices()){String name=label(id).toLowerCase(Locale.ROOT);if(name.equals(q)){candidate=id;break;}if(candidate.isEmpty()&&name.contains(q))candidate=id;}if(candidate.isEmpty()){select("");selection.setText("Nenhum nó ou tópico visível corresponde à busca");return;}select(candidate);stage.fit(true);}

 final class Stage extends View {
  final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);final ScaleGestureDetector pinch;
  float scale=1,tx=0,ty=0,downX,downY,lastX,lastY;boolean moved,scaling,needsFit=true;
  final Map<String,Integer> virtualIds=new LinkedHashMap<>();int nextVirtual=1,accessibilityFocus=-1,hovered=-1;
  Stage(Context c){super(c);setBackgroundColor(BG);setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("Topologia ROS interativa. Toque para destacar conexões.");pinch=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){@Override public boolean onScale(ScaleGestureDetector d){float old=scale;scale=Math.max(.08f,Math.min(4,scale*d.getScaleFactor()));tx=d.getFocusX()-(d.getFocusX()-tx)*scale/old;ty=d.getFocusY()-(d.getFocusY()-ty)*scale/old;scaling=true;moved=true;invalidate();return true;}});}
  void virtualEvent(int id,int type){if(getParent()==null)return;AccessibilityEvent event=AccessibilityEvent.obtain(type);event.setSource(this,id);event.setPackageName(getContext().getPackageName());event.setClassName("android.widget.Button");getParent().requestSendAccessibilityEvent(this,event);}
  void changed(){Set<String> visible=visibleVertices();boolean keepFocus=false,keepHover=false;for(String k:visible){Integer id=virtualIds.get(k);if(id!=null&&id==accessibilityFocus)keepFocus=true;if(id!=null&&id==hovered)keepHover=true;}if(!keepFocus&&accessibilityFocus!=-1){virtualEvent(accessibilityFocus,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);accessibilityFocus=-1;}if(!keepHover&&hovered!=-1){virtualEvent(hovered,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);hovered=-1;}virtualIds.keySet().retainAll(vertices.keySet());for(String id:vertices.keySet())if(!virtualIds.containsKey(id))virtualIds.put(id,nextVirtual++);sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
  @Override public boolean dispatchHoverEvent(MotionEvent e){AccessibilityManager manager=(AccessibilityManager)getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);if(!manager.isEnabled()||!manager.isTouchExplorationEnabled())return super.dispatchHoverEvent(e);if(e.getActionMasked()!=MotionEvent.ACTION_HOVER_ENTER&&e.getActionMasked()!=MotionEvent.ACTION_HOVER_MOVE&&e.getActionMasked()!=MotionEvent.ACTION_HOVER_EXIT)return super.dispatchHoverEvent(e);String key=e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT?"":hit(e.getX(),e.getY());int next=virtualIds.containsKey(key)?virtualIds.get(key):-1;if(next!=hovered){if(hovered!=-1)virtualEvent(hovered,AccessibilityEvent.TYPE_VIEW_HOVER_EXIT);hovered=next;if(hovered!=-1)virtualEvent(hovered,AccessibilityEvent.TYPE_VIEW_HOVER_ENTER);}return next!=-1||e.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT;}
  RectF screenRect(String id){RectF r=positions.get(id);if(r==null)return new RectF();return new RectF(r.left*scale+tx,r.top*scale+ty,r.right*scale+tx,r.bottom*scale+ty);}
  void fit(boolean focus){if(getWidth()==0||getHeight()==0){needsFit=true;return;}RectF box=new RectF();boolean any=false;for(String id:visibleVertices())if(!focus||focused.contains(id)){RectF r=positions.get(id);if(!any){box.set(r);any=true;}else box.union(r);}if(!any){needsFit=true;invalidate();return;}scale=Math.max(.08f,Math.min(2.2f,Math.min((getWidth()-dp(28))/Math.max(1,box.width()),(getHeight()-dp(50))/Math.max(1,box.height()))));tx=getWidth()/2f-box.centerX()*scale;ty=getHeight()/2f-box.centerY()*scale;needsFit=false;invalidate();}
  void zoom(float factor){float old=scale;scale=Math.max(.08f,Math.min(4,scale*factor));tx=getWidth()/2f-(getWidth()/2f-tx)*scale/old;ty=getHeight()/2f-(getHeight()/2f-ty)*scale/old;invalidate();}
  @Override protected void onSizeChanged(int w,int h,int ow,int oh){if(w!=ow||h!=oh)fit(!selected.isEmpty());}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);Set<String> visible=visibleVertices();if(visible.isEmpty()){pen.setColor(MUTED);pen.setTextSize(dp(15));c.drawText("Aguardando topologia anunciada…",dp(18),getHeight()/2f,pen);return;}
   c.save();c.translate(tx,ty);c.scale(scale,scale);
   for(JSONObject e:edges.values())if(edgeVisible(e,visible)){
    boolean lit=selected.isEmpty()||focusedEdges.contains(e.optString("id"));int color="Publica".equals(e.optString("role"))?PUB:SUB;
    PointF[] ends=ends(e);PointF a=ends[0],b=ends[1];pen.setColor(lit?color:0xff435063);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(e.optString("id").equals(selected)?4:2);
    c.drawLine(a.x,a.y,b.x,b.y,pen);float angle=(float)Math.atan2(b.y-a.y,b.x-a.x);Path arrow=new Path();arrow.moveTo(b.x,b.y);arrow.lineTo(b.x-12*(float)Math.cos(angle-.45),b.y-12*(float)Math.sin(angle-.45));arrow.moveTo(b.x,b.y);arrow.lineTo(b.x-12*(float)Math.cos(angle+.45),b.y-12*(float)Math.sin(angle+.45));c.drawPath(arrow,pen);
    if(e.optString("id").equals(selected)){pen.setStyle(Paint.Style.FILL);pen.setTextSize(12);c.drawText(e.optString("role"),(a.x+b.x)/2-18,(a.y+b.y)/2-7,pen);}
   }
   for(String id:visible){JSONObject v=vertices.get(id);RectF r=positions.get(id);boolean lit=selected.isEmpty()||focused.contains(id);boolean topic="topic".equals(v.optString("kind"));int color=topic?TOPIC:"unknown".equals(v.optString("kind"))?MUTED:PUB;
    pen.setStyle(Paint.Style.FILL);pen.setColor(lit?(selected.equals(id)?0xff274752:0xff18232e):0xff111820);c.drawRoundRect(r,topic?5:28,topic?5:28,pen);pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(selected.equals(id)?3:1.5f);pen.setColor(lit?color:0xff435063);c.drawRoundRect(r,topic?5:28,topic?5:28,pen);
    pen.setStyle(Paint.Style.FILL);pen.setTextSize(11);pen.setColor(lit?color:0xff6a788b);c.drawText(kind(v).toUpperCase(Locale.ROOT),r.left+14,r.top+21,pen);pen.setTextSize(16);pen.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL));String title=v.optString("label");title=TextUtils.ellipsize(title,new android.text.TextPaint(pen),r.width()-28,TextUtils.TruncateAt.MIDDLE).toString();c.drawText(title,r.left+14,r.top+46,pen);pen.setTypeface(Typeface.DEFAULT);
   }
   c.restore();
  }
  PointF[] ends(JSONObject e){RectF a=positions.get(e.optString("from")),b=positions.get(e.optString("to"));boolean right=b.centerX()>=a.centerX();boolean reverse=false;for(JSONObject other:edges.values())if(other.optString("from").equals(e.optString("to"))&&other.optString("to").equals(e.optString("from"))){reverse=true;break;}float offset=reverse?("Publica".equals(e.optString("role"))?-13:13):0;return new PointF[]{new PointF(right?a.right:a.left,a.centerY()+offset),new PointF(right?b.left:b.right,b.centerY()+offset)};}
  String hit(float x,float y){Set<String> visible=visibleVertices();for(String id:visible)if(screenRect(id).contains(x,y))return id;float best=dp(14);String found="";for(JSONObject e:edges.values())if(edgeVisible(e,visible)){PointF[] p=ends(e);float ax=p[0].x*scale+tx,ay=p[0].y*scale+ty,bx=p[1].x*scale+tx,by=p[1].y*scale+ty;float dx=bx-ax,dy=by-ay;float t=Math.max(0,Math.min(1,((x-ax)*dx+(y-ay)*dy)/Math.max(1,dx*dx+dy*dy)));float d=(float)Math.hypot(x-ax-t*dx,y-ay-t*dy);if(d<best){best=d;found=e.optString("id");}}return found;}
  @Override public boolean onTouchEvent(MotionEvent e){
   pinch.onTouchEvent(e);switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:downX=lastX=e.getX();downY=lastY=e.getY();moved=scaling=false;getParent().requestDisallowInterceptTouchEvent(true);return true;
    case MotionEvent.ACTION_MOVE:if(e.getPointerCount()==1&&!scaling){float x=e.getX(),y=e.getY();if(Math.hypot(x-downX,y-downY)>dp(7))moved=true;if(moved){tx+=x-lastX;ty+=y-lastY;lastX=x;lastY=y;invalidate();}}return true;
    case MotionEvent.ACTION_UP:if(!moved&&!scaling){select(hit(e.getX(),e.getY()));performClick();}getParent().requestDisallowInterceptTouchEvent(false);return true;
    case MotionEvent.ACTION_CANCEL:getParent().requestDisallowInterceptTouchEvent(false);return true;
    default:return true;
   }
  }
  @Override public boolean performClick(){super.performClick();return true;}
  @Override public AccessibilityNodeProvider getAccessibilityNodeProvider(){return new AccessibilityNodeProvider(){
   String key(int id){for(Map.Entry<String,Integer> e:virtualIds.entrySet())if(e.getValue()==id)return e.getKey();return null;}
   @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
    if(id==View.NO_ID){AccessibilityNodeInfo root=AccessibilityNodeInfo.obtain(Stage.this);onInitializeAccessibilityNodeInfo(root);for(String k:visibleVertices())root.addChild(Stage.this,virtualIds.get(k));return root;}
    String k=key(id);if(k==null||!visibleVertices().contains(k))return null;JSONObject v=vertices.get(k);AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain();n.setSource(Stage.this,id);n.setParent(Stage.this);n.setPackageName(getContext().getPackageName());n.setClassName("android.widget.Button");n.setContentDescription(kind(v)+" "+v.optString("label"));n.setClickable(true);n.setEnabled(true);n.setFocusable(true);n.setSelected(k.equals(selected));n.addAction(AccessibilityNodeInfo.ACTION_CLICK);n.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);n.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);n.setAccessibilityFocused(accessibilityFocus==id);Rect r=new Rect();screenRect(k).roundOut(r);n.setBoundsInParent(r);int[] location=new int[2];getLocationOnScreen(location);r.offset(location[0],location[1]);n.setBoundsInScreen(r);n.setVisibleToUser(isShown()&&RectF.intersects(screenRect(k),new RectF(0,0,getWidth(),getHeight())));return n;
   }
   @Override public boolean performAction(int id,int action,Bundle args){String k=key(id);if(k==null||!visibleVertices().contains(k))return false;if(action==AccessibilityNodeInfo.ACTION_CLICK){select(k);virtualEvent(id,AccessibilityEvent.TYPE_VIEW_CLICKED);return true;}if(action==AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS){if(accessibilityFocus==id)return false;if(accessibilityFocus!=-1)virtualEvent(accessibilityFocus,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);accessibilityFocus=id;virtualEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);return true;}if(action==AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS&&accessibilityFocus==id){accessibilityFocus=-1;virtualEvent(id,AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);return true;}return false;}
  };}
 }
}
