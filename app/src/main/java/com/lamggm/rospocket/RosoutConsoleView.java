package com.lamggm.rospocket;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Bounded local projection of the independently collected /rosout reader. */
@android.annotation.SuppressLint("ViewConstructor")
public final class RosoutConsoleView extends LinearLayout {
 public interface Listener { void clear(); }
 private final Listener listener;
 private final Button pause;
 private final Spinner severity;
 private final EditText search,logger,timestamp;
 private final TextView status,entries;
 private boolean paused=false;
 private String last="";
 private JSONObject snapshot=new JSONObject();
 public RosoutConsoleView(Context context,Listener listener){
  super(context);this.listener=listener;setOrientation(VERTICAL);
  TextView title=new TextView(context);title.setText("CONSOLE /rosout · somente leitura");title.setTextColor(0xff48d7bb);title.setTextSize(14);addView(title);
  LinearLayout controls=new LinearLayout(context);controls.setGravity(Gravity.CENTER_VERTICAL);
  pause=new Button(context);pause.setId(R.id.rosout_pause);pause.setText("Pausar");controls.addView(pause);
  Button clear=new Button(context);clear.setId(R.id.rosout_clear);clear.setText("Limpar");controls.addView(clear);addView(controls);
  status=new TextView(context);status.setId(R.id.rosout_status);status.setTextColor(0xff9aa9bc);addView(status);
  severity=new Spinner(context);severity.setId(R.id.rosout_severity);severity.setAdapter(new ArrayAdapter<>(context,android.R.layout.simple_spinner_dropdown_item,new String[]{"Todas severidades","DEBUG","INFO","WARN","ERROR","FATAL"}));addView(severity);
  search=field("Buscar texto, logger, arquivo ou horário",R.id.rosout_search);
  logger=field("Filtro de logger",R.id.rosout_logger);
  timestamp=field("Filtro de horário de origem",R.id.rosout_timestamp);
  entries=new TextView(context);entries.setId(R.id.rosout_entries);entries.setTextColor(0xffe6edf3);entries.setTextIsSelectable(true);entries.setTypeface(android.graphics.Typeface.MONOSPACE);entries.setText("Aguardando /rosout.");addView(entries);
  pause.setOnClickListener(v->{paused=!paused;pause.setText(paused?"Retomar":"Pausar");if(paused)status.setText(status.getText()+"\nVisualização pausada; estado e idade visíveis congelados.");else render();});clear.setOnClickListener(v->listener.clear());
  TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){render();}public void afterTextChanged(Editable e){}};
  search.addTextChangedListener(watcher);logger.addTextChangedListener(watcher);timestamp.addTextChangedListener(watcher);severity.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,android.view.View v,int pos,long id){render();}});
 }
 private EditText field(String hint,int id){EditText e=new EditText(getContext());e.setId(id);e.setHint(hint);e.setSingleLine(true);e.setTextColor(0xffe6edf3);e.setHintTextColor(0xff9aa9bc);addView(e);return e;}
 public boolean isPaused(){return paused;}
 public void clearNow(){snapshot=new JSONObject();last="";entries.setText("Console limpo.");status.setText("Buffer local limpo.");}
 public void update(JSONObject value){String sig=value==null?"{}":value.toString();if(sig.equals(last))return;last=sig;snapshot=value==null?new JSONObject():value;if(!paused)render();}
 private static String level(int n){switch(n){case 10:return "DEBUG";case 20:return "INFO";case 30:return "WARN";case 40:return "ERROR";case 50:return "FATAL";default:return "LEVEL "+n;}}
 private static String values(JSONArray a){if(a==null||a.length()==0)return "desconhecido";java.util.LinkedHashSet<String> unique=new java.util.LinkedHashSet<>();for(int i=0;i<a.length()&&i<32;i++)unique.add(a.optString(i));return unique.toString();}
 private void render(){
  if(paused)return;
  boolean active=snapshot.optBoolean("active",false);String error=snapshot.optString("error","");
  String qos="Oferecido pelos writers: reliability "+values(snapshot.optJSONArray("offeredReliability"))+" · durability "+values(snapshot.optJSONArray("offeredDurability"))+". Reader solicita best_effort / volatile.";
  status.setText(!error.isEmpty()?"Leitor indisponível · "+error:(active?"Leitor ativo · best_effort / volatile · buffer "+snapshot.optInt("count")+"/200 · "+snapshot.optInt("bytes")+" B · "+snapshot.optLong("evicted")+" evicções do ring retido\n"+qos:"Leitor /rosout inativo · "+qos));
  JSONArray values=snapshot.optJSONArray("entries");if(values==null)values=new JSONArray();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT),loggerQuery=logger.getText().toString().trim().toLowerCase(Locale.ROOT),timeQuery=timestamp.getText().toString().trim().toLowerCase(Locale.ROOT);int selected=severity.getSelectedItemPosition();StringBuilder out=new StringBuilder();int shown=0;
  for(int i=0;i<values.length()&&shown<200;i++){JSONObject e=values.optJSONObject(i);if(e==null)continue;int n=e.optInt("level");String label=level(n);if(selected>0&&n!=selected*10)continue;String name=e.optString("logger"),msg=e.optString("message"),file=e.optString("file"),fn=e.optString("function");String source=java.math.BigDecimal.valueOf(e.optLong("sourceSec")).add(java.math.BigDecimal.valueOf(e.optLong("sourceNanosec"),9)).setScale(9).toPlainString();String searchable=(name+" "+msg+" "+file+" "+fn+" "+source).toLowerCase(Locale.ROOT);if(!loggerQuery.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(loggerQuery))continue;if(!timeQuery.isEmpty()&&!source.contains(timeQuery))continue;if(!query.isEmpty()&&!searchable.contains(query))continue;out.append('[').append(label).append("] ").append(source).append(" · ").append(name).append(" · ").append(msg);if(e.optBoolean("truncated"))out.append(" [truncado]");if(!file.isEmpty())out.append(" (").append(file).append(':').append(e.optInt("line")).append(' ').append(fn).append(')');out.append(" · intake ").append(e.optLong("intakeAgeMs")).append(" ms ago\n");shown++;}
  entries.setText(out.length()==0?"Nenhum log corresponde aos filtros.":out.toString().trim());
 }
}
