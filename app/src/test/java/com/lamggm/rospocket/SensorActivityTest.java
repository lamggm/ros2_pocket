package com.lamggm.rospocket;
import android.view.View;
import android.widget.Button;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SensorActivityTest extends ConnectionActivityTest {
 @Test public void explorerOpensSensorWithoutReplacingTelemetryWatch() throws Exception {
  tick();paint();
  JSONObject topic=new JSONObject().put("wireName","rt/scan").put("wireType","sensor_msgs::msg::dds_::LaserScan_").put("name","/scan").put("type","sensor_msgs/msg/LaserScan").put("supported",false);
  ExplorerView explorer=(ExplorerView)a.findViewById(R.id.topics);explorer.update(new JSONArray().put(topic));
  Button read=explorer.findViewWithTag("read:rt/scan\nsensor_msgs::msg::dds_::LaserScan_");
  assertTrue("A sensor topic must have an actionable visualizer even without the telemetry decoder",read.isEnabled());
  Object primary=get("selectedWire");int subscribed=bridge.subscriptions;read.performClick();paint();
  assertEquals(primary,get("selectedWire"));assertEquals(subscribed,bridge.subscriptions);
  assertNotNull(a.getWindow().getDecorView().findViewWithTag("sensor-panel"));
 }
}
