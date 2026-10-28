package com.lamggm.rospocket;

import android.util.AtomicFile;
import java.io.*;
import java.util.Arrays;

/** Bounded local file; manual UI operations only, never run on the DDS worker.
 * Process-wide lock + optimistic byte comparison prevent stale Activity saves.
 */
final class ProfileStore {
 private static final Object LOCK=new Object();
 private final AtomicFile file;
 private byte[] observed;
 private boolean loaded;
 ProfileStore(File file){this(new AtomicFile(file));}
 ProfileStore(AtomicFile file){this.file=file;}
 private byte[] read() throws IOException {
  if(!file.getBaseFile().exists()&&!new File(file.getBaseFile().getPath()+".bak").exists())return null;
  try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];int n;
   while((n=in.read(buffer))!=-1){if(out.size()+n>BenchProfiles.MAX_BYTES)throw new IOException("Arquivo de perfis excede 128 KiB; preservado");out.write(buffer,0,n);}
   return out.toByteArray();
  }
 }
 BenchProfiles load() throws IOException {
  synchronized(LOCK){
   loaded=false;byte[] bytes=read();
   try{BenchProfiles book=bytes==null?new BenchProfiles():BenchProfiles.decode(bytes);observed=bytes;loaded=true;return book;}
   catch(Exception ex){throw new IOException("Perfis inválidos/schema desconhecido; arquivo preservado",ex);}
  }
 }
 void commit(BenchProfiles next) throws IOException {
  synchronized(LOCK){
   if(!loaded)throw new IOException("Recarregue os perfis antes de salvar");
   if(!Arrays.equals(observed,read())){loaded=false;throw new IOException("Perfis mudaram em outra tela; reabra o app antes de salvar");}
   final byte[] bytes;
   try{bytes=next.encode();BenchProfiles.decode(bytes);}catch(Exception ex){throw new IOException("Configuração de perfil inválida",ex);}
   FileOutputStream out=null;
   try{out=file.startWrite();out.write(bytes);out.flush();out.getFD().sync();file.finishWrite(out);out=null;
    if(!Arrays.equals(bytes,read())){loaded=false;throw new IOException("Gravação de perfis não confirmada; reabra o app");}
    observed=bytes;
   }catch(IOException|RuntimeException ex){if(out!=null)file.failWrite(out);throw ex;}
  }
 }
}
