package com.lamggm.rospocket;
import android.util.AtomicFile;
import java.io.*;
import java.util.Arrays;
final class RobotAssetStore {
 private final AtomicFile file;
 RobotAssetStore(File root){file=new AtomicFile(new File(root,"robot-assets-v1.zip"));}
 RobotAssetBundle load()throws Exception{if(!file.getBaseFile().exists()&&!new File(file.getBaseFile()+".bak").exists())return null;try(InputStream in=file.openRead()){return RobotAssetBundle.decode(read(in));}}
 RobotAssetBundle save(byte[] bytes,RobotDescription expected)throws Exception{RobotAssetBundle bundle=RobotAssetBundle.decode(bytes);if(!bundle.matches(expected))throw new IllegalArgumentException("O pacote não corresponde à descrição selecionada");FileOutputStream out=null;try{out=file.startWrite();out.write(bytes);file.finishWrite(out);out=null;}finally{if(out!=null)file.failWrite(out);}try(InputStream in=file.openRead()){if(!Arrays.equals(bytes,read(in)))throw new IOException("Falha ao confirmar pacote gravado");}return bundle;}
 static byte[] read(InputStream in)throws IOException{if(in==null)throw new IOException("Sem origem");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(n==0||out.size()+(long)n>RobotAssetBundle.MAX_BYTES)throw new IOException("Pacote excede 64 MiB ou leitura inválida");out.write(buffer,0,n);}return out.toByteArray();}
}
