package com.lamggm.rospocket;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Local field selection only: never creates a DDS reader or issues a command. */
final class StructuredMessageView extends LinearLayout {
 private static final int MAX_NODES=192,MAX_DEPTH=8,MAX_TEXT=512;
 private final TextView selection,freshness;
 private final LinearLayout rows;
 private final Set<String> numeric=new HashSet<>();
 private String selected="",type="";
 private boolean fresh,limited;
 private int visited;
 StructuredMessageView(Context context) {
  super(context);setOrientation(VERTICAL);
  selection=label("Campo numérico: nenhum");selection.setTag("selectedNumericPath");addView(selection);
  freshness=label("");freshness.setTag("structuredFreshness");addView(freshness);
  rows=new LinearLayout(context);rows.setOrientation(VERTICAL);addView(rows);
 }
 private TextView label(String text) {
  TextView view=new TextView(getContext());view.setText(text);view.setTextColor(0xffe6edf3);
  view.setTypeface(Typeface.MONOSPACE);view.setTextIsSelectable(true);return view;
 }
 private String bounded(String text,int max) {
  if(text.codePointCount(0,text.length())<=max)return text;
  limited=true;return text.substring(0,text.offsetByCodePoints(0,max))+"…";
 }
 void update(JSONObject message,boolean fresh) {
  this.fresh=fresh;rows.removeAllViews();numeric.clear();visited=0;limited=false;
  if(message==null || message.optInt("schemaVersion")!=1) {
   selected="";type="";selection.setText("Campo numérico: nenhum");freshness.setText(message==null?"":"Esquema não suportado");return;
  }
  String nextType=message.optString("type");if(!type.equals(nextType))selected="";type=nextType;
  JSONArray paths=message.optJSONArray("numericPaths");
  if(paths!=null)for(int i=0;i<Math.min(paths.length(),MAX_NODES);i++) {
   String path=paths.optString(i);if(path.length()<=160)numeric.add(path);
  }
  if(!numeric.contains(selected))selected="";
  limited=message.optBoolean("truncated");
  freshness.setText(fresh?"DADOS RECENTES":"ÚLTIMO VALOR · sem dados recentes");
  add(message.optJSONObject("root"),0);
  if(limited){TextView notice=label("Exibição limitada · limite de campos/profundidade/texto; valores não foram substituídos por zero.");notice.setTag("structuredLimit");rows.addView(notice);}
  selection.setText("Campo numérico: "+(selected.isEmpty()?"nenhum":selected));
 }
 private void add(JSONObject field,int depth) {
  if(visited>=MAX_NODES){limited=true;return;}visited++;
  if(field==null || depth>MAX_DEPTH){limited=true;return;}
  String path=bounded(field.optString("path"),160),kind=field.optString("kind");
  if(!path.isEmpty()) {
   String text=path+(field.has("display")?" = "+bounded(field.optString("display"),MAX_TEXT):" · "+bounded(kind,20));
   if(field.optBoolean("truncated")){limited=true;text+=" … [truncado]";}
   TextView row;
   if("number".equals(kind) && numeric.contains(path)) {
    Button button=new Button(getContext());button.setText(text);button.setTag("numeric:"+path);
    button.setOnClickListener(v->{selected=path;selection.setText("Campo numérico: "+path);});row=button;
   }else {row=label(text);row.setTag("field:"+path);}
   row.setAlpha(fresh?1f:0.55f);row.setPadding(depth*8,0,0,0);rows.addView(row);
  }
  JSONArray children=field.optJSONArray("children");
  if(children!=null)for(int i=0;i<children.length();i++) {
   if(visited>=MAX_NODES){limited=true;break;}
   add(children.optJSONObject(i),depth+1);
  }
 }
}
