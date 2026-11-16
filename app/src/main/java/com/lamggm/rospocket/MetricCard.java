package com.lamggm.rospocket;

import android.content.Context;
import android.graphics.*;
import android.text.*;

/** Four-slot presentation. Typed numeric fields only; never parses display text or infers units. */
@android.annotation.SuppressLint("ViewConstructor")
final class MetricCard extends VisualButton {
 private final TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);
 private String wire="",type="",value="",state="";private int slot,occupied;private boolean fresh;
 private DashboardLayout.Display display=DashboardLayout.Display.defaults();private Double numeric;
 void display(DashboardLayout.Display d){boolean same=display.mode.equals(d.mode)&&display.path.equals(d.path)&&display.unit.equals(d.unit)&&display.min==d.min&&display.max==d.max;display=d;if(!same)numeric=null;invalidate();}
 void sample(org.json.JSONArray fields,boolean current){numeric=null;if(current&&fresh&&BenchProfiles.numeric(type,display.path)&&fields!=null)for(int i=0;i<Math.min(PlotController.MAX_FIELDS,fields.length());i++){org.json.JSONObject f=fields.optJSONObject(i);if(f!=null&&display.path.equals(f.optString("path"))){Object v=f.opt("value");if("finite".equals(f.optString("state"))&&v instanceof Number&&Double.isFinite(((Number)v).doubleValue()))numeric=((Number)v).doubleValue();break;}}String detail="number".equals(display.mode)?"": " · "+display.mode+" · "+display.path+" · "+(numeric==null?"Sem dados atuais":format(numeric)+" "+display.unit+(numeric<display.min||numeric>display.max?" · Fora da escala":""));setContentDescription(getText()+detail+" · abrir detalhes e QoS");invalidate();}
 private static String format(double n){return String.format(java.util.Locale.ROOT,"%.5g",n);}
 private boolean compact(){return getHeight()<dp(180*Math.max(1,getResources().getConfiguration().fontScale));}
 private void compactInstrument(Canvas c,float w,float h,float pad,int fg,int muted){
  float d=getResources().getDisplayMetrics().density*Math.max(1,getResources().getConfiguration().fontScale);boolean valid=numeric!=null;double fraction=valid?Math.max(0,Math.min(1,(numeric-display.min)/(display.max-display.min))):0;float valueY=h-17*d,limitY=h-31*d,top=55*d;String shown=valid?format(numeric)+(display.unit.isEmpty()?"":" "+display.unit):"—";
  if("gauge".equals(display.mode)){float radius=Math.max(4*d,Math.min((w-2*pad)*.3f,(h-74*d)*.5f)),cx=w*.5f,cy=top+radius;RectF oval=new RectF(cx-radius,top,cx+radius,top+2*radius);p.setColor(border(getContext()));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4*d);c.drawArc(oval,180,180,false,p);if(valid){p.setColor(0xff4688d4);c.drawArc(oval,180,(float)(180*fraction),false,p);p.setStrokeWidth(2*d);double a=Math.PI*(1+fraction);c.drawLine(cx,cy,cx+(float)Math.cos(a)*radius*.8f,cy+(float)Math.sin(a)*radius*.8f,p);}p.setStyle(Paint.Style.FILL);line(c,format(display.min),pad,limitY,9,muted,w*.3f,false);p.setTextAlign(Paint.Align.RIGHT);line(c,format(display.max),w-pad,limitY,9,muted,w*.3f,false);p.setTextAlign(Paint.Align.LEFT);
  }else{p.setStyle(Paint.Style.FILL);p.setColor(border(getContext()));c.drawRoundRect(pad,top,w-pad,top+7*d,3*d,3*d,p);if(valid){p.setColor(0xff4688d4);c.drawRoundRect(pad,top,pad+(float)fraction*(w-2*pad),top+7*d,3*d,3*d,p);}line(c,format(display.min)+" … "+format(display.max),pad,top+20*d,9,muted,w-2*pad,false);}
  p.setTextAlign(Paint.Align.CENTER);line(c,shown,w*.5f,valueY,18,fg,w-2*pad,true);p.setTextAlign(Paint.Align.LEFT);String status=!valid?(fresh?"Campo indisponível":"Sem dados atuais"):numeric<display.min||numeric>display.max?"Fora da escala":display.path;line(c,status,pad,h-6*d,9,muted,w-2*pad,false);
 }
 private void instrument(Canvas c,float w,float h,float pad,int fg,int muted){
  if(compact()){compactInstrument(c,w,h,pad,fg,muted);return;}
  float d=getResources().getDisplayMetrics().density,top=72*d,bottom=h-25*d;boolean valid=numeric!=null;double fraction=valid?Math.max(0,Math.min(1,(numeric-display.min)/(display.max-display.min))):0;
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(7*d);p.setColor(border(getContext()));
  if("gauge".equals(display.mode)){float radius=Math.max(4,Math.min((w-2*pad)*.45f,(bottom-top)*.65f)),cx=w*.5f,cy=top+radius;RectF oval=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);c.drawArc(oval,180,180,false,p);if(valid){p.setColor(0xff4688d4);c.drawArc(oval,180,(float)(180*fraction),false,p);double angle=Math.PI*(1+fraction);p.setStrokeWidth(3*d);c.drawLine(cx,cy,cx+(float)Math.cos(angle)*radius*.82f,cy+(float)Math.sin(angle)*radius*.82f,p);}p.setStyle(Paint.Style.FILL);line(c,format(display.min),pad,bottom,10,muted,w*.35f,false);p.setTextAlign(Paint.Align.RIGHT);line(c,format(display.max),w-pad,bottom,10,muted,w*.35f,false);p.setTextAlign(Paint.Align.LEFT);
  }else{float y=top+12*d;p.setStyle(Paint.Style.FILL);c.drawRoundRect(pad,y,w-pad,y+12*d,6*d,6*d,p);if(valid){p.setColor(0xff4688d4);c.drawRoundRect(pad,y,pad+(float)fraction*(w-2*pad),y+12*d,6*d,6*d,p);}line(c,format(display.min)+" … "+format(display.max),pad,y+34*d,10,muted,w-2*pad,false);}
  p.setStyle(Paint.Style.FILL);String shown=valid?format(numeric)+(display.unit.isEmpty()?"":" "+display.unit):"—";float valueY="gauge".equals(display.mode)?Math.min(bottom-16*d,top+(bottom-top)*.82f):Math.min(bottom-16*d,top+76*d);line(c,shown,pad,valueY,22,fg,w-2*pad,true);String status=!valid?(fresh?"Campo indisponível":"Sem dados atuais"):numeric<display.min||numeric>display.max?"Fora da escala":display.path;line(c,status,pad,h-8*d,10,muted,w-2*pad,false);
 }
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
  float yd=compact()?d*Math.max(1,getResources().getConfiguration().fontScale):d;
  String badge=fresh?"Recebendo":"Sem dados atuais";p.setTextSize(sp(9));float bw=p.measureText(badge)+16*d;
  p.setStyle(Paint.Style.FILL);p.setColor(fresh?(UiTheme.dark(getContext())?0xff263e37:0xffdcefe8):bg(getContext()));c.drawRoundRect(w-pad-bw,13*yd,w-pad,33*yd,9*d,9*d,p);
  line(c,badge,w-pad-bw+8*d,27*yd,9,fresh?accent:muted,bw-12*d,false);
  line(c,"LEITURA "+(slot+1),pad,27*yd,10,muted,Math.max(1,w-2*pad-bw-8*d),true);
  String name=wire.startsWith("rt/")?wire.substring(2):wire;
  line(c,name,pad,compact()?47*yd:59*d,compact()? 13:15,fg,w-2*pad,true);
  if(!"number".equals(display.mode)){instrument(c,w,h,pad,fg,muted);return;}
  String shown=fresh?value.trim():"—";if(shown.startsWith("{")||shown.startsWith("["))shown="Ver campos";if(shown.isEmpty())shown="—";
  line(c,shown,pad,compact()?84*yd:103*d,compact()? 26:32,fg,w-2*pad,true);
  int split=type.lastIndexOf("::");String shortType=split<0?type:type.substring(split+2);if(shortType.endsWith("_"))shortType=shortType.substring(0,shortType.length()-1);
  line(c,shortType,pad,compact()?h-10*yd:133*d,11,muted,w-2*pad,false);
 }
}
