package com.lamggm.rospocket;
import java.util.*;import org.json.*;
/** Presentation-only layout. No topic, reader, network or connection identity. */
final class DashboardLayout {
 static final List<String> IDS=Collections.unmodifiableList(Arrays.asList("watch0","watch1","watch2","watch3","robot","sensors","plot"));
 static final class Display {
  final String mode,path,unit;final double min,max;
  Display(String mode,String path,double min,double max,String unit){
   if(!Arrays.asList("number","gauge","bar").contains(mode)||path==null||path.length()>256||!path.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*|\\[[0-9]{1,2}\\])*")||unit==null||unit.length()>24||unit.codePoints().anyMatch(Character::isISOControl)||!Double.isFinite(min)||!Double.isFinite(max)||min>=max||!Double.isFinite(max-min))throw new IllegalArgumentException("Visualização inválida: campo, escala ou unidade");
   this.mode=mode;this.path=path;this.min=min;this.max=max;this.unit=unit;
  }
  static Display defaults(){return new Display("number","data",0,100,"");}
  JSONObject json()throws JSONException{return new JSONObject().put("mode",mode).put("path",path).put("min",min).put("max",max).put("unit",unit);}
 }
 static final class Item {
  final String id,name;final int width,height;final Display display;
  Item(String id,int w,int h){this(id,w,h,Display.defaults(),"");}
  Item(String id,int w,int h,Display display){this(id,w,h,display,"");}
  Item(String id,int w,int h,Display display,String name){
   if(name==null||name.length()>64||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Nome de card inválido: máximo 64 caracteres, sem controles");
   this.id=id;this.name=name;width=w;height=h;this.display=Objects.requireNonNull(display);
  }
 }
 final List<Item> items;
 private DashboardLayout(List<Item> values){items=Collections.unmodifiableList(new ArrayList<>(values));}
 static DashboardLayout defaults(){List<Item> a=new ArrayList<>();for(String id:IDS)a.add(new Item(id,id.startsWith("watch")?1:id.equals("robot")?4:2,id.startsWith("watch")?1:id.equals("robot")?3:2));return new DashboardLayout(a);}
 Item get(String id){for(Item i:items)if(i.id.equals(id))return i;throw new IllegalArgumentException("Card desconhecido");}
 static void size(String id,int w,int h){if(!IDS.contains(id)||w<1||w>4||h<1||h>4||!id.startsWith("watch")&&(w<2||h<2))throw new IllegalArgumentException("Tamanho de card inválido");}
 DashboardLayout resize(String id,int w,int h){get(id);size(id,w,h);List<Item> next=new ArrayList<>();for(Item i:items)next.add(i.id.equals(id)?new Item(id,w,h,i.display,i.name):i);return new DashboardLayout(next);}
 DashboardLayout before(String id,String target){Item moving=get(id);get(target);if(id.equals(target))return this;List<Item> next=new ArrayList<>(items);next.remove(moving);for(int i=0;i<next.size();i++)if(next.get(i).id.equals(target)){next.add(i,moving);break;}return new DashboardLayout(next);}
 DashboardLayout display(String id,Display display){if(!get(id).id.startsWith("watch"))throw new IllegalArgumentException("Visualização só nas leituras");List<Item> next=new ArrayList<>();for(Item i:items)next.add(i.id.equals(id)?new Item(id,i.width,i.height,display,i.name):i);return new DashboardLayout(next);}
 DashboardLayout rename(String id,String name){get(id);List<Item> next=new ArrayList<>();for(Item i:items)next.add(i.id.equals(id)?new Item(id,i.width,i.height,i.display,name):i);return new DashboardLayout(next);}
 String encode(){try{JSONArray rows=new JSONArray();for(Item i:items){JSONObject r=new JSONObject().put("id",i.id).put("width",i.width).put("height",i.height).put("name",i.name);if(i.id.startsWith("watch"))r.put("display",i.display.json());rows.put(r);}return new JSONObject().put("schema",3).put("cards",rows).toString();}catch(JSONException e){throw new IllegalStateException(e);}}
 private static void keys(JSONObject j,String... names){Set<String> wanted=new HashSet<>(Arrays.asList(names));Iterator<String> k=j.keys();while(k.hasNext())if(!wanted.remove(k.next()))throw new IllegalArgumentException("Campo de layout desconhecido");if(!wanted.isEmpty())throw new IllegalArgumentException("Layout incompleto");}
 private static int integer(Object value){if(!(value instanceof Integer||value instanceof Long))throw new IllegalArgumentException("Inteiro de layout inválido");long n=((Number)value).longValue();if(n<1||n>4)throw new IllegalArgumentException("Tamanho de layout inválido");return (int)n;}
 static DashboardLayout decode(String text){try{
  if(text==null||text.length()>4096)throw new IllegalArgumentException("Layout excessivo");JSONObject j=ProfileJson.parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));keys(j,"schema","cards");int schema=integer(j.get("schema"));if(schema<1||schema>3)throw new IllegalArgumentException("Versão de layout desconhecida");JSONArray cards=j.getJSONArray("cards");if(cards.length()!=IDS.size())throw new IllegalArgumentException("População de cards inválida");Set<String> seen=new HashSet<>();List<Item> out=new ArrayList<>();
  for(int i=0;i<cards.length();i++){
   JSONObject row=cards.getJSONObject(i);Object value=row.get("id");if(!(value instanceof String)||!seen.add((String)value))throw new IllegalArgumentException("Card inválido ou duplicado");String id=(String)value;boolean visual=schema>=2&&id.startsWith("watch");List<String> fields=new ArrayList<>(Arrays.asList("id","width","height"));if(visual&&row.has("display"))fields.add("display");if(schema==3)fields.add("name");keys(row,fields.toArray(new String[0]));int w=integer(row.get("width")),h=integer(row.get("height"));size(id,w,h);String name="";if(schema==3){Object n=row.get("name");if(!(n instanceof String))throw new IllegalArgumentException("Tipo de nome inválido");name=(String)n;}
   Display display=Display.defaults();if(visual&&row.has("display")){JSONObject d=row.getJSONObject("display");keys(d,"mode","path","min","max","unit");if(!(d.get("mode") instanceof String)||!(d.get("path") instanceof String)||!(d.get("unit") instanceof String)||!(d.get("min") instanceof Number)||!(d.get("max") instanceof Number))throw new IllegalArgumentException("Tipo de visualização inválido");display=new Display(d.getString("mode"),d.getString("path"),d.getDouble("min"),d.getDouble("max"),d.getString("unit"));}out.add(new Item(id,w,h,display,name));
  }return new DashboardLayout(out);
 }catch(Exception e){throw new IllegalArgumentException("Layout inválido",e);}}
}
