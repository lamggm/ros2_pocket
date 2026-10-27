package com.lamggm.rospocket;

import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import org.junit.Test;
import static org.junit.Assert.*;

/** Host Android Views with the inherited explicit JNI double; not device acceptance. */
public class BenchNavigationTest extends ConnectionActivityTest {
 private View view(String name){
  int id=a.getResources().getIdentifier(name,"id",a.getPackageName());
  assertNotEquals("Stable navigation resource: "+name,0,id);
  View v=a.findViewById(id);assertNotNull(name,v);return v;
 }
 private void section(String name){view("nav_"+name).performClick();}
 private boolean within(View child,View ancestor){
  for(ViewParent p=child.getParent();p!=null;p=p.getParent())if(p==ancestor)return true;
  return false;
 }
 @Test public void connectionFooterUsesAnActualLineBreak() {
  java.util.ArrayList<View> matches=new java.util.ArrayList<>();
  view("section_connection").findViewsWithText(matches,"Sem rosbridge",View.FIND_VIEWS_WITH_TEXT);
  assertEquals("Exactly one connection footer",1,matches.size());
  String footer=((android.widget.TextView)matches.get(0)).getText().toString();
  assertTrue("Footer separates the network warning with a real newline",footer.contains("\nBloqueio"));
  assertFalse("Never display an escaped newline literally",footer.contains("\\n"));
 }
 @Test public void fourSectionsContainRealControlsAndSwitchWithoutRebuildingViews() throws Exception {
  String[] names={"connection","topics","monitor","logs"};
  String[] labels={"Conexão","Tópicos","Monitor","Logs"};
  int[] controls={R.id.domain,R.id.topics,R.id.plot_panel,R.id.rosout_entries};
  View[] actual=new View[4];
  for(int i=0;i<4;i++){
   actual[i]=a.findViewById(controls[i]);
   assertTrue("Real control belongs to "+names[i],within(actual[i],view("section_"+names[i])));
   assertEquals(labels[i],((Button)view("nav_"+names[i])).getText().toString());
   assertEquals("Abrir seção "+labels[i],view("nav_"+names[i]).getContentDescription().toString());
  }
  int[][] remaining={{R.id.peer,R.id.connect,R.id.status,R.id.diagnostics,R.id.nodes},
   {R.id.explorer_search,R.id.explorer_namespace,R.id.explorer_type,R.id.explorer_favorites},
   {R.id.watch_select_0,R.id.watch_select_1,R.id.watch_select_2,R.id.watch_select_3,R.id.watch_editor,R.id.watch_remove,R.id.qos_reliable,R.id.qos_transient,R.id.counts,R.id.message,R.id.plot_chart},
   {R.id.rosout_pause,R.id.rosout_clear,R.id.rosout_search,R.id.rosout_logger}};
  for(int i=0;i<remaining.length;i++)for(int id:remaining[i])assertTrue("Existing control stays in "+names[i],within(a.findViewById(id),view("section_"+names[i])));
  for(String selected:names){
   section(selected);
   for(String name:names){
    assertEquals(name.equals(selected)?View.VISIBLE:View.GONE,view("section_"+name).getVisibility());
    assertEquals(name.equals(selected),view("nav_"+name).isSelected());
   }
   for(int i=0;i<4;i++)assertSame("No recreated section controls",actual[i],a.findViewById(controls[i]));
  }
 }
 @Test public void switchingSectionsPreservesSessionReaderSelectionAndLocalFilters() throws Exception {
  tick();paint();
  ((android.widget.EditText)a.findViewById(R.id.explorer_search)).setText("numeric");
  ((android.widget.EditText)a.findViewById(R.id.rosout_search)).setText("camera");
  a.findViewById(R.id.plot_pause).performClick();a.findViewById(R.id.rosout_pause).performClick();
  String plotPause=text(R.id.plot_pause),logPause=text(R.id.rosout_pause),message=text(R.id.message),counts=text(R.id.counts);
  Object requested=get("requested"),worker=get("worker"),multicast=get("multicast"),generation=get("generation"),epoch=get("appliedReaderEpoch");
  int starts=bridge.starts,stops=bridge.stops,subscriptions=bridge.subscriptions,polls=bridge.polls;
  for(int round=0;round<3;round++)for(String name:new String[]{"topics","monitor","logs","connection"})section(name);
  ((java.util.concurrent.ScheduledExecutorService)worker).submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);paint();
  assertSame(requested,get("requested"));assertSame(worker,get("worker"));assertSame(multicast,get("multicast"));
  assertEquals(generation,get("generation"));assertEquals(epoch,get("appliedReaderEpoch"));
  assertEquals(starts,bridge.starts);assertEquals(stops,bridge.stops);assertEquals(subscriptions,bridge.subscriptions);assertEquals(polls,bridge.polls);
  assertEquals("rt/numeric",get("selectedWire"));assertEquals(message,text(R.id.message));assertEquals(counts,text(R.id.counts));
  assertEquals("numeric",text(R.id.explorer_search));assertEquals("camera",text(R.id.rosout_search));
  assertEquals(plotPause,text(R.id.plot_pause));assertEquals(logPause,text(R.id.rosout_pause));
 }
}
