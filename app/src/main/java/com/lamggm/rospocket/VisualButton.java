package com.lamggm.rospocket;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.widget.Button;

/** Native button semantics/clicks, without platform-specific raised-button chrome. */
@android.annotation.SuppressLint("ViewConstructor")
class VisualButton extends Button {
 final String role,glyph;
 VisualButton(Context c,String role,String glyph){super(c);this.role=role;this.glyph=glyph;setAllCaps(false);setMinWidth(0);setMinimumWidth(0);setMinHeight(dp(48));setMinimumHeight(dp(48));setStateListAnimator(null);setElevation(0);skin();}
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 static int bg(Context c){return UiTheme.dark(c)?0xff10181c:0xfff4f7f8;}
 static int surface(Context c){return UiTheme.dark(c)?0xff192329:0xffffffff;}
 static int border(Context c){return UiTheme.dark(c)?0xff304047:0xffd5dfe6;}
 static int ink(Context c){return UiTheme.dark(c)?0xffedf4f5:0xff182c38;}
 static int muted(Context c){return UiTheme.dark(c)?0xff96a9af:0xff526779;}
 static int accent(Context c){return UiTheme.dark(c)?0xff91e9c4:0xff087f67;}
 void skin(){
  int fg=isSelected()?accent(getContext()):ink(getContext());
  int fill="primary".equals(role)?accent(getContext()):"nav".equals(role)?(isSelected()?(UiTheme.dark(getContext())?0xff263e37:0xffdcefe8):bg(getContext())):surface(getContext());
  if("primary".equals(role))fg=UiTheme.dark(getContext())?0xff11251e:0xffffffff;
  setTextColor(fg);setBackgroundTintList(null);setTextSize("nav".equals(role)?11:13);setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));setGravity(Gravity.CENTER);setPadding(dp("nav".equals(role)?4:12),dp(6),dp("nav".equals(role)?4:12),dp(6));
  GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp("nav".equals(role)||"metric".equals(role)?14:10));if(!"nav".equals(role)&&!"icon".equals(role)&&!"metric".equals(role))g.setStroke(dp(1),isSelected()?accent(getContext()):border(getContext()));
  GradientDrawable mask=new GradientDrawable();mask.setColor(0xffffffff);mask.setCornerRadius(dp(12));setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(UiTheme.dark(getContext())?0x2291e9c4:0x22087f67),g,mask));
  if(glyph!=null&&!"metric".equals(role)){
   PocketIcons icon=new PocketIcons(glyph,fg,dp("nav".equals(role)?24:18));icon.setBounds(0,0,icon.getIntrinsicWidth(),icon.getIntrinsicHeight());
   if("nav".equals(role)){setCompoundDrawables(null,icon,null,null);setCompoundDrawablePadding(dp(8));}
   else if("icon".equals(role))setCompoundDrawables(null,null,null,null);
   else{setCompoundDrawables(icon,null,null,null);setCompoundDrawablePadding(dp(8));}
  }invalidate();
 }
 @Override protected void onDraw(Canvas c){
  if("icon".equals(role)&&glyph!=null){PocketIcons icon=new PocketIcons("pause".equals(glyph)&&isSelected()?"play":glyph,isSelected()?accent(getContext()):ink(getContext()),dp(20));int x=(getWidth()-dp(20))/2,y=(getHeight()-dp(20))/2;icon.setBounds(x,y,x+dp(20),y+dp(20));icon.draw(c);return;}super.onDraw(c);
 }
 @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);setAlpha(enabled?1f:.4f);}
}
