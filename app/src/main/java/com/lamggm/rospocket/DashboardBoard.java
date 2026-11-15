package com.lamggm.rospocket;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;

/** Presentation-only editor. All content Views keep the same parent throughout editing. */
final class DashboardBoard extends LinearLayout {
 interface Sizing {void size(int height);}
 private final DashboardStore store;
 private DashboardLayout committed,draft;
 private Object editOwner;
 private final Grid grid;
 private final LinearLayout controls;
 private final TextView notice;
 private final Button editButton;
 private AlertDialog options;
 private boolean disposed,persistenceUncertain;
 private static final String UNCERTAIN="Salvamento incerto · O arquivo pode ter mudado. Cancelar descarta apenas o rascunho. Tente Salvar novamente ou reinicie o app para reler o layout salvo.";
 DashboardBoard(Context c){
  super(c);setOrientation(VERTICAL);setTag("dashboard-board");store=new DashboardStore(c.getFilesDir());committed=store.load();
  editButton=button("Editar painel","dashboard-edit",()->edit());addView(editButton,new LayoutParams(-1,dp(48)));
  controls=new LinearLayout(c);controls.setOrientation(HORIZONTAL);controls.setVisibility(GONE);addView(controls,new LayoutParams(-1,-2));
  for(Button b:new Button[]{button("Salvar","dashboard-save",()->save()),button("Cancelar","dashboard-cancel",()->cancel()),button("Padrão","dashboard-reset",()->reset())})controls.addView(b,new LayoutParams(0,dp(48),1));
  notice=new TextView(c);notice.setTag("dashboard-persistence-status");notice.setTextSize(13);notice.setTextColor(UiTheme.color(c,0xff9aa9bc));notice.setPadding(0,dp(8),0,dp(8));notice.setVisibility(GONE);addView(notice,new LayoutParams(-1,-2));
  grid=new Grid(c);addView(grid,new LayoutParams(-1,-2));
 }
 private Button button(String text,String tag,Runnable action){Button b=new VisualButton(getContext(),"secondary",null);b.setText(text);b.setTag(tag);b.setOnClickListener(v->action.run());return b;}
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private float font(){return Math.max(1,getResources().getConfiguration().fontScale);}
 void addCard(String id,String title,View view,Sizing size){if(!DashboardLayout.IDS.contains(id)||grid.cards.containsKey(id))throw new IllegalArgumentException("Card duplicado ou desconhecido");grid.register(id,title,view,size);}
 DashboardLayout layout(){return editing()?draft:committed;}
 boolean editing(){return editOwner!=null;}
 void edit(){if(disposed||editing())return;draft=committed;editOwner=new Object();editButton.setVisibility(GONE);controls.setVisibility(VISIBLE);notice.setVisibility(VISIBLE);changed();}
 void cancel(){if(!editing())return;endEdit();}
 private void reset(){if(editing()){draft=DashboardLayout.defaults();changed();notice.setText(persistenceUncertain?UNCERTAIN:"Padrão no rascunho · Salve para aplicar");}}
 private void endEdit(){if(options!=null){options.dismiss();options=null;}draft=null;editOwner=null;editButton.setVisibility(VISIBLE);controls.setVisibility(GONE);notice.setVisibility(persistenceUncertain?VISIBLE:GONE);if(persistenceUncertain)notice.setText(UNCERTAIN);changed();}
 boolean save(){if(!editing()||disposed)return false;try{store.save(draft);committed=draft;persistenceUncertain=false;endEdit();return true;}catch(IOException e){if(e instanceof DashboardStore.Unconfirmed)persistenceUncertain=true;notice.setText(persistenceUncertain?UNCERTAIN:"Falha ao salvar · Layout anterior preservado · Tente novamente ou cancele");notice.announceForAccessibility(notice.getText());return false;}}
 void resize(String id,int w,int h){if(!editing()||disposed)return;draft=draft.resize(id,w,h);changed();}
 void moveBefore(String id,String target){if(!editing()||disposed)return;draft=draft.before(id,target);changed();}
 private void changed(){if(editing())notice.setText(persistenceUncertain?UNCERTAIN:"Rascunho local · Segure a alça para mover · Arraste a alça diagonal ou toque no tamanho para posição e medidas");grid.refresh();}
 void editSize(String id){if(disposed)return;edit();sizeMenu(id);}
 void appearance(){for(Card card:grid.cards.values()){card.overlay.getChildAt(0).setBackgroundColor(VisualButton.surface(getContext()));((GradientDrawable)card.overlay.getBackground()).setStroke(dp(2),VisualButton.accent(getContext()));}if(options!=null&&options.isShowing())UiTheme.dialog(options);}
 void dispose(){cancel();disposed=true;}
 private void sizeMenu(String id){
  if(!editing())return;final Object owner=editOwner;DashboardLayout.Item item=layout().get(id);List<String> labels=new ArrayList<>();List<int[]> sizes=new ArrayList<>();
  for(int h=1;h<=4;h++)for(int w=1;w<=4;w++){try{DashboardLayout.size(id,w,h);}catch(IllegalArgumentException e){continue;}sizes.add(new int[]{w,h});labels.add(w+" × "+h+(item.width==w&&item.height==h?" · atual":""));}
  final int count=sizes.size();labels.add("Mover antes do card anterior");labels.add("Mover depois do próximo card");
  if(options!=null)options.dismiss();options=new AlertDialog.Builder(getContext()).setTitle(grid.cards.get(id).title+" · Tamanho e posição").setItems(labels.toArray(new String[0]),(d,which)->{
   if(!editing()||disposed||editOwner!=owner)return;
   if(which<count){int[] s=sizes.get(which);resize(id,s[0],s[1]);}
   else {List<DashboardLayout.Item> entries=layout().items;int index=0;while(index<entries.size()&&!entries.get(index).id.equals(id))index++;
    if(which==count&&index>0)moveBefore(id,entries.get(index-1).id);
    else if(which==count+1&&index+1<entries.size())moveBefore(entries.get(index+1).id,id);
   }
  }).setNegativeButton("Voltar",null).create();options.show();UiTheme.dialog(options);
 }
 private final class Card {
  final String id,title;final View content;final Sizing sizing;final FrameLayout overlay;final TextView label;final int accessibility;
  Card(String id,String title,View content,Sizing sizing){this.id=id;this.title=title;this.content=content;this.sizing=sizing;accessibility=content.getImportantForAccessibility();
   content.setId(View.generateViewId());if(content.getTag()==null)content.setTag("dashboard-card:"+id);
   overlay=new FrameLayout(getContext());overlay.setTag("dashboard-overlay:"+id);overlay.setClickable(true);overlay.setVisibility(GONE);
   GradientDrawable bg=new GradientDrawable();bg.setColor(0x44000000);bg.setCornerRadius(dp(14));bg.setStroke(dp(2),VisualButton.accent(getContext()));overlay.setBackground(bg);
   LinearLayout top=new LinearLayout(getContext());top.setOrientation(HORIZONTAL);top.setBackgroundColor(VisualButton.surface(getContext()));overlay.addView(top,new FrameLayout.LayoutParams(-1,dp(48*font()),Gravity.TOP));
   Button drag=new VisualButton(getContext(),"icon","drag");drag.setTag("layout-drag:"+id);drag.setOnClickListener(v->sizeMenu(id));drag.setContentDescription("Segure para mover "+title);top.addView(drag,new LinearLayout.LayoutParams(dp(48),-1));
   label=new VisualButton(getContext(),"secondary",null);label.setTag("layout-options:"+id);label.setContentDescription("Tamanho e posição de "+title);label.setOnClickListener(v->sizeMenu(id));label.setTextColor(UiTheme.color(getContext(),0xffe6edf3));label.setTextSize(13);label.setGravity(Gravity.CENTER_VERTICAL);label.setMaxLines(2);top.addView(label,new LinearLayout.LayoutParams(0,-1,1));
   drag.setOnLongClickListener(v->{if(!editing())return false;return v.startDragAndDrop(ClipData.newPlainText("dashboard-card",id),new View.DragShadowBuilder(v),new Drag(editOwner,id),0);});
   overlay.setOnDragListener((v,e)->{Object local=e.getLocalState();if(!(local instanceof Drag))return false;Drag data=(Drag)local;if(!editing()||data.owner!=editOwner)return false;if(e.getAction()==DragEvent.ACTION_DROP)moveBefore(data.id,id);return true;});
   Button handle=new VisualButton(getContext(),"icon","resize");handle.setTag("layout-resize:"+id);handle.setOnClickListener(v->sizeMenu(id));handle.setContentDescription("Arraste para redimensionar "+title+"; toque para escolher tamanho");top.addView(handle,new LinearLayout.LayoutParams(dp(48),-1));
   handle.setOnTouchListener(new View.OnTouchListener(){float x,y;int w,h,nw,nh;Object owner;
    public boolean onTouch(View v,android.view.MotionEvent e){if(!editing())return false;int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){owner=editOwner;x=e.getRawX();y=e.getRawY();DashboardLayout.Item old=layout().get(id);nw=w=old.width;nh=h=old.height;v.getParent().requestDisallowInterceptTouchEvent(true);v.setPressed(true);return true;}
     if(owner!=editOwner){v.setPressed(false);v.getParent().requestDisallowInterceptTouchEvent(false);return true;}
     if(action==MotionEvent.ACTION_MOVE){int min=id.startsWith("watch")?1:2;nw=Math.max(min,Math.min(4,w+Math.round((e.getRawX()-x)/Math.max(1,grid.stride))));nh=Math.max(min,Math.min(4,h+Math.round((e.getRawY()-y)/Math.max(1,grid.unit+grid.gap))));label.setText(title+"\n"+nw+" × "+nh);return true;}
     if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){v.setPressed(false);v.getParent().requestDisallowInterceptTouchEvent(false);if(action==MotionEvent.ACTION_UP){if(nw==w&&nh==h){if(Math.hypot(e.getRawX()-x,e.getRawY()-y)<=ViewConfiguration.get(getContext()).getScaledTouchSlop())v.performClick();else grid.refresh();}else resize(id,nw,nh);}else grid.refresh();return true;}return true;
    }
   });
  }
 }
 private static final class Drag {final Object owner;final String id;Drag(Object owner,String id){this.owner=owner;this.id=id;}}
 private final class Grid extends ViewGroup {
  final Map<String,Card> cards=new LinkedHashMap<>();final Map<String,int[]> boxes=new HashMap<>();int gap,unit,stride;
  Grid(Context c){super(c);setTag("dashboard-grid");}
  void register(String id,String title,View view,Sizing size){Card card=new Card(id,title,view,size);cards.put(id,card);addView(view,new ViewGroup.LayoutParams(-1,-1));addView(card.overlay,new ViewGroup.LayoutParams(-1,-1));refresh();}
  void refresh(){for(Card c:cards.values()){c.overlay.setVisibility(editing()?VISIBLE:GONE);c.content.setImportantForAccessibility(editing()?IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:c.accessibility);DashboardLayout.Item item=DashboardBoard.this.layout().get(c.id);c.label.setText(c.title+"\n"+item.width+" × "+item.height);}requestLayout();invalidate();}
  @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);gap=dp(12);unit=dp(160*font());int columns=width<dp(280*font())?1:width>=dp(800)&&font()<1.35f?4:2;int cell=Math.max(1,(width-gap*(columns-1))/columns);stride=cell+gap;int x=0,y=0,row=0;boxes.clear();int previous=View.NO_ID;
   for(DashboardLayout.Item item:DashboardBoard.this.layout().items){Card c=cards.get(item.id);if(c==null)continue;int w=Math.min(columns,item.width),h=item.height;if(x+w>columns){y+=row+gap;x=0;row=0;}int left=x*stride,top=y,cw=w*cell+(w-1)*gap,ch=h*unit+(h-1)*gap+(editing()?dp(48*font()):0);boxes.put(item.id,new int[]{left,top,cw,ch});int contentHeight=Math.max(1,ch-(editing()?dp(48*font()):0));if(c.sizing!=null)c.sizing.size(contentHeight);c.content.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(contentHeight,MeasureSpec.EXACTLY));if(editing())c.overlay.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY));c.content.setAccessibilityTraversalAfter(previous);previous=c.content.getId();x+=w;row=Math.max(row,ch);}
   int height=cards.isEmpty()?0:y+row;setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
  }
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(Map.Entry<String,int[]> e:boxes.entrySet()){Card c=cards.get(e.getKey());int[] q=e.getValue();c.content.layout(q[0],q[1]+(editing()?dp(48*font()):0),q[0]+q[2],q[1]+q[3]);c.overlay.layout(q[0],q[1],q[0]+q[2],q[1]+q[3]);}}
 }
}
