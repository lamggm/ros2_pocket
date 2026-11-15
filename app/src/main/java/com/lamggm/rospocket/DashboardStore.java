package com.lamggm.rospocket;
import android.util.AtomicFile;import java.io.*;
class DashboardStore {
 static final class Unconfirmed extends IOException{Unconfirmed(Throwable cause){super("Resultado incerto do salvamento",cause);}}
 final AtomicFile file;
 DashboardStore(File root){file=new AtomicFile(new File(root,"dashboard-layout-v1.json"));}
 private DashboardLayout read()throws IOException{try(InputStream in=file.openRead()){byte[] bytes=new byte[4097];int used=0,n;while(used<bytes.length&&(n=in.read(bytes,used,bytes.length-used))!=-1)used+=n;if(used>4096)throw new IOException("Layout excessivo");return DashboardLayout.decode(new String(bytes,0,used,java.nio.charset.StandardCharsets.UTF_8));}catch(IllegalArgumentException e){throw new IOException("Layout inválido",e);}}
 DashboardLayout load(){try{return read();}catch(IOException e){return DashboardLayout.defaults();}}
 void verify(DashboardLayout layout)throws IOException{if(!read().encode().equals(layout.encode()))throw new IOException("Layout salvo não corresponde ao solicitado");}
 void save(DashboardLayout layout)throws IOException{byte[] bytes=DashboardLayout.decode(layout.encode()).encode().getBytes(java.nio.charset.StandardCharsets.UTF_8);FileOutputStream out=null;boolean sealing=false;try{out=file.startWrite();out.write(bytes);sealing=true;file.finishWrite(out);out=null;verify(layout);}catch(IOException|RuntimeException e){if(sealing)throw new Unconfirmed(e);if(out!=null){file.failWrite(out);out=null;}throw new IOException("Não foi possível salvar o painel",e);}finally{if(out!=null)try{out.close();}catch(IOException ignored){/* Result is already unconfirmed; never claim rollback. */}}}
}
