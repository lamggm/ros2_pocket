package com.lamggm.rospocket;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One live panel, one preview hit target and at most one dialog per card. */
final class DashboardPreview extends FrameLayout {
 interface Mode {void update(boolean compact,int height);}
 private final Activity activity;
 private final View panel;
 private final Mode mode;
 private final View hit;
 private final TextView expand;
 private final ScrollView inline;
 private final Runnable closeDetails;
 private static final int INLINE_CHROME_DP=144;
 private boolean compact=true;
 private final String defaultTitle,openTag;
 private String title;
 private Dialog dialog;
 private Runnable onOpen=()->{};
 DashboardPreview(Activity activity,String id,String label,View panel,Mode mode,Runnable closeDetails) {
  super(activity);this.activity=activity;this.panel=panel;this.mode=mode;this.closeDetails=closeDetails;defaultTitle=label;openTag="preview-open:"+id;
  setTag("dashboard-card:"+id);background();
  inline=new ScrollView(activity);inline.setFillViewport(true);inline.setVerticalScrollBarEnabled(false);inline.setVisibility(GONE);addView(inline,new LayoutParams(-1,-1));
  addView(panel,new LayoutParams(-1,-1));
  panel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  hit=new View(activity);hit.setTag(openTag);hit.setFocusable(true);hit.setOnClickListener(v->open());addView(hit,new LayoutParams(-1,-1));
  expand=new VisualButton(activity,"secondary",null);expand.setText("Ampliar ↗");expand.setVisibility(GONE);expand.setOnClickListener(v->open());addView(expand,new LayoutParams(-1,dp(48),Gravity.BOTTOM));
  alias("");mode.update(true,dp(230));
 }
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private void background(){GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(12));bg.setColor(VisualButton.surface(activity));setBackground(bg);setClipToOutline(true);}
 DashboardPreview onOpen(Runnable action){onOpen=action;return this;}
 void alias(String value){title=value.isEmpty()?defaultTitle:value;if(panel instanceof PlotView)((PlotView)panel).title(title);hit.setContentDescription("Ampliar · "+title);expand.setContentDescription(hit.getContentDescription());}
 private void detachPanel(){if(panel.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)panel.getParent()).removeView(panel);}
 private void attachInline(){
  panel.setImportantForAccessibility(compact?IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:IMPORTANT_FOR_ACCESSIBILITY_AUTO);
  if(compact)addView(panel,1,new LayoutParams(-1,-1));else inline.addView(panel,new ScrollView.LayoutParams(-1,-2));
 }
 @Override protected void onMeasure(int w,int h){
  if(dialog==null){
   int height=View.MeasureSpec.getSize(h);int threshold=dp(Math.round(440*Math.max(1,getResources().getConfiguration().fontScale)));
   boolean small=View.MeasureSpec.getSize(w)<threshold||height<threshold;
   if(small!=compact){closeDetails.run();detachPanel();compact=small;hit.setTag(small?openTag:null);expand.setTag(small?null:openTag);inline.setVisibility(small?GONE:VISIBLE);hit.setVisibility(small?VISIBLE:GONE);expand.setVisibility(small?GONE:VISIBLE);attachInline();}
   LayoutParams box=(LayoutParams)inline.getLayoutParams();box.bottomMargin=compact?0:dp(48);
   mode.update(compact,compact?height:Math.max(1,height-dp(INLINE_CHROME_DP)));
  }
  super.onMeasure(w,h);
 }
 void close(){closeDetails.run();if(dialog!=null)dialog.dismiss();}
 private void open(){
  if(dialog!=null||!isShown()||activity.isFinishing()||activity.isDestroyed()||getImportantForAccessibility()==IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)return;
  onOpen.run();
  View decor=activity.getWindow().getDecorView();int w=decor.getWidth(),h=decor.getHeight();
  if(w<=0)w=getResources().getDisplayMetrics().widthPixels;if(h<=0)h=getResources().getDisplayMetrics().heightPixels;
  int width=Math.min(dp(1120),Math.max(1,w-dp(32))),height=Math.min(dp(840),Math.max(1,h-dp(56)));
  Dialog d=new Dialog(activity);d.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog=d;
  LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(4),dp(12),dp(8));
  GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(16));bg.setColor(VisualButton.surface(activity));body.setBackground(bg);body.setClipToOutline(true);
  LinearLayout bar=new LinearLayout(activity);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBaselineAligned(false);
  TextView name=new TextView(activity);name.setTag("preview-title");name.setText(title);name.setTextSize(18);name.setTextColor(UiTheme.color(activity,0xffe6edf3));name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);bar.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  VisualButton close=new VisualButton(activity,"secondary",null);close.setText("Fechar");close.setTag("preview-close");close.setOnClickListener(v->d.dismiss());bar.addView(close,new LinearLayout.LayoutParams(-2,dp(48)));body.addView(bar);
  ScrollView scroll=new ScrollView(activity);scroll.setNestedScrollingEnabled(true);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  closeDetails.run();detachPanel();hit.setEnabled(false);expand.setEnabled(false);panel.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);mode.update(false,Math.max(dp(120),height-dp(152)));scroll.addView(panel,new ScrollView.LayoutParams(-1,-2));
  d.setContentView(body);d.setOnDismissListener(ignored->{if(dialog!=d)return;closeDetails.run();scroll.removeView(panel);dialog=null;mode.update(compact,compact?getHeight():Math.max(1,getHeight()-dp(INLINE_CHROME_DP)));attachInline();hit.setEnabled(true);expand.setEnabled(true);});
  d.show();Window window=d.getWindow();if(window!=null){window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));window.setGravity(Gravity.CENTER);window.setLayout(width,height);}
 }
}
