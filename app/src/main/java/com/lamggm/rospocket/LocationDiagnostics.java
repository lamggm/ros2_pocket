package com.lamggm.rospocket;
import org.json.*;
/** Only compare advertised map-writer policies; Watch 1 compatibility is unrelated. */
final class LocationDiagnostics {
 static String offers(LocationConfig c,JSONArray topics){
  if(c.wire.isEmpty())return "Nenhum mapa selecionado. Use Fontes e ajustes ou Explorar.";
  if(topics==null)return "Descoberta não disponível nesta sessão · QoS ofertado desconhecido.";
  StringBuilder out=new StringBuilder();boolean found=false;
  for(int i=0;i<Math.min(1024,topics.length());i++){
   JSONObject topic=topics.optJSONObject(i);if(topic==null||!c.wire.equals(topic.optString("wireName")))continue;found=true;
   if(!MapGrid.TYPE.equals(topic.optString("wireType"))){out.append("Tipo anunciado diverge de OccupancyGrid: ").append(topic.optString("wireType")).append('\n');continue;}
   JSONArray endpoints=topic.optJSONArray("endpoints");int writers=0;
   if(endpoints!=null)for(int n=0;n<Math.min(128,endpoints.length());n++){
    JSONObject e=endpoints.optJSONObject(n);if(e==null||!e.optBoolean("writer"))continue;writers++;JSONObject q=e.optJSONObject("qos");
    if(q==null){out.append("QoS ofertado desconhecido.\n");continue;}
    String rel=q.optString("reliability","unknown"),dur=q.optString("durability","unknown");boolean mismatch=c.reliable&&"best_effort".equals(rel)||c.retained&&"volatile".equals(dur);
    out.append(mismatch?"Oferta incompatível observada: ":"Oferta observada (não garante entrega): ").append(rel).append(" / ").append(dur).append('\n');
   }
   if(writers==0)out.append("Nenhum publicador anunciado nesta sessão · QoS ofertado desconhecido.\n");
  }
  if(!found)return "Tópico ainda não anunciado nesta sessão · QoS ofertado desconhecido. Não comprova ausência no robô.";
  return out.toString().trim();
 }
}
