package com.lamggm.rospocket;
import org.json.*;import java.util.*;
/** Portable configuration only. Empty map wire means no selected map reader. */
final class LocationConfig {
 final String wire,robotFrame,dynamicTf,staticTf;final boolean reliable,retained;
 LocationConfig(){this("","base_link","rt/tf","rt/tf_static",true,true);}
 LocationConfig(String w,String base,String dynamic,String fixed,boolean r,boolean t){wire=topic(w,true);robotFrame=frame(base);dynamicTf=topic(dynamic,true);staticTf=topic(fixed,true);if(!dynamic.isEmpty()&&dynamic.equals(fixed))throw new IllegalArgumentException("Tópicos TF devem ser distintos");reliable=r;retained=t;}
 static String topic(String s,boolean empty){if(s==null||s.length()>256||(!s.isEmpty()&&!s.matches("rt/[A-Za-z_][A-Za-z_0-9]*(/[A-Za-z_][A-Za-z_0-9]*)*"))||!empty&&s.isEmpty())throw new IllegalArgumentException("Tópico de localização inválido");return s;}
 static String frame(String s){if(s==null||s.isEmpty()||s.length()>256||s.startsWith("/")||s.chars().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IllegalArgumentException("Frame do robô inválido");return s;}
 JSONObject json()throws JSONException{return new JSONObject().put("wire",wire).put("robotFrame",robotFrame).put("dynamicTf",dynamicTf).put("staticTf",staticTf).put("reliable",reliable).put("retained",retained);}
 static LocationConfig decode(JSONObject j)throws JSONException{Set<String> keys=new HashSet<>();Iterator<String> it=j.keys();while(it.hasNext())keys.add(it.next());if(!keys.equals(new HashSet<>(Arrays.asList("wire","robotFrame","dynamicTf","staticTf","reliable","retained"))))throw new IllegalArgumentException("Configuração de localização incompleta/desconhecida");for(String k:new String[]{"wire","robotFrame","dynamicTf","staticTf"})if(!(j.get(k) instanceof String))throw new IllegalArgumentException("Texto de localização inválido");if(!(j.get("reliable") instanceof Boolean)||!(j.get("retained") instanceof Boolean))throw new IllegalArgumentException("QoS de mapa inválido");return new LocationConfig(j.getString("wire"),j.getString("robotFrame"),j.getString("dynamicTf"),j.getString("staticTf"),j.getBoolean("reliable"),j.getBoolean("retained"));}
}
