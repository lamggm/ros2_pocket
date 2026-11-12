package com.lamggm.rospocket;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** Presentation only. Never recreates an Activity, touches profiles or calls JNI. */
final class UiTheme {
 static final String PREFS="appearance",KEY="mode";
 private static final int[] DARK={0xff0d1117,0xff161d27,0xffe6edf3,0xff9aa9bc,0xff48d7bb,0xffffa97d,0xff5cdbb8,0xff82b8ff,0xffe0a0ff,0xffffc477,0xff101820,0xff405064,0xff435063,0xff274752,0xff18232e,0xff111820,0xff6a788b};
 private static final int[] LIGHT={0xfff4f7f8,0xffffffff,0xff172b37,0xff526773,0xff176b55,0xff9c4920,0xff176b55,0xff265da8,0xff8245a1,0xff805411,0xffeaf1f4,0xffa0b5c0,0xff71848e,0xffd2e9e0,0xffe5eef3,0xffedf2f4,0xff526773};
 static String mode(Context c){String s=c.getSharedPreferences(PREFS,0).getString(KEY,"system");return "light".equals(s)||"dark".equals(s)?s:"system";}
 static boolean dark(Context c){String m=mode(c);return "dark".equals(m)||("system".equals(m)&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);}
 static void save(Context c,String mode){if(!"system".equals(mode)&&!"light".equals(mode)&&!"dark".equals(mode))throw new IllegalArgumentException("Unknown appearance");c.getSharedPreferences(PREFS,0).edit().putString(KEY,mode).apply();}
 static int color(Context c,int canonical){for(int i=0;i<DARK.length;i++)if(canonical==DARK[i])return dark(c)?DARK[i]:LIGHT[i];return canonical;}
 private static int remap(Context c,int current){for(int i=0;i<DARK.length;i++)if(current==DARK[i]||current==LIGHT[i])return dark(c)?DARK[i]:LIGHT[i];return color(c,0xffe6edf3);}
 static GradientDrawable surface(Context c,boolean selected){GradientDrawable d=new GradientDrawable();d.setColor(selected?(dark(c)?0xff263e37:0xffe3f1eb):VisualButton.surface(c));d.setCornerRadius(10*c.getResources().getDisplayMetrics().density);d.setStroke(Math.max(1,(int)c.getResources().getDisplayMetrics().density),selected?VisualButton.accent(c):VisualButton.border(c));return d;}
 static ArrayAdapter<String> spinner(Context context,String[] values){return spinner(context,java.util.Arrays.asList(values));}
 static ArrayAdapter<String> spinner(Context context,java.util.List<String> values){
  return new ArrayAdapter<String>(context,android.R.layout.simple_spinner_item,values){
   private View themed(View view,boolean dropdown){TextView t=(TextView)view;t.setTextColor(color(context,0xffe6edf3));t.setBackgroundColor(color(context,dropdown?0xff161d27:0xff0d1117));t.setMinHeight((int)(48*context.getResources().getDisplayMetrics().density));return t;}
   @Override public View getView(int position,View recycled,ViewGroup parent){return themed(super.getView(position,recycled,parent),false);}
   @Override public View getDropDownView(int position,View recycled,ViewGroup parent){return themed(super.getDropDownView(position,recycled,parent),true);}
  };
 }
 static void dialog(android.app.AlertDialog dialog){
  Context c=dialog.getContext();c.setTheme(dark(c)?R.style.AppTheme:R.style.AppTheme_Light);
  if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(new ColorDrawable(color(c,0xff161d27)));apply(dialog.getWindow().getDecorView());}
  if(dialog.getListView()!=null)dialog.getListView().setBackgroundColor(color(c,0xff161d27));
 }
 static void apply(View v){
  Context c=v.getContext();
  if(v.getBackground() instanceof ColorDrawable){ColorDrawable d=(ColorDrawable)v.getBackground();for(int i=0;i<DARK.length;i++)if(d.getColor()==DARK[i]||d.getColor()==LIGHT[i]){d.setColor(dark(c)?DARK[i]:LIGHT[i]);break;}}
  if(v.getBackground() instanceof GradientDrawable){GradientDrawable d=(GradientDrawable)v.getBackground();if(d.getColor()!=null){int value=d.getColor().getDefaultColor();for(int i=0;i<DARK.length;i++)if(value==DARK[i]||value==LIGHT[i]){d.setColor(dark(c)?DARK[i]:LIGHT[i]);d.setStroke(Math.max(1,(int)c.getResources().getDisplayMetrics().density),color(c,0xff405064));break;}}}
  if(v instanceof TextView){TextView t=(TextView)v;t.setTextColor(remap(c,t.getCurrentTextColor()));t.setHintTextColor(color(c,0xff9aa9bc));}
  if(v instanceof Button){Button b=(Button)v;b.setAllCaps(false);b.setMinHeight((int)(48*c.getResources().getDisplayMetrics().density));b.setTextColor(color(c,b.isSelected()?0xff48d7bb:0xffe6edf3));b.setBackground(surface(c,b.isSelected()));b.setBackgroundTintList(null);}
  if(v instanceof CompoundButton)((CompoundButton)v).setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{color(c,0xff48d7bb),color(c,0xff9aa9bc)}));
  if(v instanceof EditText)v.setBackgroundTintList(ColorStateList.valueOf(color(c,0xff9aa9bc)));
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)apply(g.getChildAt(i));}
  Object tag=v.getTag();
  if("brand-icon".equals(tag)&&v instanceof ImageView)((ImageView)v).setImageDrawable(new PocketIcons("graph",VisualButton.accent(c),Math.round(26*v.getResources().getDisplayMetrics().density)));
  if("design-input".equals(tag)){v.setBackgroundTintList(null);v.setBackground(surface(c,false));}
  if(tag instanceof String&&((String)tag).startsWith("explorer-topic:"))v.setBackground(surface(c,v.isSelected()));
  if("app-root".equals(tag))v.setBackgroundColor(VisualButton.bg(c));
  if("design-surface".equals(tag)||v instanceof SensorPanel){GradientDrawable panel=new GradientDrawable();panel.setColor(VisualButton.surface(c));panel.setCornerRadius(16*v.getResources().getDisplayMetrics().density);panel.setStroke(Math.max(1,Math.round(v.getResources().getDisplayMetrics().density)),VisualButton.border(c));v.setBackground(panel);}
  if(v instanceof RosGraphPanel)((RosGraphPanel)v).applyAppearance();
  if(v instanceof RosoutConsoleView)((RosoutConsoleView)v).applyAppearance();
  if(v instanceof VisualButton)((VisualButton)v).skin();
  else if(v instanceof Button&&!(v instanceof CompoundButton)){Button b=(Button)v;b.setAllCaps(false);b.setTextSize(13);b.setStateListAnimator(null);b.setElevation(0);GradientDrawable button=new GradientDrawable();button.setColor(VisualButton.surface(c));button.setCornerRadius(10*v.getResources().getDisplayMetrics().density);button.setStroke(1,VisualButton.border(c));b.setBackground(button);}
  v.invalidate();
 }
}
