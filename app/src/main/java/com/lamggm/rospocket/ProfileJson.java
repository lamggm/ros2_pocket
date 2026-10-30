package com.lamggm.rospocket;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.*;
import org.json.*;

/** Strict bounded admission before Android's deliberately lenient JSON parser. */
final class ProfileJson {
 private static final Pattern NUMBER=Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
 private final String text;private int at;
 private ProfileJson(String text){this.text=text;}
 static JSONObject parse(byte[] bytes)throws JSONException {
  if(bytes==null||bytes.length>BenchProfiles.MAX_BYTES)throw bad();
  final String text;
  try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
  catch(CharacterCodingException ex){throw bad();}
  ProfileJson parser=new ProfileJson(text);parser.value(0);parser.space();
  if(parser.at!=text.length())throw bad();
  Object root=new JSONTokener(text).nextValue();if(!(root instanceof JSONObject))throw bad();
  return (JSONObject)root;
 }
 private static IllegalArgumentException bad(){return new IllegalArgumentException("JSON de perfil inválido, duplicado ou fora dos limites (128 KiB / profundidade 12)");}
 private void space(){while(at<text.length()&&" \t\r\n".indexOf(text.charAt(at))>=0)at++;}
 private boolean take(char c){space();if(at<text.length()&&text.charAt(at)==c){at++;return true;}return false;}
 private void require(char c){if(!take(c))throw bad();}
 private void value(int depth){
  space();if(at>=text.length())throw bad();char c=text.charAt(at);
  if(c=='{'||c=='['){if(depth>=12)throw bad();at++;
   if(c=='{'){
    Set<String> keys=new HashSet<>();if(take('}'))return;
    do{space();String key=string();if(!keys.add(key))throw bad();require(':');value(depth+1);}while(take(','));require('}');
   }else{if(take(']'))return;do{value(depth+1);}while(take(','));require(']');}
  }else if(c=='"')string();
  else if(text.startsWith("true",at))at+=4;
  else if(text.startsWith("false",at))at+=5;
  else if(text.startsWith("null",at))at+=4;
  else{Matcher m=NUMBER.matcher(text);m.region(at,text.length());if(!m.lookingAt())throw bad();at=m.end();}
 }
 private String string(){
  if(at>=text.length()||text.charAt(at++)!='"')throw bad();StringBuilder out=new StringBuilder();
  while(at<text.length()){
   char c=text.charAt(at++);if(c=='"')return out.toString();if(c<32)throw bad();
   if(c=='\\'){
    if(at>=text.length())throw bad();char e=text.charAt(at++);
    switch(e){
     case '"':case '\\':case '/':c=e;break;
     case 'b':c='\b';break;case 'f':c='\f';break;case 'n':c='\n';break;case 'r':c='\r';break;case 't':c='\t';break;
     case 'u':
      if(at+4>text.length())throw bad();int code=0;
      for(int i=0;i<4;i++){char h=text.charAt(at++);if(!((h>='0'&&h<='9')||(h>='a'&&h<='f')||(h>='A'&&h<='F')))throw bad();int digit=Character.digit(h,16);code=(code<<4)|digit;}c=(char)code;break;
     default:throw bad();
    }
   }
   out.append(c);
  }
  throw bad();
 }
}
