package com.lamggm.rospocket;
import org.junit.Test;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class) @org.robolectric.annotation.Config(sdk=31)
public class PlotCsvExporterTest {
 @Test public void historyRetainsOriginalSourceTimestampForEveryExportedPoint()throws Exception {
  PlotHistory h=new PlotHistory(600,30_000_000_000L);h.select("/v\ndata","data",null);
  h.append("/v\ndata",10,1.0,101L);h.append("/v\ndata",20,2.0,202L);
  java.lang.reflect.Field source;
  try{source=PlotHistory.Point.class.getDeclaredField("source");}
  catch(NoSuchFieldException ex){fail("CSV needs original DDS timestamps per point, not only the last ring timestamp");return;}
  assertEquals(101L,source.get(h.snapshot().get(0).points.get(0)));
  assertEquals(202L,source.get(h.snapshot().get(0).points.get(1)));
 }

 @Test public void exportsRetainedValuesGapsAndClocksAsBoundedCsv()throws Exception {
  PlotHistory h=new PlotHistory(600,30_000_000_000L);h.select("/v\ndata","data",null);
  h.append("/v\ndata",10,1.25,101L);h.append("/v\ndata",20,Double.NaN,null);
  Class<?> exporter;
  try{exporter=Class.forName("com.lamggm.rospocket.PlotCsvExporter");}
  catch(ClassNotFoundException ex){fail("User CSV exporter missing");return;}
  java.lang.reflect.Method encode=exporter.getDeclaredMethod("encode",java.util.List.class,boolean.class);
  String csv=new String((byte[])encode.invoke(null,h.snapshot(),true),java.nio.charset.StandardCharsets.UTF_8);
  assertTrue(csv.contains("receive_offset_ns,receive_origin_monotonic_ns,dds_source_timestamp_ns,value,state,break_before"));
  assertTrue(csv.contains(",0,10,101,1.25,finite,true\r\n"));
  assertTrue(csv.contains(",10,10,,,gap,false\r\n"));assertTrue(csv.contains("latest_snapshot,paused"));
 }

 @Test public void protectsTextCellsAndRejectsEmptyOrOverBudgetSnapshots()throws Exception {
  PlotHistory h=new PlotHistory(600,30_000_000_000L);h.select("=SUM(1,2)","data",null);h.append("=SUM(1,2)",1,3.0,null);
  String csv=new String(PlotCsvExporter.encode(h.snapshot(),false),java.nio.charset.StandardCharsets.UTF_8);
  assertTrue("Spreadsheet formulas are text, not executable cells",csv.contains("\"'=SUM(1,2)\""));
  try{PlotCsvExporter.encode(java.util.Collections.emptyList(),false);fail("No points must not produce success");}catch(java.io.IOException expected){}
  String longText=String.join("",java.util.Collections.nCopies(256,"漢"));PlotHistory big=new PlotHistory(2048,300_000_000_000L);
  for(int i=0;i<4;i++){String id="series"+i;big.select(id,longText,null);for(int n=0;n<2048;n++)big.append(id,n,1.0,null);}
  try{PlotCsvExporter.encode(big.snapshot(),false);fail("CSV byte budget must be enforced before provider I/O");}catch(java.io.IOException expected){}
 }

 @Test public void csvBindsTopicAndTypeToTheRecordedReaderNotItsDisplayLabel()throws Exception {
  PlotController c=new PlotController();org.json.JSONObject row=PlotControllerTest.sample(1,100).put("type","std_msgs::msg::dds_::Float64_");
  c.acceptNative(row,1,false);c.select("/value","data");row.put("count",2).put("receivedMonotonicNs",200);c.acceptNative(row,1,false);
  String csv=new String(PlotCsvExporter.encode(c.history.snapshot(),false),java.nio.charset.StandardCharsets.UTF_8);
  assertTrue("Topic/type must be retained with immutable source provenance",csv.contains("\"/value\",\"std_msgs::msg::dds_::Float64_\",\"data\""));
 }
}
