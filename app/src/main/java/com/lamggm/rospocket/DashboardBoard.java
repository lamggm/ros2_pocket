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
 interface Persistence {void save(DashboardLayout layout)throws IOException;}
 private Persistence persistence;
 private DashboardLayout committed,draft;
 private Object editOwner;
 private final Grid grid;
 private final LinearLayout controls;
 private final TextView notice,summary;
 private final Button editButton;
 private AlertDialog options;
 private boolean disposed,persistenceUncertain;
 private static final String UNCERTAIN="Salvamento incerto · O arquivo pode ter mudado. Cancelar descarta apenas o rascunho. Tente Salvar novamente ou reinicie o app para reler o layout salvo.";
 DashboardBoard(Context c){
  super(c);setOrientation(VERTICAL);setTag("dashboard-board");store=new DashboardStore(c.getFilesDir());committed=store.load();
  LinearLayout heading=new LinearLayout(c);heading.setGravity(Gravity.CENTER_VERTICAL);summary=new TextView(c);summary.setTag("dashboard-summary");summary.setText("Suas leituras, num só lugar.");summary.setTextColor(UiTheme.color(c,0xff9aa9bc));summary.setTextSize(13);summary.setGravity(Gravity.CENTER_VERTICAL);heading.addView(summary,new LayoutParams(0,-1,1));editButton=new VisualButton(c,"icon","edit");editButton.setTag("dashboard-edit");editButton.setText("");editButton.setContentDescription("Editar painel");editButton.setTooltipText("Editar painel");editButton.setOnClickListener(v->edit());heading.addView(editButton,new LayoutParams(dp(48),dp(48)));addView(heading,new LayoutParams(-1,dp(48)));
  controls=new LinearLayout(c);controls.setOrientation(HORIZONTAL);controls.setVisibility(GONE);addView(controls,new LayoutParams(-1,-2));
  for(Button b:new Button[]{button("Salvar","dashboard-save",()->save()),button("Cancelar","dashboard-cancel",()->cancel()),button("Padrão","dashboard-reset",()->reset())})controls.addView(b,new LayoutParams(0,dp(48),1));
  notice=new TextView(c);notice.setTag("dashboard-persistence-status");notice.setTextSize(13);notice.setTextColor(UiTheme.color(c,0xff9aa9bc));notice.setPadding(0,dp(8),0,dp(8));notice.setVisibility(GONE);addView(notice,new LayoutParams(-1,-2));
  grid=new Grid(c);addView(grid,new LayoutParams(-1,-2));
 }
 private Button button(String text,String tag,Runnable action){Button b=new VisualButton(getContext(),"secondary",null);b.setText(text);b.setTag(tag);b.setOnClickListener(v->action.run());return b;}
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private float font(){return Math.max(1,getResources().getConfiguration().fontScale);}
 void addCard(String id,String title,View view,Sizing size){if(!DashboardLayout.IDS.contains(id)||grid.cards.containsKey(id))throw new IllegalArgumentException("Card duplicado ou desconhecido");grid.register(id,title,view,size);}
 TextView summary(){return summary;}
 DashboardLayout layout(){return editing()?draft:committed;}
 DashboardLayout savedLayout(){return committed;}
 void bind(DashboardLayout layout,Persistence save){cancel();committed=java.util.Objects.requireNonNull(layout);persistence=save;changed();}
 boolean editing(){return editOwner!=null;}
 void edit(){if(disposed||editing())return;draft=committed.project(Math.max(1,grid.columns));editOwner=new Object();editButton.setVisibility(GONE);controls.setVisibility(VISIBLE);notice.setVisibility(VISIBLE);changed();}
 void cancel(){if(!editing())return;endEdit();}
 private void reset(){if(editing()){draft=DashboardLayout.defaults();changed();notice.setText(persistenceUncertain?UNCERTAIN:"Padrão no rascunho · Salve para aplicar");}}
 private void endEdit(){if(options!=null){options.dismiss();options=null;}draft=null;editOwner=null;editButton.setVisibility(VISIBLE);controls.setVisibility(GONE);notice.setVisibility(persistenceUncertain?VISIBLE:GONE);if(persistenceUncertain)notice.setText(UNCERTAIN);changed();}
 boolean save(){if(!editing()||disposed)return false;try{if(persistence==null)store.save(draft);else persistence.save(draft);committed=draft;persistenceUncertain=false;endEdit();return true;}catch(IOException e){if(e instanceof DashboardStore.Unconfirmed)persistenceUncertain=true;notice.setText(persistenceUncertain?UNCERTAIN:"Falha ao salvar · Layout anterior preservado · "+e.getMessage());notice.announceForAccessibility(notice.getText());return false;}}
 void resize(String id,int w,int h){if(!editing()||disposed)return;draft=draft.project(Math.max(1,grid.columns)).resize(id,w,h);changed();}
 void moveBefore(String id,String target){if(!editing()||disposed)return;draft=draft.project(Math.max(1,grid.columns)).before(id,target);changed();}
 private void changed(){if(editing())notice.setText(persistenceUncertain?UNCERTAIN:"Rascunho local · Segure qualquer card e arraste para uma célula livre · Diagonal: tamanho e nome");grid.refresh();}
 void editSize(String id){if(disposed)return;edit();sizeMenu(id);}
 void appearance(){for(Card card:grid.cards.values()){card.overlay.getChildAt(0).setBackgroundColor(VisualButton.surface(getContext()));((GradientDrawable)card.overlay.getBackground()).setStroke(dp(2),VisualButton.accent(getContext()));}if(options!=null&&options.isShowing())UiTheme.dialog(options);}
 void dispose(){cancel();disposed=true;}
 private void sizeMenu(String id){
  if(!editing())return;final Object owner=editOwner;DashboardLayout.Item item=layout().get(id);List<String> labels=new ArrayList<>();List<int[]> sizes=new ArrayList<>();
  for(int h=1;h<=4;h++)for(int w=1;w<=4;w++){try{DashboardLayout.size(id,w,h);}catch(IllegalArgumentException e){continue;}sizes.add(new int[]{w,h});labels.add(w+" × "+h+(item.width==w&&item.height==h?" · atual":""));}
  final int count=sizes.size();labels.add("Mover antes do card anterior");labels.add("Mover depois do próximo card");
  if(id.startsWith("watch"))labels.add("Visualização da leitura…");final int nameIndex=labels.size();labels.add("Nome do card…");
  if(options!=null)options.dismiss();options=new AlertDialog.Builder(getContext()).setTitle(grid.cards.get(id).title+" · Tamanho e posição").setItems(labels.toArray(new String[0]),(d,which)->{
   if(!editing()||disposed||editOwner!=owner)return;
   if(which==nameIndex){nameMenu(id);return;}
   if(which==count+2&&id.startsWith("watch")){displayMenu(id);return;}
   if(which<count){int[] s=sizes.get(which);resize(id,s[0],s[1]);}
   else {List<DashboardLayout.Item> entries=layout().items;int index=0;while(index<entries.size()&&!entries.get(index).id.equals(id))index++;
    if(which==count&&index>0)moveBefore(id,entries.get(index-1).id);
    else if(which==count+1&&index+1<entries.size())moveBefore(entries.get(index+1).id,id);
   }
  }).setNegativeButton("Voltar",null).create();options.show();UiTheme.dialog(options);
 }
 private EditText displayField(LinearLayout form,String caption,String tag,String value){TextView label=new TextView(getContext());label.setText(caption);form.addView(label);EditText field=new EditText(getContext());field.setSingleLine(true);field.setTextColor(UiTheme.color(getContext(),0xffe6edf3));field.setHintTextColor(UiTheme.color(getContext(),0xff9aa9bc));field.setTag(tag);field.setContentDescription(caption);field.setText(value);form.addView(field,new LayoutParams(-1,dp(48)));return field;}
 private void nameMenu(String id){
  if(!editing()||disposed)return;Object owner=editOwner;LinearLayout form=new LinearLayout(getContext());form.setOrientation(VERTICAL);form.setPadding(dp(16),dp(8),dp(16),dp(8));EditText field=displayField(form,"Nome do card (opcional)","card-name",layout().get(id).name);field.setHint("Vazio usa o nome padrão");TextView error=new TextView(getContext());error.setTag("card-name-error");form.addView(error);TextView help=new TextView(getContext());help.setText("Até 64 caracteres. O nome é só um rótulo; não muda o tópico nem a leitura. Salve o painel para persistir.");form.addView(help);ScrollView scroll=new ScrollView(getContext());scroll.addView(form);
  options=new AlertDialog.Builder(getContext()).setTitle("Nome · "+grid.cards.get(id).title).setView(scroll).setPositiveButton("Usar no rascunho",null).setNegativeButton("Cancelar",null).create();AlertDialog dialog=options;dialog.show();UiTheme.dialog(dialog);dialog.getButton(-1).setOnClickListener(v->{if(!editing()||disposed||owner!=editOwner){dialog.dismiss();return;}try{draft=draft.rename(id,field.getText().toString().trim());changed();dialog.dismiss();}catch(IllegalArgumentException ex){error.setText("Nome inválido: máximo 64 caracteres, sem quebras de linha ou controles.");error.announceForAccessibility(error.getText());}});
 }
 private void displayMenu(String id){
  if(!editing()||disposed)return;Object owner=editOwner;DashboardLayout.Display old=layout().get(id).display;
  LinearLayout form=new LinearLayout(getContext());form.setOrientation(VERTICAL);form.setPadding(dp(16),dp(8),dp(16),dp(8));Spinner mode=new Spinner(getContext());mode.setTag("display-mode");mode.setContentDescription("Tipo de visualização da leitura");String[] modes={"number","gauge","bar"};mode.setAdapter(new ArrayAdapter<>(getContext(),android.R.layout.simple_spinner_dropdown_item,new String[]{"Número / mensagem","Gauge semicircular","Barra horizontal"}));mode.setSelection(Arrays.asList(modes).indexOf(old.mode));form.addView(mode,new LayoutParams(-1,dp(48)));
  EditText path=displayField(form,"Campo numérico exato (ex.: data, linear.x)","display-path",old.path),min=displayField(form,"Mínimo da escala","display-min",Double.toString(old.min)),max=displayField(form,"Máximo da escala","display-max",Double.toString(old.max)),unit=displayField(form,"Unidade exibida (sem conversão)","display-unit",old.unit);TextView error=new TextView(getContext());error.setTag("display-error");form.addView(error);TextView help=new TextView(getContext());help.setText("Gauge e barra exigem campo numérico tipado. Sem dados atuais, nenhum indicador é desenhado. Escala só limita o desenho; o número real é preservado. Salve o painel para persistir.");form.addView(help);ScrollView scroll=new ScrollView(getContext());scroll.addView(form);
  options=new AlertDialog.Builder(getContext()).setTitle("Visualização · "+grid.cards.get(id).title).setView(scroll).setPositiveButton("Usar no rascunho",null).setNegativeButton("Cancelar",null).create();AlertDialog dialog=options;dialog.show();UiTheme.dialog(dialog);dialog.getButton(-1).setOnClickListener(v->{if(!editing()||disposed||owner!=editOwner){dialog.dismiss();return;}try{DashboardLayout.Display value=new DashboardLayout.Display(modes[mode.getSelectedItemPosition()],path.getText().toString().trim(),Double.parseDouble(min.getText().toString().trim()),Double.parseDouble(max.getText().toString().trim()),unit.getText().toString().trim());draft=draft.display(id,value);changed();dialog.dismiss();}catch(IllegalArgumentException ex){error.setText("Campo/escala inválidos: use números finitos, mínimo menor que máximo e campo numérico exato.");error.announceForAccessibility(error.getText());}});
 }
 private final class Card {
  float grabX,grabY;final String id,title;final View content;final Sizing sizing;final FrameLayout overlay;final TextView label,manualTitle;final int accessibility;
  Card(String id,String title,View content,Sizing sizing){this.id=id;this.title=title;this.content=content;this.sizing=sizing;accessibility=content.getImportantForAccessibility();
   content.setId(View.generateViewId());if(content.getTag()==null)content.setTag("dashboard-card:"+id);
   manualTitle=new TextView(getContext());manualTitle.setTag("card-title:"+id);manualTitle.setTextSize(15);manualTitle.setTextColor(UiTheme.color(getContext(),0xffe6edf3));manualTitle.setSingleLine(true);manualTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);manualTitle.setGravity(Gravity.CENTER_VERTICAL);manualTitle.setPadding(dp(12),0,dp(12),0);manualTitle.setVisibility(GONE);
   overlay=new FrameLayout(getContext());overlay.setTag("dashboard-overlay:"+id);overlay.setClickable(true);overlay.setVisibility(GONE);
   GradientDrawable bg=new GradientDrawable();bg.setColor(0x44000000);bg.setCornerRadius(dp(14));bg.setStroke(dp(2),VisualButton.accent(getContext()));overlay.setBackground(bg);
   LinearLayout top=new LinearLayout(getContext());top.setOrientation(HORIZONTAL);top.setBackgroundColor(VisualButton.surface(getContext()));overlay.addView(top,new FrameLayout.LayoutParams(-1,dp(48*font()),Gravity.TOP));
   Button drag=new VisualButton(getContext(),"icon","drag");drag.setTag("layout-drag:"+id);drag.setOnClickListener(v->sizeMenu(id));drag.setContentDescription("Segure para mover "+title);top.addView(drag,new LinearLayout.LayoutParams(dp(48),-1));
   label=new VisualButton(getContext(),"secondary",null);label.setTag("layout-options:"+id);label.setContentDescription("Tamanho e posição de "+title);label.setOnClickListener(v->sizeMenu(id));label.setTextColor(UiTheme.color(getContext(),0xffe6edf3));label.setTextSize(11);label.setPadding(0,0,0,0);label.setGravity(Gravity.CENTER_VERTICAL);label.setMaxLines(2);top.addView(label,new LinearLayout.LayoutParams(0,-1,1));
   overlay.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){grabX=e.getX();grabY=e.getY();}return false;});
   overlay.setOnLongClickListener(v->startDrag(grabX,grabY));drag.setOnLongClickListener(v->startDrag(overlay.getWidth()/2f,overlay.getHeight()/2f));
   Button handle=new VisualButton(getContext(),"icon","resize");handle.setTag("layout-resize:"+id);handle.setOnClickListener(v->sizeMenu(id));handle.setContentDescription("Arraste para redimensionar "+title+"; toque para escolher tamanho");top.addView(handle,new LinearLayout.LayoutParams(dp(48),-1));
   handle.setOnTouchListener(new View.OnTouchListener(){float x,y;int w,h,nw,nh;Object owner;
    public boolean onTouch(View v,android.view.MotionEvent e){if(!editing())return false;int action=e.getActionMasked();if(action==MotionEvent.ACTION_DOWN){owner=editOwner;x=e.getRawX();y=e.getRawY();DashboardLayout.Item old=layout().get(id);nw=w=old.width;nh=h=old.height;v.getParent().requestDisallowInterceptTouchEvent(true);v.setPressed(true);return true;}
     if(owner!=editOwner){v.setPressed(false);v.getParent().requestDisallowInterceptTouchEvent(false);return true;}
     if(action==MotionEvent.ACTION_MOVE){int min=id.startsWith("watch")?1:2;nw=Math.max(min,Math.min(4,w+Math.round((e.getRawX()-x)/Math.max(1,grid.stride))));nh=Math.max(min,Math.min(4,h+Math.round((e.getRawY()-y)/Math.max(1,grid.rowUnit(id)+grid.gap))));label.setText(title+"\n"+nw+" × "+nh);return true;}
     if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){v.setPressed(false);v.getParent().requestDisallowInterceptTouchEvent(false);if(action==MotionEvent.ACTION_UP){if(nw==w&&nh==h){if(Math.hypot(e.getRawX()-x,e.getRawY()-y)<=ViewConfiguration.get(getContext()).getScaledTouchSlop())v.performClick();else grid.refresh();}else resize(id,nw,nh);}else grid.refresh();return true;}return true;
    }
   });
  }
  private boolean startDrag(float x,float y){if(!editing()||disposed)return false;final int gx=Math.max(0,Math.min(overlay.getWidth()-1,Math.round(x))),gy=Math.max(0,Math.min(overlay.getHeight()-1,Math.round(y)));View.DragShadowBuilder shadow=new View.DragShadowBuilder(overlay){@Override public void onProvideShadowMetrics(android.graphics.Point size,android.graphics.Point touch){size.set(overlay.getWidth(),overlay.getHeight());touch.set(gx,gy);}};return overlay.startDragAndDrop(ClipData.newPlainText("dashboard-card",id),shadow,new Drag(editOwner,id,gx,gy),0);}
 }
 private static final class Drag {final Object owner;final String id;final float x,y;Drag(Object owner,String id){this(owner,id,0,0);}Drag(Object owner,String id,float x,float y){this.owner=owner;this.id=id;this.x=x;this.y=y;}}
 private final class Grid extends ViewGroup {
  final Map<String,Card> cards=new LinkedHashMap<>();final Map<String,int[]> boxes=new HashMap<>();int gap,unit,stride,columns=4,rows;
  private Drag active;private float dragX,dragY;private int dropColumn,dropRow,scrollDirection;private boolean validDrop,scrollScheduled;
  private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  Grid(Context c){super(c);setTag("dashboard-grid");setWillNotDraw(false);setOnDragListener((v,e)->dragEvent(e));}
  int rowUnit(String id){return unit;}
  void register(String id,String title,View view,Sizing size){Card card=new Card(id,title,view,size);cards.put(id,card);addView(view,new ViewGroup.LayoutParams(-1,-1));addView(card.manualTitle,new ViewGroup.LayoutParams(-1,-1));addView(card.overlay,new ViewGroup.LayoutParams(-1,-1));refresh();}
  void refresh(){if(!editing())clearDrag();for(Card c:cards.values()){c.overlay.setVisibility(editing()?VISIBLE:GONE);c.content.setImportantForAccessibility(editing()?IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:c.accessibility);DashboardLayout.Item item=DashboardBoard.this.layout().get(c.id);if(c.content instanceof MetricCard){((MetricCard)c.content).display(item.display);((MetricCard)c.content).title(item.name);}c.manualTitle.setVisibility(!(c.content instanceof MetricCard)&&!item.name.isEmpty()?VISIBLE:GONE);c.manualTitle.setText(item.name);c.label.setText((item.name.isEmpty()?c.title:item.name)+"\n"+item.width+" × "+item.height);}requestLayout();invalidate();}
  private ScrollView scrollingParent(){for(android.view.ViewParent p=getParent();p!=null;p=p.getParent())if(p instanceof ScrollView)return (ScrollView)p;return null;}
  private void scheduleScroll(){if(scrollDirection!=0&&!scrollScheduled){scrollScheduled=true;postDelayed(scrollTick,32);}}
  private final Runnable scrollTick=new Runnable(){public void run(){scrollScheduled=false;if(active==null||!editing()||active.owner!=editOwner)return;ScrollView p=scrollingParent();if(p==null||scrollDirection==0)return;int before=p.getScrollY();p.scrollBy(0,scrollDirection*dp(12));int moved=p.getScrollY()-before;if(moved!=0){dragY+=moved;updateDrop();scheduleScroll();}}};
  private void clearDrag(){removeCallbacks(scrollTick);scrollScheduled=false;active=null;scrollDirection=0;validDrop=false;invalidate();}
  private void updateDrop(){if(active==null)return;DashboardLayout layout=DashboardBoard.this.layout().project(columns);DashboardLayout.Item item=layout.get(active.id);dropColumn=Math.max(0,Math.min(columns-Math.min(columns,item.width),Math.round((dragX-active.x)/Math.max(1,stride))));dropRow=Math.max(0,Math.min(DashboardLayout.MAX_ROWS-item.height,Math.round((dragY-active.y)/Math.max(1,stride))));try{layout.position(active.id,dropColumn,dropRow);validDrop=true;}catch(IllegalArgumentException busy){validDrop=false;}if(editing()&&dropRow+item.height+2>rows&&rows<DashboardLayout.MAX_ROWS)requestLayout();invalidate();}
  private boolean dragEvent(DragEvent event){Object local=event.getLocalState();if(!(local instanceof Drag)||!editing()||disposed||((Drag)local).owner!=editOwner)return false;Drag value=(Drag)local;if(!cards.containsKey(value.id))return false;int action=event.getAction();if(action==DragEvent.ACTION_DRAG_STARTED){clearDrag();active=value;return true;}if(active!=value)return false;
   if(action==DragEvent.ACTION_DRAG_LOCATION||action==DragEvent.ACTION_DROP){dragX=event.getX();dragY=event.getY();updateDrop();}
   if(action==DragEvent.ACTION_DRAG_LOCATION){android.graphics.Rect visible=new android.graphics.Rect();getLocalVisibleRect(visible);scrollDirection=dragY>visible.bottom-dp(48)?1:dragY<visible.top+dp(48)?-1:0;if(scrollDirection==0){removeCallbacks(scrollTick);scrollScheduled=false;}else scheduleScroll();return true;}
   if(action==DragEvent.ACTION_DROP){boolean accepted=validDrop;if(accepted){draft=draft.project(columns).position(value.id,dropColumn,dropRow);notice.setText("Posição no rascunho · Salve para aplicar");}else notice.setText(persistenceUncertain?UNCERTAIN:"Célula ocupada · Card mantido na posição anterior");clearDrag();if(accepted)changed();return accepted;}
   if(action==DragEvent.ACTION_DRAG_EXITED){scrollDirection=0;removeCallbacks(scrollTick);scrollScheduled=false;validDrop=false;invalidate();return true;}
   if(action==DragEvent.ACTION_DRAG_ENDED){clearDrag();return true;}return true;
  }
  @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);gap=dp(12);unit=Math.max(1,Math.min(width,dp(120*font())));stride=unit+gap;columns=Math.min(DashboardLayout.MAX_COLUMNS,Math.max(1,(width+gap)/stride));DashboardLayout render=DashboardBoard.this.layout().project(columns);boxes.clear();int previous=View.NO_ID;rows=0;
   for(DashboardLayout.Item item:render.items){Card c=cards.get(item.id);if(c==null)continue;int cw=Math.min(columns,item.width)*stride-gap,ch=item.height*stride-gap,left=item.column*stride,top=item.row*stride,titleHeight=c.manualTitle.getVisibility()==VISIBLE?dp(32*font()):0;rows=Math.max(rows,item.row+item.height);boxes.put(item.id,new int[]{left,top,cw,ch,titleHeight});if(titleHeight>0)c.manualTitle.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(titleHeight,MeasureSpec.EXACTLY));int contentHeight=Math.max(1,ch-titleHeight);if(c.sizing!=null)c.sizing.size(contentHeight);c.content.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(contentHeight,MeasureSpec.EXACTLY));if(editing()){c.label.setPadding(0,0,0,0);String caption=cw<dp(200)?item.width+"×"+item.height:(item.name.isEmpty()?c.title:item.name)+"\n"+item.width+" × "+item.height;if(!caption.equals(c.label.getText().toString()))c.label.setText(caption);c.overlay.measure(MeasureSpec.makeMeasureSpec(cw,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(ch,MeasureSpec.EXACTLY));}c.content.setAccessibilityTraversalAfter(previous);previous=c.content.getId();}
   if(editing()){rows=Math.min(DashboardLayout.MAX_ROWS,Math.max(8,rows+4));if(active!=null)rows=Math.min(DashboardLayout.MAX_ROWS,Math.max(rows,dropRow+render.get(active.id).height+2));}setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(Math.max(0,rows*stride-gap),heightSpec));
  }
  @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(Map.Entry<String,int[]> e:boxes.entrySet()){Card c=cards.get(e.getKey());int[] q=e.getValue();c.manualTitle.layout(q[0],q[1],q[0]+q[2],q[1]+q[4]);c.content.layout(q[0],q[1]+q[4],q[0]+q[2],q[1]+q[3]);c.overlay.layout(q[0],q[1],q[0]+q[2],q[1]+q[3]);}}
  @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);if(!editing()||stride<=0)return;paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,dp(1)));paint.setColor(VisualButton.border(getContext()));android.graphics.Rect clip=canvas.getClipBounds();for(int y=Math.max(0,clip.top/stride);y<Math.min(rows,clip.bottom/stride+1);y++)for(int x=0;x<columns;x++)canvas.drawRoundRect(x*stride+1,y*stride+1,x*stride+unit-1,y*stride+unit-1,dp(8),dp(8),paint);if(active!=null){DashboardLayout.Item item=DashboardBoard.this.layout().get(active.id);paint.setColor(validDrop?VisualButton.accent(getContext()):0xffd96666);paint.setStrokeWidth(dp(3));canvas.drawRoundRect(dropColumn*stride+2,dropRow*stride+2,(dropColumn+Math.min(columns,item.width))*stride-gap-2,(dropRow+item.height)*stride-gap-2,dp(10),dp(10),paint);}}
 }
}
