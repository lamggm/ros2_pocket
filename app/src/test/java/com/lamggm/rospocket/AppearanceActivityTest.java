package com.lamggm.rospocket;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.EditText;
import org.junit.Test;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;

/** Actual Android Views and inherited explicit JNI double; not physical DDS acceptance. */
public class AppearanceActivityTest extends ConnectionActivityTest {
 @Test public void futureSpinnerRowsUseCurrentThemeRatherThanCreationTheme() {
  for(String mode:new String[]{"dark","light"}){
   UiTheme.save(a,mode);a.applyAppearance();
   for(int id:new int[]{R.id.graph_format,R.id.network_mode,R.id.rosout_severity}){
    android.widget.Spinner spinner=a.findViewById(id);android.widget.SpinnerAdapter adapter=spinner.getAdapter();
    android.widget.TextView selected=(android.widget.TextView)adapter.getView(0,null,spinner);
    android.widget.TextView popup=(android.widget.TextView)adapter.getDropDownView(0,null,spinner);
    assertEquals(UiTheme.color(a,0xffe6edf3),selected.getCurrentTextColor());assertEquals(UiTheme.color(a,0xffe6edf3),popup.getCurrentTextColor());
    assertTrue("Popup row has an explicit matching surface",popup.getBackground() instanceof ColorDrawable);
    assertEquals(UiTheme.color(a,0xff161d27),((ColorDrawable)popup.getBackground()).getColor());
   }
  }
 }
 @Test public void savedManualThemeIsAppliedOnNextActivity() {
  UiTheme.save(a,"light");MainActivity next=org.robolectric.Robolectric.buildActivity(MainActivity.class).create().get();
  View root=next.getWindow().getDecorView().findViewWithTag("app-root");assertEquals(0xfff4f7f8,((ColorDrawable)root.getBackground()).getColor());
  assertEquals("light",UiTheme.mode(next));
 }
 @Test public void systemNightChangeRecolorsSameSession() throws Exception {
  tick();paint();UiTheme.save(a,"system");Object request=get("requested");int starts=bridge.starts,stops=bridge.stops;
  android.content.res.Configuration c=new android.content.res.Configuration(a.getResources().getConfiguration());c.uiMode=(c.uiMode&~android.content.res.Configuration.UI_MODE_NIGHT_MASK)|android.content.res.Configuration.UI_MODE_NIGHT_YES;
  a.getResources().updateConfiguration(c,a.getResources().getDisplayMetrics());a.onConfigurationChanged(c);
  View root=a.getWindow().getDecorView().findViewWithTag("app-root");assertEquals(0xff10181c,((ColorDrawable)root.getBackground()).getColor());assertSame(request,get("requested"));assertEquals(starts,bridge.starts);assertEquals(stops,bridge.stops);
 }
 @Test public void unavailableRateIsNotPresentedAsNaN() throws Exception {
  bridge.snapshot=new org.json.JSONObject(bridge.snapshot).put("hz","NaN").toString();tick();paint();
  assertFalse(text(R.id.counts).contains("NaN"));assertTrue(text(R.id.counts).contains("— Hz recebidos"));
 }
 @Test public void themeChangesThroughRealMenuWithoutRecreatingSessionOrControls() throws Exception {
  tick();paint();
  View menu=a.getWindow().getDecorView().findViewWithTag("appearance-menu");
  assertNotNull("Appearance must be accessible outside the connection screen",menu);
  Object request=get("requested"),worker=get("worker"),generation=get("generation");
  View search=a.findViewById(R.id.explorer_search);
  ((EditText)search).setText("retained-filter");
  int starts=bridge.starts,stops=bridge.stops,subscriptions=bridge.subscriptions;
  menu.performClick();AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());
  assertEquals(3,dialog.getListView().getAdapter().getCount());
  dialog.getListView().performItemClick(null,1,1);paint();
  assertEquals("light",a.getSharedPreferences("appearance",Context.MODE_PRIVATE).getString("mode","missing"));
  View root=a.getWindow().getDecorView().findViewWithTag("app-root");assertNotNull(root);
  assertEquals(0xfff4f7f8,((ColorDrawable)root.getBackground()).getColor());
  assertSame(request,get("requested"));assertSame(worker,get("worker"));assertEquals(generation,get("generation"));
  assertSame(search,a.findViewById(R.id.explorer_search));assertEquals("retained-filter",((EditText)search).getText().toString());
  assertEquals(starts,bridge.starts);assertEquals(stops,bridge.stops);assertEquals(subscriptions,bridge.subscriptions);
  menu.performClick();dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getListView().performItemClick(null,2,2);paint();
  assertEquals("dark",a.getSharedPreferences("appearance",0).getString("mode","missing"));
  assertEquals(0xff10181c,((ColorDrawable)root.getBackground()).getColor());
 }
}
