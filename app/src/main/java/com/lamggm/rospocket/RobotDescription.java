package com.lamggm.rospocket;

import android.util.Xml;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;

/** Static URDF reference pose only. Never fetches a URI or executes xacro. */
final class RobotDescription {
 static final int MAX_BYTES=512*1024,MAX_LINKS=128,MAX_VISUALS=256;
 static final class Visual {
  final String kind,mesh,link;final double[] localTransform;final double[] size,transform;final int color;final boolean materialOverride;
  Visual(String k,String file,double[] s,double[] m,int c,boolean override){this(k,file,s,m,c,override,"",m);}
  Visual(String k,String file,double[] s,double[] m,int c,boolean override,String id,double[] local){link=id;localTransform=local.clone();kind=k;mesh=file;size=s.clone();transform=m.clone();color=c;materialOverride=override;}
 }
 final String name,fingerprint,rootLink;final Map<String,double[]> linkTransforms;final List<Visual> visuals;final List<String> missingMeshes;
 private RobotDescription(String n,String hash,List<Visual> v,Set<String> missing,String root,Map<String,double[]> transforms){rootLink=root;linkTransforms=Collections.unmodifiableMap(new HashMap<>(transforms));name=n;fingerprint=hash;visuals=Collections.unmodifiableList(new ArrayList<>(v));missingMeshes=Collections.unmodifiableList(new ArrayList<>(missing));}
 private static final class Element {
  String tag;final Map<String,String> attrs=new HashMap<>();final List<Element> children=new ArrayList<>();
  String attr(String key){String v=attrs.get(key);if(v==null||v.isEmpty())throw bad("Atributo ausente: "+key);return v;}
  Element child(String name){Element found=null;for(Element e:children)if(name.equals(e.tag)){if(found!=null)throw bad("Elemento duplicado: "+name);found=e;}return found;}
 }
 static IllegalArgumentException bad(String message){return new IllegalArgumentException(message);}
 static RobotDescription parse(String xml){
  if(xml==null||xml.isEmpty()||xml.length()>MAX_BYTES||xml.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw bad("Descrição ausente ou maior que 512 KiB");
  String upper=xml.toUpperCase(Locale.ROOT);if(upper.contains("<!DOCTYPE")||upper.contains("<!ENTITY"))throw bad("DTD e entidades externas não são aceitas");
  try{
   XmlPullParser p=Xml.newPullParser();p.setInput(new StringReader(xml));ArrayDeque<Element> stack=new ArrayDeque<>();Element root=null;int count=0;
   for(int event=p.nextToken();event!=XmlPullParser.END_DOCUMENT;event=p.nextToken()){
    if(event==XmlPullParser.DOCDECL)throw bad("DTD não é aceita");
    if(event==XmlPullParser.START_TAG){
     if(++count>4096||stack.size()>=48||p.getAttributeCount()>24)throw bad("Complexidade XML excedida");
     Element e=new Element();e.tag=p.getName();if(e.tag.indexOf(':')>=0)throw bad("Forneça URDF expandido, não xacro");
     for(int i=0;i<p.getAttributeCount();i++)e.attrs.put(p.getAttributeName(i),p.getAttributeValue(i));
     if(stack.isEmpty()){if(root!=null)throw bad("Mais de uma raiz XML");root=e;}else stack.peek().children.add(e);stack.push(e);
    }else if(event==XmlPullParser.END_TAG){if(stack.isEmpty())throw bad("Estrutura XML inválida");stack.pop();}
   }
   if(root==null||!"robot".equals(root.tag)||!stack.isEmpty())throw bad("Raiz robot ausente");String name=root.attr("name");if(name.trim().isEmpty()||name.length()>256)throw bad("Nome do robô inválido");
   Map<String,Element> links=new LinkedHashMap<>();Map<String,Integer> materials=new HashMap<>();List<Element> joints=new ArrayList<>();
   for(Element e:root.children){if("link".equals(e.tag)){String id=e.attr("name");if(links.put(id,e)!=null||links.size()>MAX_LINKS)throw bad("Links duplicados ou limite de 128 links excedido");}else if("joint".equals(e.tag))joints.add(e);else if("material".equals(e.tag))materials.put(e.attr("name"),color(e,0xff90adb8));}
   if(links.isEmpty()||joints.size()>MAX_LINKS)throw bad("Árvore de links ausente ou excessiva");
   Map<String,String> parents=new HashMap<>();Map<String,double[]> jointOrigins=new HashMap<>();Set<String> jointNames=new HashSet<>();
   for(Element joint:joints){if(!jointNames.add(joint.attr("name")))throw bad("Junta duplicada");String type=joint.attr("type");if(!Arrays.asList("fixed","revolute","continuous","prismatic","floating","planar").contains(type))throw bad("Tipo de junta desconhecido");Element a=joint.child("parent"),b=joint.child("child");if(a==null||b==null)throw bad("Junta sem parent/child");String parent=a.attr("link"),child=b.attr("link");if(!links.containsKey(parent)||!links.containsKey(child)||parent.equals(child)||parents.put(child,parent)!=null)throw bad("Associação de juntas inválida");jointOrigins.put(child,origin(joint.child("origin")));}
   int roots=0;for(String id:links.keySet())if(!parents.containsKey(id))roots++;if(roots!=1)throw bad("URDF deve conter uma única árvore");
   Map<String,double[]> world=new HashMap<>();for(String id:links.keySet())world(id,parents,jointOrigins,world,new HashSet<>());
   List<Visual> visuals=new ArrayList<>();Set<String> missing=new LinkedHashSet<>();
   for(Map.Entry<String,Element> link:links.entrySet())for(Element visual:link.getValue().children)if("visual".equals(visual.tag)){
    if(visuals.size()>=MAX_VISUALS)throw bad("Limite de 256 elementos visuais excedido");Element geometry=visual.child("geometry");if(geometry==null||geometry.children.size()!=1)throw bad("Geometria visual ausente ou ambígua");Element shape=geometry.children.get(0);double[] size;String mesh="";
    switch(shape.tag){case "box":size=numbers(shape.attr("size"),3);for(double d:size)positive(d);break;case "sphere":size=new double[]{positive(number(shape.attr("radius")))};break;case "cylinder":size=new double[]{positive(number(shape.attr("radius"))),positive(number(shape.attr("length")))};break;case "mesh":mesh=shape.attr("filename");if(mesh.length()>4096)throw bad("Referência de malha excessiva");size=shape.attrs.containsKey("scale")?numbers(shape.attr("scale"),3):new double[]{1,1,1};for(double v:size)if(v==0||Math.abs(v)>1000)throw bad("Escala de malha inválida");missing.add(mesh);break;default:throw bad("Geometria URDF não suportada");}
    Element material=visual.child("material");int rgba=0xff90adb8;if(material!=null){String ref=material.attrs.get("name");rgba=color(material,materials.getOrDefault(ref,rgba));}
    boolean override=material!=null&&(material.child("color")!=null||materials.containsKey(material.attrs.get("name")));
    visuals.add(new Visual(shape.tag,mesh,size,multiply(world.get(link.getKey()),origin(visual.child("origin"))),rgba,override,link.getKey(),origin(visual.child("origin"))));
   }
   return new RobotDescription(name,RobotGlb.sha(xml.getBytes(StandardCharsets.UTF_8)),visuals,missing,links.keySet().stream().filter(id->!parents.containsKey(id)).findFirst().get(),world);
  }catch(IllegalArgumentException ex){throw ex;}catch(Exception ex){throw bad("URDF XML inválido");}
 }
 private static double[] world(String id,Map<String,String> parents,Map<String,double[]> origins,Map<String,double[]> ready,Set<String> active){
  if(ready.containsKey(id))return ready.get(id);if(!active.add(id))throw bad("Ciclo entre links");String parent=parents.get(id);double[] m=parent==null?identity():multiply(world(parent,parents,origins,ready,active),origins.get(id));active.remove(id);ready.put(id,m);return m;
 }
 private static double number(String v){try{double n=Double.parseDouble(v);if(!Double.isFinite(n)||Math.abs(n)>10000)throw bad("Grandeza não finita ou excessiva");return n;}catch(NumberFormatException ex){throw bad("Número URDF inválido");}}
 private static double positive(double d){if(d<=0||d>1000)throw bad("Dimensão visual inválida");return d;}
 private static double[] numbers(String value,int n){String[] tokens=value.trim().split("\\s+");if(tokens.length!=n)throw bad("Número de componentes inválido");double[] out=new double[n];for(int i=0;i<n;i++)out[i]=number(tokens[i]);return out;}
 private static int color(Element material,int fallback){Element e=material.child("color");if(e==null)return fallback;double[] c=numbers(e.attr("rgba"),4);int out=0;for(double v:c)if(v<0||v>1)throw bad("Cor RGBA inválida");out|=((int)Math.round(c[3]*255))<<24;out|=((int)Math.round(c[0]*255))<<16;out|=((int)Math.round(c[1]*255))<<8;out|=(int)Math.round(c[2]*255);return out;}
 static double[] identity(){return new double[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};}
 static double[] multiply(double[] a,double[] b){double[] out=new double[16];for(int r=0;r<4;r++)for(int c=0;c<4;c++)for(int k=0;k<4;k++)out[r*4+c]+=a[r*4+k]*b[k*4+c];return out;}
 static double[] point(double[] m,double x,double y,double z){return new double[]{m[0]*x+m[1]*y+m[2]*z+m[3],m[4]*x+m[5]*y+m[6]*z+m[7],m[8]*x+m[9]*y+m[10]*z+m[11]};}
 private static double[] origin(Element e){if(e==null)return identity();double[] t=e.attrs.containsKey("xyz")?numbers(e.attr("xyz"),3):new double[3],r=e.attrs.containsKey("rpy")?numbers(e.attr("rpy"),3):new double[3];double cr=Math.cos(r[0]),sr=Math.sin(r[0]),cp=Math.cos(r[1]),sp=Math.sin(r[1]),cy=Math.cos(r[2]),sy=Math.sin(r[2]);return new double[]{cy*cp,cy*sp*sr-sy*cr,cy*sp*cr+sy*sr,t[0],sy*cp,sy*sp*sr+cy*cr,sy*sp*cr-cy*sr,t[1],-sp,cp*sr,cp*cr,t[2],0,0,0,1};}
}
