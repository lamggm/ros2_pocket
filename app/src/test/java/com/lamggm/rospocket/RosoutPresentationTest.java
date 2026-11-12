package com.lamggm.rospocket;
import android.content.Context;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31,qualifiers="w1280dp-h850dp")
@LooperMode(LooperMode.Mode.PAUSED)
public class RosoutPresentationTest {
 static JSONObject entry(String message,long sec,int severity) throws Exception {
  return new JSONObject().put("message",message).put("level",severity).put("logger","demo.console").put("file","console_demo.py").put("function","main").put("line",17).put("sourceSec",sec).put("sourceNanosec",123000000).put("intakeAgeMs",230);
 }
 static JSONObject snapshot(JSONObject...entries) throws Exception {
  JSONArray a=new JSONArray();for(JSONObject e:entries)a.put(e);
  return new JSONObject().put("active",true).put("count",a.length()).put("bytes",400).put("evicted",0).put("offeredReliability",new JSONArray().put("reliable")).put("offeredDurability",new JSONArray().put("transient_local")).put("entries",a);
 }
 static void layout(View view,int width,int height){for(int pass=0;pass<3;pass++){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}}
 static String visibleText(View v){if(v.getVisibility()!=View.VISIBLE)return "";StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText());if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)b.append('\n').append(visibleText(g.getChildAt(i)));}return b.toString();}
 @Test public void compactConsoleHidesFileAndQosParagraphsUntilRequested() throws Exception {
  RosoutConsoleView view=new RosoutConsoleView(RuntimeEnvironment.getApplication(),()->{});view.update(snapshot(entry("[DEMO] Mensagem principal",123,20)));layout(view,1144,740);
  assertTrue(visibleText(view).contains("Mensagem principal"));
  assertFalse("File/function must not be dumped into every compact row",visibleText(view).contains("console_demo.py"));
  assertFalse("Writer QoS belongs behind details",visibleText(view).contains("Oferecido pelos writers"));
 }
 @Test public void selectedEventDoesNotMoveToAnotherLogWhenTheRingChanges() throws Exception {
  RosoutConsoleView view=new RosoutConsoleView(RuntimeEnvironment.getApplication(),()->{});JSONObject a=entry("primeiro",1,20),b=entry("selecionado",2,40);
  view.update(snapshot(a,b));layout(view,1144,740);view.findViewWithTag("log-row:1").performClick();
  assertTrue("Selection must be painted on the actual current row",view.findViewWithTag("log-row:1").isSelected());
  view.update(snapshot(entry("novo",3,10),a,b));
  assertEquals("selecionado",((TextView)view.findViewWithTag("log-detail-message")).getText().toString());assertTrue(view.findViewWithTag("log-row:2").isSelected());assertFalse(view.findViewWithTag("log-row:1").isSelected());
 }
 @Test public void themeSwitchRecolorsRowsWithoutLosingPinnedEventOrPause() throws Exception {
  android.content.Context c=RuntimeEnvironment.getApplication();UiTheme.save(c,"dark");RosoutConsoleView view=new RosoutConsoleView(c,()->{});view.update(snapshot(entry("evento fixado",1,40)));layout(view,1144,740);view.findViewWithTag("log-row:0").performClick();view.findViewById(R.id.rosout_pause).performClick();
  UiTheme.save(c,"light");UiTheme.apply(view);
  assertEquals(VisualButton.ink(c),((TextView)view.findViewWithTag("log-message:0")).getCurrentTextColor());
  assertEquals("evento fixado",((TextView)view.findViewWithTag("log-detail-message")).getText().toString());assertTrue(view.isPaused());assertTrue(view.findViewWithTag("log-row:0").isSelected());
 }
 @Test public void resumeAfterClearingWhilePausedShowsTheNewBufferState() throws Exception {
  RosoutConsoleView view=new RosoutConsoleView(RuntimeEnvironment.getApplication(),()->{});view.update(snapshot(entry("antes",1,20)));view.findViewById(R.id.rosout_pause).performClick();view.clearNow();view.update(snapshot(entry("novo após limpar",2,20)));view.findViewById(R.id.rosout_pause).performClick();
  assertTrue(visibleText(view.findViewById(R.id.rosout_entries)).contains("novo após limpar"));assertFalse(((TextView)view.findViewById(R.id.rosout_status)).getText().toString().contains("limpo"));
 }
 @Test public void clearInvalidatesCapturedRowCallbacksAndThePinnedEvent() throws Exception {
  RosoutConsoleView view=new RosoutConsoleView(RuntimeEnvironment.getApplication(),()->{});view.update(snapshot(entry("dado anterior",1,40)));layout(view,1144,740);View old=view.findViewWithTag("log-row:0");old.performClick();view.clearNow();old.performClick();
  assertEquals(View.GONE,view.findViewWithTag("log-inspector").getVisibility());assertNull(view.findViewWithTag("log-detail-message"));assertTrue(visibleText(view).contains("Console limpo"));
 }
 @Test @Config(qualifiers="w412dp-h850dp") public void narrowScreenUsesClosableLateralOverlay() throws Exception {
  android.app.Activity activity=Robolectric.buildActivity(android.app.Activity.class).setup().get();RosoutConsoleView view=new RosoutConsoleView(activity,()->{});activity.setContentView(view);view.update(snapshot(entry("mensagem no celular",1,30)));layout(view,412,780);view.findViewWithTag("log-row:0").performClick();
  android.app.AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(d);assertTrue(d.isShowing());assertEquals("mensagem no celular",((TextView)d.findViewById(android.R.id.content).findViewWithTag("log-detail-message")).getText().toString());
  d.getWindow().getDecorView().findViewWithTag("log-inspector-close").performClick();assertFalse(d.isShowing());activity.finish();
 }
 @Test public void intakeOnlyUpdatesPreserveRowsButSelectionCapturesTheLatestAge() throws Exception {
  RosoutConsoleView view=new RosoutConsoleView(RuntimeEnvironment.getApplication(),()->{});JSONObject e=entry("idade",1,20);view.update(snapshot(e));layout(view,1144,740);View row=view.findViewWithTag("log-row:0");e.put("intakeAgeMs",999);view.update(snapshot(e));assertSame(row,view.findViewWithTag("log-row:0"));row.performClick();assertTrue(((TextView)view.findViewWithTag("log-detail-age")).getText().toString().startsWith("999 ms"));
 }
 @Test public void copyUsesOnlyTheExplicitlySelectedImmutableEvent() throws Exception {
  Context c=RuntimeEnvironment.getApplication();RosoutConsoleView view=new RosoutConsoleView(c,()->{});view.update(snapshot(entry("fixado",1,20)));layout(view,1144,740);view.findViewWithTag("log-row:0").performClick();view.update(snapshot(entry("outro evento",2,40)));view.findViewWithTag("log-copy").performClick();android.content.ClipboardManager clipboard=(android.content.ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);assertEquals("fixado",new JSONObject(clipboard.getPrimaryClip().getItemAt(0).getText().toString()).getString("message"));
 }
 @Test public void pausedSnapshotAgeIsNeverLabeledAsCurrentAtTapOrResume() throws Exception {
  Context c=RuntimeEnvironment.getApplication();RosoutConsoleView view=new RosoutConsoleView(c,()->{});JSONObject e=entry("idade congelada",1,20).put("intakeAgeMs",500);view.update(snapshot(e));layout(view,1144,740);view.findViewById(R.id.rosout_pause).performClick();
  org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(1));view.update(snapshot(entry("idade congelada",1,20).put("intakeAgeMs",60500)));view.findViewWithTag("log-row:0").performClick();
  assertTrue(visibleText(view).contains("IDADE NA AMOSTRA SELECIONADA"));assertFalse(visibleText(view).contains("IDADE NO MOMENTO DO TOQUE"));assertTrue(((TextView)view.findViewWithTag("log-detail-age")).getText().toString().startsWith("500 ms"));
  view.findViewById(R.id.rosout_pause).performClick();UiTheme.save(c,"light");UiTheme.apply(view);assertTrue(visibleText(view).contains("IDADE NA AMOSTRA SELECIONADA"));assertFalse(visibleText(view).contains("IDADE NO MOMENTO DO TOQUE"));assertTrue(((TextView)view.findViewWithTag("log-detail-age")).getText().toString().startsWith("500 ms"));
 }
 @Test public void tappingNativeLogRowOpensTheLateralInspector() throws Exception {
  RosoutConsoleView view=new RosoutConsoleView(RuntimeEnvironment.getApplication(),()->{});
  view.update(snapshot(entry("[DEMO] Aviso simulado",123,30)));layout(view,1144,740);
  View row=view.findViewWithTag("log-row:0");assertNotNull("The compact console must contain an actual clickable log row",row);assertTrue(row.performClick());
  View inspector=view.findViewWithTag("log-inspector");assertNotNull(inspector);assertEquals(View.VISIBLE,inspector.getVisibility());
  assertEquals("[DEMO] Aviso simulado",((TextView)view.findViewWithTag("log-detail-message")).getText().toString());
  assertEquals("console_demo.py:17",((TextView)view.findViewWithTag("log-detail-file")).getText().toString());
 }
}
