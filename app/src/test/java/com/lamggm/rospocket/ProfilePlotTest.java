package com.lamggm.rospocket;
import java.util.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Synthetic native rows exercise configuration/provenance only, not real DDS. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class ProfilePlotTest {
 @Test public void fourRestoredFieldsWaitForMatchingFreshRowsAndUncheckCancelsThePlan() throws Exception {
  JSONObject json=BenchProfilesTest.profile();JSONArray fields=new JSONArray();for(int i=0;i<4;i++)fields.put(new JSONObject().put("slot",i).put("path","data"));json.put("plots",fields);
  BenchProfiles.Profile profile=BenchProfiles.decode(BenchProfilesTest.catalog(json)).applied;
  PlotController plots=new PlotController();plots.reset(10);plots.configureProfile(profile);assertTrue(plots.history.snapshot().isEmpty());assertEquals(4,plots.pendingProfileFields().size());
  WatchSlots slots=new WatchSlots();JSONArray rows=new JSONArray();
  for(int i=0;i<4;i++){
   BenchProfiles.Watch watch=profile.watches.get(i);WatchSlots.Request request=slots.request(i,watch.wire,watch.type,watch.reliable,watch.transientLocal);slots.complete(request,"");
   rows.put(new JSONObject().put("slot",i).put("generation",5+i).put("subscription",watch.wire.substring(2)).put("type","std_msgs::msg::dds_::String_").put("readerActive",true).put("fresh",true).put("subscriptionState","receiving").put("error","").put("receivedMonotonicNs",100L).put("count",1).put("numericFields",new JSONArray().put(new JSONObject().put("path","data").put("state","finite").put("value",i+0.5))));
  }
  JSONObject snapshot=new JSONObject().put("running",true).put("sessionGeneration",20).put("watches",rows);
  plots.acceptNative(snapshot,10,slots,slots.frames());assertTrue("Wrong advertised type must not restore a series",plots.history.snapshot().isEmpty());
  for(int i=0;i<4;i++)rows.getJSONObject(i).put("type",profile.watches.get(i).type).put("fresh",false);
  plots.acceptNative(snapshot,10,slots,slots.frames());assertTrue(plots.history.snapshot().isEmpty());
  for(int i=0;i<4;i++)rows.getJSONObject(i).put("fresh",true);
  plots.acceptNative(snapshot,10,slots,slots.frames());assertEquals(4,plots.history.snapshot().size());assertTrue(plots.pendingProfileFields().isEmpty());
  for(PlotHistory.Series s:plots.history.snapshot()){assertEquals(1,s.points.size());assertEquals(20,s.provenance.session);assertEquals(5+s.provenance.slot,s.provenance.reader);}
  String removed=plots.history.snapshot().get(2).id;plots.removeSelection(removed);
  for(int i=0;i<4;i++)rows.getJSONObject(i).put("count",2).put("receivedMonotonicNs",200L);
  plots.acceptNative(snapshot,10,slots,slots.frames());assertEquals(3,plots.history.snapshot().size());assertEquals(3,plots.profileSelection().size());
  for(PlotHistory.Series s:plots.history.snapshot()){assertNotEquals(removed,s.id);assertEquals(2,s.points.size());}
 }
}
