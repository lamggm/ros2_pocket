package com.lamggm.rospocket;
import org.json.*;
import java.util.*;
/** Portable execution of production exporters. Native rows are explicitly synthetic; no draw/device claim. */
public final class GraphProvenanceExports {
 static JSONObject poll(int count,long received,long session,long reader) throws Exception {
  JSONArray rows=new JSONArray();
  for(int i=0;i<4;i++)rows.put(new JSONObject().put("slot",i).put("generation",reader+i)
   .put("subscription","/synthetic/"+i).put("type","std_msgs/msg/Float64")
   .put("readerActive",true).put("fresh",true).put("subscriptionState","receiving").put("error","")
   .put("count",count).put("matched",1).put("hz",2.0).put("age",0.0)
   .put("receivedMonotonicNs",received).put("sourceTimestampNs",JSONObject.NULL)
   .put("numericFields",new JSONArray().put(new JSONObject().put("path","data").put("state","finite").put("value",1.5))));
  return new JSONObject().put("running",true).put("sessionGeneration",session).put("watches",rows);
 }
 public static void main(String[]args)throws Exception {
  WatchSlots slots=new WatchSlots();
  for(int i=0;i<4;i++){WatchSlots.Request r=slots.request(i,"rt/synthetic/"+i,"std_msgs/msg/Float64",false,false);slots.complete(r,"");}
  PlotController plots=new PlotController();WatchSlots.Frame[] frames=slots.frames();
  plots.acceptNative(poll(29,1_000_000_000L,7,10),2,slots,frames);
  for(int i=0;i<2;i++)if(!plots.select("Watch "+(i+1)+" · /synthetic/"+i,"data"))throw new AssertionError("select");
  plots.acceptNative(poll(30,1_500_000_000L,7,10),2,slots,frames);
  JSONObject rows=poll(31,2_000_000_000L,7,10);plots.acceptNative(rows,2,slots,frames);
  List<PlotHistory.Series> frozen=plots.history.snapshot();
  QaSnapshot qa=new QaSnapshot();qa.publish(rows,slots,frames,plots,1,2,2_000_000_100L);
  JSONObject result=new JSONObject().put("qa",qa.export(2_000_000_200L));
  plots.acceptNative(poll(32,2_100_000_000L,7,90),2,slots,frames);
  result.put("afterReaderReplacement",plots.qaSeries(frozen));
  plots.acceptNative(poll(33,2_200_000_000L,8,100),2,slots,frames);
  result.put("afterSessionReplacement",plots.qaSeries(frozen));
  System.out.println(result.toString());
 }
}
