package com.lamggm.rospocket;
import java.util.*;import org.json.*;
/** Presentation-only layout. No topic, reader, network or connection identity. */
final class DashboardLayout {
 static final List<String> IDS=Collections.unmodifiableList(Arrays.asList("watch0","watch1","watch2","watch3","robot","sensors","plot"));
 static final class Item {final String id;final int width,height;Item(String id,int w,int h){this.id=id;width=w;height=h;}}
 final List<Item> items;
 private DashboardLayout(List<Item> values){items=Collections.unmodifiableList(new ArrayList<>(values));}
 static DashboardLayout defaults(){List<Item> a=new ArrayList<>();for(String id:IDS)a.add(new Item(id,id.startsWith("watch")?1:id.equals("robot")?4:2,id.startsWith("watch")?1:id.equals("robot")?3:2));return new DashboardLayout(a);}
 Item get(String id){for(Item i:items)if(i.id.equals(id))return i;throw new IllegalArgumentException("Card desconhecido");}
 static void size(String id,int w,int h){if(!IDS.contains(id)||w<1||w>4||h<1||h>4||!id.startsWith("watch")&&(w<2||h<2))throw new IllegalArgumentException("Tamanho de card inválido");}
 DashboardLayout resize(String id,int w,int h){get(id);size(id,w,h);List<Item> next=new ArrayList<>();for(Item i:items)next.add(i.id.equals(id)?new Item(id,w,h):i);return new DashboardLayout(next);}
 DashboardLayout before(String id,String target){Item moving=get(id);get(target);if(id.equals(target))return this;List<Item> next=new ArrayList<>(items);next.remove(moving);for(int i=0;i<next.size();i++)if(next.get(i).id.equals(target)){next.add(i,moving);break;}return new DashboardLayout(next);}
 String encode(){try{JSONArray rows=new JSONArray();for(Item i:items)rows.put(new JSONObject().put("id",i.id).put("width",i.width).put("height",i.height));return new JSONObject().put("schema",1).put("cards",rows).toString();}catch(JSONException e){throw new IllegalStateException(e);}}
 private static void keys(JSONObject j,String... names){Set<String> wanted=new HashSet<>(Arrays.asList(names));Iterator<String> k=j.keys();while(k.hasNext())if(!wanted.remove(k.next()))throw new IllegalArgumentException("Campo de layout desconhecido");if(!wanted.isEmpty())throw new IllegalArgumentException("Layout incompleto");}
 private static int integer(Object value){if(!(value instanceof Integer||value instanceof Long))throw new IllegalArgumentException("Inteiro de layout inválido");long n=((Number)value).longValue();if(n<1||n>4)throw new IllegalArgumentException("Tamanho de layout inválido");return (int)n;}
 static DashboardLayout decode(String text){try{if(text==null||text.length()>4096)throw new IllegalArgumentException("Layout excessivo");JSONObject j=ProfileJson.parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));keys(j,"schema","cards");if(integer(j.get("schema"))!=1)throw new IllegalArgumentException("Versão de layout desconhecida");JSONArray cards=j.getJSONArray("cards");if(cards.length()!=IDS.size())throw new IllegalArgumentException("População de cards inválida");Set<String> seen=new HashSet<>();List<Item> out=new ArrayList<>();for(int i=0;i<cards.length();i++){JSONObject row=cards.getJSONObject(i);keys(row,"id","width","height");Object value=row.get("id");if(!(value instanceof String)||!seen.add((String)value))throw new IllegalArgumentException("Card inválido ou duplicado");String id=(String)value;int w=integer(row.get("width")),h=integer(row.get("height"));size(id,w,h);out.add(new Item(id,w,h));}return new DashboardLayout(out);}catch(Exception e){throw new IllegalArgumentException("Layout inválido",e);}}
}
