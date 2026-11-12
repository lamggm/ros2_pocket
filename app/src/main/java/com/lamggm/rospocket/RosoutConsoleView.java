package com.lamggm.rospocket;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Bounded presentation of /rosout. No subscription, session or network side effects. */
@android.annotation.SuppressLint("ViewConstructor")
public final class RosoutConsoleView extends LinearLayout {
 public interface Listener { void clear(); }
 private final Button pause,filters,technical;
 private final Spinner severity;
 private final EditText search,logger,timestamp;
 private final TextView status,buffer,filtered,qos;
 private final LinearLayout entries,advanced,stage,table,headers,inspector,detailBody;
 private final ScrollView listScroll,inspectorScroll;
 private final Button[] chips=new Button[6];
 private JSONObject latest=new JSONObject(),presented=new JSONObject(),pinned;
 private List<JSONObject> projection=new ArrayList<>();
 private boolean paused,cleared,ready;
 private String last="",rowsSignature="";
 private int rowsEpoch;
 private AlertDialog drawer;
 private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm:ss.SSS",Locale.ROOT).withZone(ZoneOffset.UTC);

 public RosoutConsoleView(Context context,Listener listener){
  super(context);setOrientation(VERTICAL);setTag("rosout-console");severity=new Spinner(context);listScroll=new ScrollView(context);
  LinearLayout heading=row();LinearLayout identity=column();identity.addView(text("Logs",27,false));identity.addView(text(getResources().getConfiguration().screenWidthDp<600?"/rosout · mensagens ROS\u00a02":"/rosout · mensagens do ambiente ROS\u00a02",12,true));heading.addView(identity,new LayoutParams(0,-2,1));
  pause=button("Pausar",R.id.rosout_pause,"pause");heading.addView(pause);Button clear=button("Limpar",R.id.rosout_clear,null);LayoutParams cp=new LayoutParams(-2,dp(48));cp.leftMargin=dp(8);heading.addView(clear,cp);addSpaced(heading,16);
  LinearLayout searchRow=row();search=field("Buscar mensagem, logger ou arquivo",R.id.rosout_search);search.setCompoundDrawablesWithIntrinsicBounds(new PocketIcons("explore",VisualButton.muted(context),dp(19)),null,null,null);search.setCompoundDrawablePadding(dp(10));searchRow.addView(search,new LayoutParams(0,dp(50),1));filters=button("Filtros",View.NO_ID,null);filters.setTag("log-filters");LayoutParams fp=new LayoutParams(-2,dp(50));fp.leftMargin=dp(10);searchRow.addView(filters,fp);addSpaced(searchRow,12);
  HorizontalScrollView chipScroll=new HorizontalScrollView(context);chipScroll.setHorizontalScrollBarEnabled(false);LinearLayout levels=row();String[] names={"Todos","DEBUG","INFO","WARN","ERROR","FATAL"};
  for(int i=0;i<6;i++){final int index=i;chips[i]=button(names[i]+" 0",View.NO_ID,null);chips[i].setTag("log-level:"+i);LayoutParams p=new LayoutParams(-2,dp(48));p.rightMargin=dp(8);levels.addView(chips[i],p);chips[i].setOnClickListener(v->{severity.setSelection(index);render();});}chipScroll.addView(levels);addSpaced(chipScroll,12);
  advanced=column();advanced.setTag("log-advanced-filters");advanced.setVisibility(GONE);advanced.setPadding(dp(12),dp(8),dp(12),dp(12));
  logger=field("Filtro de logger",R.id.rosout_logger);timestamp=field("Horário da origem: segundos ou UTC",R.id.rosout_timestamp);advanced.addView(logger,new LayoutParams(-1,dp(48)));advanced.addView(timestamp,new LayoutParams(-1,dp(48)));
  severity.setId(R.id.rosout_severity);severity.setContentDescription("Severidade do log");severity.setAdapter(UiTheme.spinner(context,new String[]{"Todas severidades","DEBUG","INFO","WARN","ERROR","FATAL"}));advanced.addView(severity,new LayoutParams(-1,dp(48)));addView(advanced);
  stage=row();stage.setGravity(Gravity.TOP);stage.setTag("log-stage");table=column();table.setTag("design-surface");stage.addView(table,new LayoutParams(0,-1,1));
  LinearLayout tableTop=row();tableTop.setPadding(dp(14),0,dp(8),0);TextView topic=text("/rosout · fluxo de eventos",12,true);tableTop.addView(topic,new LayoutParams(0,dp(48),1));Button end=button("Ir ao final",View.NO_ID,null);end.setTag("log-follow");end.setOnClickListener(v->listScroll.fullScroll(View.FOCUS_DOWN));tableTop.addView(end);table.addView(tableTop);
  headers=row();headers.setTag("log-column-headers");headers.setPadding(dp(14),0,dp(14),0);table.addView(headers,new LayoutParams(-1,dp(36)));
  listScroll.setFillViewport(true);listScroll.setNestedScrollingEnabled(true);entries=column();entries.setId(R.id.rosout_entries);listScroll.addView(entries);table.addView(listScroll,new LayoutParams(-1,0,1));
  LinearLayout tableFooter=row();tableFooter.setPadding(dp(14),0,dp(8),0);filtered=text("0 eventos no filtro",11,true);tableFooter.addView(filtered,new LayoutParams(0,dp(44),1));technical=button("Detalhes técnicos",View.NO_ID,null);technical.setTag("log-technical");tableFooter.addView(technical);table.addView(tableFooter);
  inspector=column();inspector.setTag("log-inspector");inspector.setPadding(dp(16),dp(12),dp(16),dp(12));inspector.setVisibility(GONE);
  LinearLayout inspectorHeading=row();TextView caption=text("Evento selecionado",14,false);inspectorHeading.addView(caption,new LayoutParams(0,dp(48),1));Button close=button("Fechar",View.NO_ID,null);close.setTag("log-inspector-close");close.setOnClickListener(v->closeInspector());inspectorHeading.addView(close);inspector.addView(inspectorHeading);
  inspectorScroll=new ScrollView(context);detailBody=column();inspectorScroll.addView(detailBody);inspector.addView(inspectorScroll,new LayoutParams(-1,0,1));Button copy=button("Copiar evento",View.NO_ID,null);copy.setTag("log-copy");copy.setOnClickListener(v->{if(pinned!=null){android.content.ClipboardManager cm=(android.content.ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Evento /rosout",pinned.toString()));}});inspector.addView(copy,new LayoutParams(-1,dp(48)));
  LayoutParams ip=new LayoutParams(dp(330),-1);ip.leftMargin=dp(16);stage.addView(inspector,ip);
  addView(stage,new LayoutParams(-1,dp(Math.max(300,Math.min(520,getResources().getConfiguration().screenHeightDp-(getResources().getConfiguration().screenWidthDp<600?450:370))))));
  buffer=text("",11,true);buffer.setPadding(0,dp(12),0,dp(4));addView(buffer);status=text("Leitor /rosout inativo",11,true);status.setId(R.id.rosout_status);addView(status);
  qos=text("",12,true);qos.setTag("log-qos-details");qos.setVisibility(GONE);qos.setPadding(dp(12),dp(12),dp(12),dp(12));addView(qos);
  pause.setOnClickListener(v->{paused=!paused;if(!paused&&latest.optJSONArray("entries")!=null&&latest.optJSONArray("entries").length()>0)cleared=false;pause.setText(paused?"Retomar":"Pausar");render();});clear.setOnClickListener(v->listener.clear());filters.setOnClickListener(v->{advanced.setVisibility(advanced.getVisibility()==GONE?VISIBLE:GONE);filters.setSelected(advanced.getVisibility()==VISIBLE);});technical.setOnClickListener(v->{qos.setVisibility(qos.getVisibility()==GONE?VISIBLE:GONE);technical.setSelected(qos.getVisibility()==VISIBLE);});
  TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render();}public void afterTextChanged(Editable e){}};
  search.addTextChangedListener(watcher);logger.addTextChangedListener(watcher);timestamp.addTextChangedListener(watcher);severity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){render();}});
  ready=true;applyAppearance();render();
 }
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private LinearLayout row(){LinearLayout r=new LinearLayout(getContext());r.setOrientation(HORIZONTAL);r.setBaselineAligned(false);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
 private LinearLayout column(){LinearLayout c=new LinearLayout(getContext());c.setOrientation(VERTICAL);return c;}
 private TextView text(String s,int size,boolean muted){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(muted?VisualButton.muted(getContext()):VisualButton.ink(getContext()));t.setGravity(Gravity.CENTER_VERTICAL);return t;}
 private Button button(String name,int id,String glyph){Button b=new VisualButton(getContext(),"secondary",glyph);b.setText(name);if(id!=View.NO_ID)b.setId(id);return b;}
 private EditText field(String hint,int id){EditText e=new EditText(getContext());e.setId(id);e.setHint(hint);e.setSingleLine(true);e.setTextSize(13);e.setPadding(dp(14),0,dp(14),0);e.setTag("design-input");return e;}
 private void addSpaced(View v,int bottom){LayoutParams p=new LayoutParams(-1,-2);p.bottomMargin=dp(bottom);addView(v,p);}
 private GradientDrawable surface(int color,int stroke,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
 private static JSONObject copy(JSONObject value){try{return value==null?new JSONObject():new JSONObject(value.toString());}catch(JSONException e){return new JSONObject();}}
 static String source(JSONObject e){return BigDecimal.valueOf(e.optLong("sourceSec")).add(BigDecimal.valueOf(e.optLong("sourceNanosec"),9)).setScale(9).toPlainString();}
 private static String clock(JSONObject e){try{return CLOCK.format(Instant.ofEpochSecond(e.optLong("sourceSec"),e.optLong("sourceNanosec")));}catch(DateTimeException ex){return source(e);}}
 private static String identity(JSONObject e){JSONObject c=copy(e);c.remove("intakeAgeMs");return c.toString();}
 private static String level(int n){switch(n){case 10:return "DEBUG";case 20:return "INFO";case 30:return "WARN";case 40:return "ERROR";case 50:return "FATAL";default:return "LEVEL "+n;}}
 private int levelColor(int n){boolean dark=UiTheme.dark(getContext());switch(n){case 10:return dark?0xff82b8ff:0xff265da8;case 20:return VisualButton.accent(getContext());case 30:return dark?0xffffc477:0xff805411;case 40:case 50:return dark?0xffff9d99:0xffa62f2c;default:return VisualButton.muted(getContext());}}
 private static String values(JSONArray a){if(a==null||a.length()==0)return "desconhecido";LinkedHashSet<String> s=new LinkedHashSet<>();for(int i=0;i<a.length()&&i<32;i++)s.add(a.optString(i));return s.toString();}
 public boolean isPaused(){return paused;}
 public void clearNow(){latest=new JSONObject();presented=new JSONObject();last="";cleared=true;closeInspector();rowsSignature="";render();}
 public void update(JSONObject value){String sig=value==null?"{}":value.toString();if(sig.equals(last))return;last=sig;latest=copy(value);if(!paused){cleared=false;render();}}
 private boolean roomy(){int width=getWidth()>0?getWidth():dp(Math.max(320,getResources().getConfiguration().screenWidthDp-136));return width>=dp(900)&&getResources().getConfiguration().fontScale<1.3f;}
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(ready&&w!=oldw){if(pinned!=null)placeInspector();render();}}
 private void render(){
  if(!ready)return;if(!paused)presented=latest;
  JSONArray all=presented.optJSONArray("entries");if(all==null)all=new JSONArray();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT),lq=logger.getText().toString().trim().toLowerCase(Locale.ROOT),tq=timestamp.getText().toString().trim().toLowerCase(Locale.ROOT);int choice=severity.getSelectedItemPosition();int[] counts=new int[6];List<JSONObject> next=new ArrayList<>();StringBuilder sig=new StringBuilder();
  for(int i=0;i<all.length()&&i<200;i++){JSONObject e=all.optJSONObject(i);if(e==null)continue;counts[0]++;int n=e.optInt("level");if(n>=10&&n<=50&&n%10==0)counts[n/10]++;String name=e.optString("logger"),raw=source(e),time=clock(e);String searchable=(name+" "+e.optString("message")+" "+e.optString("file")+" "+e.optString("function")+" "+raw+" "+time).toLowerCase(Locale.ROOT);if(choice>0&&n!=choice*10)continue;if(!q.isEmpty()&&!searchable.contains(q))continue;if(!lq.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(lq))continue;if(!tq.isEmpty()&&!raw.contains(tq)&&!time.contains(tq))continue;next.add(e);sig.append(identity(e)).append('\n');}
  projection=next;int available=getWidth()>0?getWidth():dp(getResources().getConfiguration().screenWidthDp-136);if(inspector.getParent()==stage&&inspector.getVisibility()==VISIBLE)available-=dp(346);boolean showTime=available>=dp(540),showLogger=available>=dp(850);sig.append(showTime).append(showLogger).append(UiTheme.dark(getContext())).append(cleared);
  for(int i=0;i<6;i++){Button b=chips[i];b.setText((i==0?"Todos":level(i*10))+" "+counts[i]);b.setSelected(choice==i);b.setTextColor(i==0?VisualButton.accent(getContext()):levelColor(i*10));b.setBackground(surface(choice==i?(UiTheme.dark(getContext())?0xff263e37:0xffdcefe8):VisualButton.surface(getContext()),choice==i?(i==0?VisualButton.accent(getContext()):levelColor(i*10)):VisualButton.border(getContext()),10));}
  filtered.setText(next.size()+" eventos no filtro");buffer.setText((paused?"Exibição pausada · ":"")+counts[0]+"/200 no buffer · "+presented.optLong("evicted")+" removidos do ring");String error=presented.optString("error");status.setText(cleared?"Buffer local limpo.":(!error.isEmpty()?"Leitor indisponível · "+error:presented.optBoolean("active")?"Leitor ativo · best_effort / volatile":"Leitor /rosout inativo")+(paused?" · visualização pausada; idade congelada":""));
  qos.setText("Oferecido pelos writers: reliability "+values(presented.optJSONArray("offeredReliability"))+" · durability "+values(presented.optJSONArray("offeredDurability"))+". Reader solicita best_effort / volatile.\nBuffer: "+presented.optInt("bytes")+" B · "+presented.optLong("evicted")+" evicções do ring retido.");
  if(rowsSignature.equals(sig.toString()))return;rowsSignature=sig.toString();rowsEpoch++;entries.removeAllViews();headers.removeAllViews();if(showTime)headers.addView(text("HORA ORIGEM · UTC",10,true),new LayoutParams(dp(142),-1));headers.addView(text("NÍVEL",10,true),new LayoutParams(dp(78),-1));if(showLogger)headers.addView(text("LOGGER",10,true),new LayoutParams(dp(168),-1));headers.addView(text("MENSAGEM",10,true),new LayoutParams(0,-1,1));
  int selectedIndex=-1;if(pinned!=null){String key=identity(pinned);for(int i=0;i<next.size();i++)if(identity(next.get(i)).equals(key)){if(selectedIndex!=-1){selectedIndex=-1;break;}selectedIndex=i;}}
  for(int i=0;i<next.size();i++){JSONObject e=next.get(i);final int position=i,epoch=rowsEpoch;LogRow r=new LogRow(getContext());r.setTag("log-row:"+i);r.setMinimumHeight(dp(62));r.setPadding(dp(14),dp(9),dp(12),dp(9));r.setBackground(surface(VisualButton.surface(getContext()),VisualButton.border(getContext()),0));
   if(showTime){TextView time=text(clock(e),11,true);time.setTypeface(Typeface.MONOSPACE);r.addView(time,new LayoutParams(dp(142),-2));}
   TextView badge=text(level(e.optInt("level")),11,false);badge.setTextColor(levelColor(e.optInt("level")));badge.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);r.addView(badge,new LayoutParams(dp(78),-2));
   if(showLogger){TextView log=text(e.optString("logger"),11,true);log.setTypeface(Typeface.MONOSPACE);log.setSingleLine(true);log.setEllipsize(TextUtils.TruncateAt.END);r.addView(log,new LayoutParams(dp(168),-2));}
   LinearLayout body=column();if(!showLogger){TextView log=text(e.optString("logger"),11,true);log.setSingleLine(true);log.setEllipsize(TextUtils.TruncateAt.END);body.addView(log);}
   TextView message=text(e.optString("message")+(e.optBoolean("truncated")?" [truncado]":""),13,false);message.setTag("log-message:"+i);message.setTypeface(Typeface.MONOSPACE);message.setMaxLines(2);message.setEllipsize(TextUtils.TruncateAt.END);body.addView(message);r.addView(body,new LayoutParams(0,-2,1));TextView arrow=text("›",23,true);arrow.setGravity(Gravity.CENTER);r.addView(arrow,new LayoutParams(dp(24),-1));
   String m=e.optString("message");r.setContentDescription(level(e.optInt("level"))+" · "+source(e)+" · "+e.optString("logger")+" · "+(m.length()>200?m.substring(0,200)+"…":m)+" · Abrir detalhes");for(int j=0;j<r.getChildCount();j++)r.getChildAt(j).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
   r.setSelected(i==selectedIndex);if(r.isSelected())r.setBackground(surface(UiTheme.dark(getContext())?0xff263e37:0xffdcefe8,VisualButton.accent(getContext()),0));r.setOnClickListener(v->{if(epoch!=rowsEpoch||position>=projection.size())return;pin(projection.get(position));});entries.addView(r,new LayoutParams(-1,-2));
  }
  if(next.isEmpty()){TextView empty=text(cleared?"Console limpo.":"Nenhum log corresponde aos filtros.",14,true);empty.setPadding(dp(18),dp(24),dp(18),dp(24));entries.addView(empty);}
 }
 private void pin(JSONObject e){pinned=copy(e);fillInspector();placeInspector();rowsSignature="";render();}
 private void fillInspector(){detailBody.removeAllViews();if(pinned==null)return;TextView badge=text(level(pinned.optInt("level")),12,false);badge.setTextColor(levelColor(pinned.optInt("level")));detailBody.addView(badge);TextView msg=text(pinned.optString("message")+(pinned.optBoolean("truncated")?" [truncado na origem da leitura]":""),14,false);msg.setTag("log-detail-message");msg.setTextIsSelectable(true);msg.setTypeface(Typeface.MONOSPACE);msg.setPadding(0,dp(10),0,dp(10));detailBody.addView(msg);
  detail("LOGGER",pinned.optString("logger"),"log-detail-logger");detail("HORÁRIO NA ORIGEM · UTC",clock(pinned),"log-detail-clock");detail("ROS TIME · SEGUNDOS",source(pinned),"log-detail-time");detail("IDADE NA AMOSTRA SELECIONADA",pinned.optLong("intakeAgeMs")+" ms desde a recepção local","log-detail-age");detail("ARQUIVO E LINHA",pinned.optString("file")+":"+pinned.optInt("line"),"log-detail-file");detail("FUNÇÃO",pinned.optString("function"),"log-detail-function");TextView note=text("Evento fixado no momento do toque. A idade acima é capturada, não uma medida de latência entre dispositivos.",11,true);note.setPadding(0,dp(12),0,dp(12));detailBody.addView(note);
 }
 private void detail(String name,String value,String tag){TextView label=text(name,10,true);label.setPadding(0,dp(12),0,dp(4));detailBody.addView(label);TextView v=text(value.isEmpty()?"—":value,12,false);v.setTag(tag);v.setTypeface(Typeface.MONOSPACE);v.setTextIsSelectable(true);detailBody.addView(v);}
 private void placeInspector(){
  boolean inline=roomy();if(inline){if(drawer!=null){drawer.setOnDismissListener(null);drawer.dismiss();drawer=null;}if(inspector.getParent()!=stage){if(inspector.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)inspector.getParent()).removeView(inspector);LayoutParams p=new LayoutParams(dp(330),-1);p.leftMargin=dp(16);stage.addView(inspector,p);}inspector.setVisibility(VISIBLE);}
  else {if(drawer!=null&&drawer.isShowing())return;if(inspector.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)inspector.getParent()).removeView(inspector);inspector.setVisibility(VISIBLE);drawer=new AlertDialog.Builder(getContext()).setView(inspector).create();drawer.setOnDismissListener(d->{pinned=null;inspector.setVisibility(GONE);drawer=null;});drawer.show();UiTheme.dialog(drawer);Window w=drawer.getWindow();if(w!=null){w.setGravity(Gravity.RIGHT);w.setLayout(Math.min(dp(390),getResources().getDisplayMetrics().widthPixels),-1);}fillInspector();}
 }
 private void closeInspector(){pinned=null;if(drawer!=null){drawer.setOnDismissListener(null);drawer.dismiss();drawer=null;}inspector.setVisibility(GONE);detailBody.removeAllViews();rowsSignature="";if(ready)render();}
 void applyAppearance(){if(!ready)return;setBackgroundColor(VisualButton.bg(getContext()));table.setBackground(surface(VisualButton.surface(getContext()),VisualButton.border(getContext()),16));inspector.setBackground(surface(VisualButton.surface(getContext()),VisualButton.border(getContext()),16));advanced.setBackground(surface(VisualButton.surface(getContext()),VisualButton.border(getContext()),10));qos.setBackground(surface(VisualButton.surface(getContext()),VisualButton.border(getContext()),10));for(EditText e:new EditText[]{search,logger,timestamp}){e.setTextColor(VisualButton.ink(getContext()));e.setHintTextColor(VisualButton.muted(getContext()));e.setBackground(surface(VisualButton.surface(getContext()),VisualButton.border(getContext()),10));}if(drawer!=null&&drawer.isShowing())UiTheme.dialog(drawer);fillInspector();rowsSignature="";render();}
 @Override protected void onDetachedFromWindow(){closeInspector();super.onDetachedFromWindow();}
 private static final class LogRow extends LinearLayout {LogRow(Context c){super(c);setOrientation(HORIZONTAL);setBaselineAligned(false);setGravity(Gravity.CENTER_VERTICAL);setClickable(true);setFocusable(true);} @Override public CharSequence getAccessibilityClassName(){return Button.class.getName();}}
}
