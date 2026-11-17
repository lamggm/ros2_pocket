package com.lamggm.rospocket;
import java.util.*;import org.json.*;
/** Presentation-only layout. No topic, reader, network or connection identity. */
final class DashboardLayout {
 static final List<String> IDS=Collections.unmodifiableList(Arrays.asList("watch0","watch1","watch2","watch3","robot","sensors","plot"));
 static final class Display {
  final String mode,path,unit;final double min,max;final int decimals;
  Display(String mode,String path,double min,double max,String unit){this(mode,path,min,max,unit,-1);}
  Display(String mode,String path,double min,double max,String unit,int decimals){
   if(decimals< -1||decimals>8)throw new IllegalArgumentException("Precisão: automática ou 0–8 casas");this.decimals=decimals;
   if(!Arrays.asList("number","gauge","bar").contains(mode)||path==null||path.length()>256||!path.matches("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*|\\[[0-9]{1,2}\\])*")||unit==null||unit.length()>24||unit.codePoints().anyMatch(Character::isISOControl)||!Double.isFinite(min)||!Double.isFinite(max)||min>=max||!Double.isFinite(max-min))throw new IllegalArgumentException("Visualização inválida: campo, escala ou unidade");
   this.mode=mode;this.path=path;this.min=min;this.max=max;this.unit=unit;
  }
  static Display defaults(){return new Display("number","data",0,100,"");}
  JSONObject json()throws JSONException{return new JSONObject().put("mode",mode).put("path",path).put("min",min).put("max",max).put("unit",unit).put("decimals",decimals);}
 }
 static final int MAX_COLUMNS=32,MAX_ROWS=64;
 static final class Item {
  final String id,name;final int width,height,column,row;final Display display;
  Item(String id,int w,int h){this(id,w,h,Display.defaults(),"");}
  Item(String id,int w,int h,Display display){this(id,w,h,display,"");}
  Item(String id,int w,int h,Display display,String name){this(id,w,h,display,name,-1,-1);}
  Item(String id,int w,int h,Display display,String name,int column,int row){
   if((column==-1)!=(row==-1)||column< -1||column>=MAX_COLUMNS||row< -1||row>=MAX_ROWS)throw new IllegalArgumentException("Posição inválida");
   if(name==null||name.length()>64||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Nome de card inválido: máximo 64 caracteres, sem controles");
   this.id=id;this.name=name;width=w;height=h;this.display=Objects.requireNonNull(display);this.column=column;this.row=row;
  }
 }
 final List<Item> items;final int columns;
 private DashboardLayout(List<Item> values){this(values,0);}
 private DashboardLayout(List<Item> values,int columns){this.columns=columns;items=Collections.unmodifiableList(new ArrayList<>(values));}
 private static boolean overlaps(Item a,Item b,int columns){int aw=Math.min(a.width,columns),bw=Math.min(b.width,columns);return a.column<b.column+bw&&a.column+aw>b.column&&a.row<b.row+b.height&&a.row+a.height>b.row;}
 private void validatePositions(){for(int i=0;i<items.size();i++){Item a=items.get(i);if(columns==0){if(a.column!=-1||a.row!=-1)throw new IllegalArgumentException("Grade automática com posição");continue;}if(a.column<0||a.row<0||a.column+Math.min(a.width,columns)>columns||a.row+a.height>MAX_ROWS)throw new IllegalArgumentException("Posição fora da grade");for(int k=0;k<i;k++)if(overlaps(a,items.get(k),columns))throw new IllegalArgumentException("Cards sobrepostos");}}
 private static int bounded(Object v,int min,int max){if(!(v instanceof Integer||v instanceof Long))throw new IllegalArgumentException("Coordenada inteira obrigatória");long n=((Number)v).longValue();if(n<min||n>max)throw new IllegalArgumentException("Coordenada fora do limite");return (int)n;}

 private static Item located(Item i,int column,int row){return new Item(i.id,i.width,i.height,i.display,i.name,column,row);}
 private static boolean free(Item candidate,List<Item> placed,int columns){if(candidate.column<0||candidate.row<0||candidate.column+Math.min(candidate.width,columns)>columns||candidate.row+candidate.height>MAX_ROWS)return false;for(Item p:placed)if(overlaps(candidate,p,columns))return false;return true;}
 DashboardLayout project(int columns){
  if(columns<1||columns>MAX_COLUMNS)throw new IllegalArgumentException("Largura de grade inválida");List<Item> placed=new ArrayList<>();
  for(Item i:items){Item candidate=located(i,Math.min(Math.max(0,i.column),columns-Math.min(i.width,columns)),Math.min(Math.max(0,i.row),MAX_ROWS-i.height));if(i.column<0||!free(candidate,placed,columns)){candidate=null;search:for(int y=0;y<=MAX_ROWS-i.height;y++)for(int x=0;x<=columns-Math.min(i.width,columns);x++){Item next=located(i,x,y);if(free(next,placed,columns)){candidate=next;break search;}}}if(candidate==null)throw new IllegalArgumentException("Grade sem espaço");placed.add(candidate);}return new DashboardLayout(placed,columns);
 }
 DashboardLayout position(String id,int column,int row){if(columns==0)throw new IllegalArgumentException("Grade ainda automática");get(id);List<Item> next=new ArrayList<>();for(Item i:items)next.add(i.id.equals(id)?located(i,column,row):i);DashboardLayout value=new DashboardLayout(next,columns);value.validatePositions();return value;}
 static DashboardLayout defaults(){List<Item> a=new ArrayList<>();for(String id:IDS)a.add(new Item(id,id.startsWith("watch")?1:id.equals("robot")?4:2,id.startsWith("watch")?1:id.equals("robot")?3:2));return new DashboardLayout(a);}
 Item get(String id){for(Item i:items)if(i.id.equals(id))return i;throw new IllegalArgumentException("Card desconhecido");}
 static void size(String id,int w,int h){if(!IDS.contains(id)||w<1||w>4||h<1||h>4||!id.startsWith("watch")&&(w<2||h<2))throw new IllegalArgumentException("Tamanho de card inválido");}
 DashboardLayout resize(String id,int w,int h){get(id);size(id,w,h);List<Item> next=new ArrayList<>();for(Item i:items)next.add(i.id.equals(id)?new Item(id,w,h,i.display,i.name,i.column,i.row):i);DashboardLayout value=new DashboardLayout(next,columns);return columns==0?value:value.project(columns);}
 DashboardLayout before(String id,String target){Item moving=get(id);get(target);if(id.equals(target))return this;List<Item> next=new ArrayList<>(items);next.remove(moving);for(int i=0;i<next.size();i++)if(next.get(i).id.equals(target)){next.add(i,moving);break;}if(columns==0)return new DashboardLayout(next);List<Item> automatic=new ArrayList<>();for(Item i:next)automatic.add(located(i,-1,-1));return new DashboardLayout(automatic).project(columns);}
 DashboardLayout display(String id,Display display){if(!get(id).id.startsWith("watch"))throw new IllegalArgumentException("Visualização só nas leituras");List<Item> next=new ArrayList<>();for(Item i:items)next.add(i.id.equals(id)?new Item(id,i.width,i.height,display,i.name,i.column,i.row):i);return new DashboardLayout(next,columns);}
 DashboardLayout rename(String id,String name){get(id);List<Item> next=new ArrayList<>();for(Item i:items)next.add(i.id.equals(id)?new Item(id,i.width,i.height,i.display,name,i.column,i.row):i);return new DashboardLayout(next,columns);}
 String encode(){try{JSONArray rows=new JSONArray();for(Item i:items){JSONObject r=new JSONObject().put("id",i.id).put("width",i.width).put("height",i.height).put("name",i.name).put("column",i.column).put("row",i.row);if(i.id.startsWith("watch"))r.put("display",i.display.json());rows.put(r);}return new JSONObject().put("schema",5).put("columns",columns).put("cards",rows).toString();}catch(JSONException e){throw new IllegalStateException(e);}}
 private static void keys(JSONObject j,String... names){Set<String> wanted=new HashSet<>(Arrays.asList(names));Iterator<String> k=j.keys();while(k.hasNext())if(!wanted.remove(k.next()))throw new IllegalArgumentException("Campo de layout desconhecido");if(!wanted.isEmpty())throw new IllegalArgumentException("Layout incompleto");}
 private static int integer(Object value){if(!(value instanceof Integer||value instanceof Long))throw new IllegalArgumentException("Inteiro de layout inválido");long n=((Number)value).longValue();if(n<1||n>4)throw new IllegalArgumentException("Tamanho de layout inválido");return (int)n;}
 static DashboardLayout decode(String text){try{
  if(text==null||text.length()>4096)throw new IllegalArgumentException("Layout excessivo");JSONObject j=ProfileJson.parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));int schema=bounded(j.get("schema"),1,5);if(schema>=4)keys(j,"schema","columns","cards");else keys(j,"schema","cards");int columns=schema>=4?bounded(j.get("columns"),0,MAX_COLUMNS):0;if(schema<1||schema>5)throw new IllegalArgumentException("Versão de layout desconhecida");JSONArray cards=j.getJSONArray("cards");if(cards.length()!=IDS.size())throw new IllegalArgumentException("População de cards inválida");Set<String> seen=new HashSet<>();List<Item> out=new ArrayList<>();
  for(int i=0;i<cards.length();i++){
   JSONObject row=cards.getJSONObject(i);Object value=row.get("id");if(!(value instanceof String)||!seen.add((String)value))throw new IllegalArgumentException("Card inválido ou duplicado");String id=(String)value;boolean visual=schema>=2&&id.startsWith("watch");List<String> fields=new ArrayList<>(Arrays.asList("id","width","height"));if(visual&&row.has("display"))fields.add("display");if(schema>=3)fields.add("name");if(schema>=4){fields.add("column");fields.add("row");}keys(row,fields.toArray(new String[0]));int w=integer(row.get("width")),h=integer(row.get("height"));size(id,w,h);String name="";if(schema>=3){Object n=row.get("name");if(!(n instanceof String))throw new IllegalArgumentException("Tipo de nome inválido");name=(String)n;}
   Display display=Display.defaults();if(visual&&row.has("display")){JSONObject d=row.getJSONObject("display");if(schema>=5)keys(d,"mode","path","min","max","unit","decimals");else keys(d,"mode","path","min","max","unit");if(!(d.get("mode") instanceof String)||!(d.get("path") instanceof String)||!(d.get("unit") instanceof String)||!(d.get("min") instanceof Number)||!(d.get("max") instanceof Number))throw new IllegalArgumentException("Tipo de visualização inválido");display=new Display(d.getString("mode"),d.getString("path"),d.getDouble("min"),d.getDouble("max"),d.getString("unit"),schema>=5?bounded(d.get("decimals"),-1,8):-1);}out.add(new Item(id,w,h,display,name,schema>=4?bounded(row.get("column"),-1,MAX_COLUMNS-1):-1,schema>=4?bounded(row.get("row"),-1,MAX_ROWS-1):-1));
  }DashboardLayout result=new DashboardLayout(out,columns);result.validatePositions();return result;
 }catch(Exception e){throw new IllegalArgumentException("Layout inválido",e);}}
}
