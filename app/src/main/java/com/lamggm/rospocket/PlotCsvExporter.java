package com.lamggm.rospocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** User CSV of retained binary64 plot snapshots, not a message recorder or QA dump. */
final class PlotCsvExporter {
 static final int MAX_BYTES=2*1024*1024;
 static byte[] encode(List<PlotHistory.Series> series,boolean paused)throws IOException {
  if(series==null||series.size()>PlotHistory.MAX_SERIES)throw new IOException("Limite de séries excedido");
  long origin=Long.MAX_VALUE;
  for(PlotHistory.Series s:series){
   if(s.points.size()>2048)throw new IOException("Limite de pontos excedido");
   for(PlotHistory.Point p:s.points)origin=Math.min(origin,p.time);
  }
  if(origin==Long.MAX_VALUE)throw new IOException("Selecione séries com pontos antes de exportar");
  ByteArrayOutputStream out=new ByteArrayOutputStream();
  row(out,"format_version,capture_mode,display_mode,series_id,slot,session_generation,reader_generation,topic,type,field,unit,receive_offset_ns,receive_origin_monotonic_ns,dds_source_timestamp_ns,value,state,break_before");
  for(PlotHistory.Series s:series)for(PlotHistory.Point p:s.points){
   row(out,"1,latest_snapshot,"+(paused?"paused":"live")+","+text(s.id)+","+s.provenance.slot+","+s.provenance.session+","+s.provenance.reader+","+text(s.provenance.topic)+","+text(s.provenance.type)+","+text(s.path)+","+text(s.unit)+","+(p.time-origin)+","+origin+","+(p.source==null?"":p.source)+","+(p.value==null?"":Double.toString(p.value))+","+(p.value==null?"gap":"finite")+","+p.breakBefore);
  }
  return out.toByteArray();
 }
 private static void row(ByteArrayOutputStream out,String value)throws IOException{
  byte[] bytes=(value+"\r\n").getBytes(StandardCharsets.UTF_8);
  if(bytes.length>MAX_BYTES-out.size())throw new IOException("CSV excede 2 MiB; reduza o histórico");
  out.write(bytes);
 }
 private static String text(String value){
  if(value==null)return "";
  String trimmed=value.trim();
  if(!trimmed.isEmpty()&&"=+-@".indexOf(trimmed.charAt(0))>=0)value="'"+value;
  return "\""+value.replace("\"","\"\"")+"\"";
 }
}
