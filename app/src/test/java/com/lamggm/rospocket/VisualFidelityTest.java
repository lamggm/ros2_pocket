package com.lamggm.rospocket;

import android.view.View;
import android.widget.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Reference-A presentation contracts. Inherited tests still exercise the real read-only controls. */
public class VisualFidelityTest extends ConnectionActivityTest {
 @Test @org.robolectric.annotation.Config(qualifiers="w1000dp-h700dp") public void unchangedDashboardDoesNotRequestAnotherLayoutFromItsLayoutObserver() throws Exception {
  tick();paint();call("refreshDashboard",new Class<?>[]{});View root=a.getWindow().getDecorView().findViewWithTag("app-root");root.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(850,View.MeasureSpec.EXACTLY));root.layout(0,0,1280,850);assertFalse(root.isLayoutRequested());
  call("refreshDashboard",new Class<?>[]{});assertFalse("An unchanged header must not feed a requestLayout/globalLayout loop",root.isLayoutRequested());
 }
 @Test @org.robolectric.annotation.Config(qualifiers="w1000dp-h700dp") public void referenceGivesScanMoreRoomThanChart(){
  View root=a.getWindow().getDecorView().findViewWithTag("app-root");root.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(850,View.MeasureSpec.EXACTLY));root.layout(0,0,1280,850);
  View sensor=root.findViewWithTag("sensor-panel"),chart=(View)root.findViewById(R.id.plot_panel).getParent();assertEquals("The selected mockup uses a 1.65:1 visualizer/chart split",1.65,(double)sensor.getWidth()/chart.getWidth(),.02);
 }
 @Test public void opensOnDashboardWithConnectionStillGloballyAccessible(){assertEquals(View.VISIBLE,a.findViewById(R.id.section_monitor).getVisibility());assertEquals(View.VISIBLE,a.findViewById(R.id.nav_connection).getVisibility());}
 @Test public void referenceNavigationHasNativeLineIconsAndMetricCardsHaveValueHierarchy(){
  Button nav=a.findViewById(R.id.nav_monitor);
  assertNotNull("Reference rail requires an icon, not a stack of standard buttons",nav.getCompoundDrawables()[1]);
  Button card=a.getWindow().getDecorView().findViewWithTag("watch-card:0");
  assertEquals("Cards must have a dedicated presentation, not a multiline form button","MetricCard",card.getClass().getSimpleName());
  assertTrue("A reading value must be visually dominant",card.getTextSize()>=28*a.getResources().getDisplayMetrics().scaledDensity);
 }
 @Test public void technicalPlotControlsAreExplicitlyExpandable(){
  View toggle=a.getWindow().getDecorView().findViewWithTag("plot-settings");assertNotNull(toggle);
  View details=a.getWindow().getDecorView().findViewWithTag("plot-configuration");assertNotNull(details);
  assertEquals(View.GONE,details.getVisibility());toggle.performClick();assertEquals(View.VISIBLE,details.getVisibility());
  toggle.performClick();assertEquals(View.GONE,details.getVisibility());
 }
 @Test public void selectingAStyledCardStillOnlySelectsItsExistingEditor() throws Exception {
  int subscriptions=bridge.subscriptions,starts=bridge.starts;
  a.getWindow().getDecorView().findViewWithTag("watch-card:2").performClick();
  assertEquals(2,((Integer)get("activeWatch")).intValue());assertEquals(View.VISIBLE,a.findViewById(R.id.section_topics).getVisibility());
  assertEquals(subscriptions,bridge.subscriptions);assertEquals(starts,bridge.starts);
 }
 @Test public void metricRowsDoNotAlignToHiddenButtonTextBaselines() throws Exception {
  tick();paint();call("refreshDashboard",new Class<?>[]{});a.findViewById(R.id.nav_monitor).performClick();
  View root=a.getWindow().getDecorView().findViewWithTag("app-root");root.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(850,View.MeasureSpec.EXACTLY));root.layout(0,0,1000,850);
  View first=root.findViewWithTag("watch-card:0"),second=root.findViewWithTag("watch-card:1");assertSame(first.getParent(),second.getParent());assertEquals("Cards align to their bounds, not native multiline Button baselines",first.getTop(),second.getTop());
 }
 @Test public void replacedRequestCannotKeepOldCardValueOrReceivingAccessibilityLabel() throws Exception {
  tick();paint();call("refreshDashboard",new Class<?>[]{});Button card=a.getWindow().getDecorView().findViewWithTag("watch-card:0");assertTrue(card.getText().toString().contains("42.5"));
  ((WatchSlots)get("watchSlots")).request(0,"rt/numeric","std_msgs::msg::dds_::Float64_",false,false);
  call("refreshDashboard",new Class<?>[]{});assertFalse(card.getText().toString().contains("42.5"));assertFalse(card.getText().toString().contains("RECEBENDO"));assertTrue(card.getText().toString().contains("Sem dados atuais"));
 }
 @Test public void themeChangesDoNotMakeStaleCardsLiveOrInventPhysicalUnits() throws Exception {
  bridge.snapshot=new org.json.JSONObject(bridge.snapshot).put("fresh",false).toString();tick();paint();
  for(String theme:new String[]{"light","dark"}){UiTheme.save(a,theme);a.applyAppearance();call("refreshDashboard",new Class<?>[]{});Button card=a.getWindow().getDecorView().findViewWithTag("watch-card:0");assertFalse(card.getText().toString().contains("42.5"));assertFalse(card.getText().toString().contains("m/s"));assertTrue(card.getText().toString().contains("Sem dados atuais"));}
 }
}
