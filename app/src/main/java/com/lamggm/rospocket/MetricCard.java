package com.lamggm.rospocket;

import android.content.Context;
import android.graphics.*;
import android.text.*;

/** A four-slot presentation only. Never parses a numeric field or infers a unit from a topic. */
@android.annotation.SuppressLint("ViewConstructor")
final class MetricCard extends VisualButton {
 private final TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);
 private String wire="",type="",value="",state="";private int slot,occupied;private boolean fresh;
 MetricCard(Context c){super(c,"metric",null);setTextSize(32);setPadding(0,0,0,0);}
 void bind(int slot,int occupied,String wire,String type,String state,String value,boolean fresh){this.slot=slot;this.occupied=occupied;this.wire=wire;this.type=type;this.state=state;this.value=value.length()>96?value.substring(0,96)+"…":value;this.fresh=fresh;String label="Leitura "+(slot+1)+" · "+(wire.isEmpty()?"Livre":wire+"\n"+type+"\n"+(fresh?state.split("\n",2)[0]+"\n"+this.value:"Sem dados atuais"));if(!getText().toString().equals(label))setText(label);invalidate();}
 @Override void skin(){super.skin();setTextSize(32);setPadding(0,0,0,0);}
 private float sp(float size){return size*getResources().getDisplayMetrics().scaledDensity;}
 private void line(Canvas c,String s,float x,float y,float size,int color,float width,boolean bold){p.setTextSize(sp(size));p.setColor(color);p.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));p.setStyle(Paint.Style.FILL);p.setPathEffect(null);c.drawText(TextUtils.ellipsize(s.replace('\n',' '),p,Math.max(1,width),TextUtils.TruncateAt.END).toString(),x,y,p);}
 @Override protected void onDraw(Canvas c){
  int fg=ink(getContext()),muted=muted(getContext()),accent=accent(getContext());float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),pad=16*d;
  p.setColor(isSelected()?accent:border(getContext()));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setPathEffect(wire.isEmpty()?new DashPathEffect(new float[]{5*d,4*d},0):null);c.drawRoundRect(d,d,w-d,h-d,14*d,14*d,p);p.setPathEffect(null);
  if(wire.isEmpty()){
   int size=dp(26),x=(getWidth()-size)/2,y=dp(33);PocketIcons icon=new PocketIcons("add",muted,size);icon.setBounds(x,y,x+size,y+size);icon.draw(c);
   p.setTextAlign(Paint.Align.CENTER);line(c,"Adicionar leitura",w/2,92*d,15,fg,w-2*pad,true);line(c,"Explorar tópicos",w/2,117*d,11,muted,w-2*pad,false);p.setTextAlign(Paint.Align.LEFT);return;
  }
  String badge=fresh?"Recebendo":"Sem dados atuais";p.setTextSize(sp(9));float bw=p.measureText(badge)+16*d;
  p.setStyle(Paint.Style.FILL);p.setColor(fresh?(UiTheme.dark(getContext())?0xff263e37:0xffdcefe8):bg(getContext()));c.drawRoundRect(w-pad-bw,13*d,w-pad,33*d,9*d,9*d,p);
  line(c,badge,w-pad-bw+8*d,27*d,9,fresh?accent:muted,bw-12*d,false);
  line(c,"LEITURA "+(slot+1),pad,27*d,10,muted,Math.max(1,w-2*pad-bw-8*d),true);
  String name=wire.startsWith("rt/")?wire.substring(2):wire;
  line(c,name,pad,59*d,15,fg,w-2*pad,true);
  String shown=fresh?value.trim():"—";if(shown.startsWith("{")||shown.startsWith("["))shown="Ver campos";if(shown.isEmpty())shown="—";
  line(c,shown,pad,103*d,32,fg,w-2*pad,true);
  int split=type.lastIndexOf("::");String shortType=split<0?type:type.substring(split+2);if(shortType.endsWith("_"))shortType=shortType.substring(0,shortType.length()-1);
  line(c,shortType,pad,133*d,11,muted,w-2*pad,false);
 }
}
