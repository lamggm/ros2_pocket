package com.lamggm.rospocket;
import android.util.AtomicFile;
import java.io.*;
import java.util.Arrays;
final class RobotAssetStore {
 private final AtomicFile file;private final File root;
 private static final int MAX_ADDITIONAL=4;
 RobotAssetStore(File root){this.root=root;file=new AtomicFile(new File(root,"robot-assets-v1.zip"));}
 private boolean exists(AtomicFile f){return f.getBaseFile().isFile()||new File(f.getBaseFile()+".bak").isFile();}
 private AtomicFile additional(RobotDescription expected){return new AtomicFile(new File(root,"robot-mesh-"+expected.fingerprint+".zip"));}
 private RobotAssetBundle load(AtomicFile f)throws Exception{if(!exists(f))return null;try(InputStream in=f.openRead()){return RobotAssetBundle.decode(read(in));}}
 RobotAssetBundle load()throws Exception{return load(file);}
 boolean hasLegacy(){return exists(file);}
 RobotAssetBundle loadFor(RobotDescription expected)throws Exception{RobotAssetBundle b=hasAdditional(expected)?loadAdditional(expected):load();return b!=null&&b.matches(expected)?b:null;}
 boolean hasAdditional(RobotDescription expected){return expected!=null&&exists(additional(expected));}
 RobotAssetBundle loadAdditional(RobotDescription expected)throws Exception{RobotAssetBundle b=load(additional(expected));return b!=null&&b.matches(expected)?b:null;}
 RobotAssetBundle save(byte[] bytes,RobotDescription expected)throws Exception{RobotAssetBundle bundle=RobotAssetBundle.decode(bytes);if(!bundle.matches(expected))throw new IllegalArgumentException("O pacote não corresponde à descrição selecionada");AtomicFile target=destination(expected);FileOutputStream out=null;try{out=target.startWrite();out.write(bytes);target.finishWrite(out);out=null;}finally{if(out!=null)target.failWrite(out);}try(InputStream in=target.openRead()){if(!Arrays.equals(bytes,read(in)))throw new IOException("Falha ao confirmar pacote gravado");}return bundle;}
 private AtomicFile destination(RobotDescription expected)throws Exception{
  if(!exists(file))return file;
  // Keep the original package even when an alternate geometry uses the same URDF.
  AtomicFile target=additional(expected);if(exists(target))return target;
  String[] entries=root.list((dir,name)->name.matches("robot-mesh-[0-9a-f]{64}\\.zip(?:\\.bak)?"));if(entries==null||entries.length>=MAX_ADDITIONAL)throw new IOException("Limite de pacotes locais atingido");return target;
 }
 static byte[] read(InputStream in)throws IOException{if(in==null)throw new IOException("Sem origem");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(n==0||out.size()+(long)n>RobotAssetBundle.MAX_BYTES)throw new IOException("Pacote excede 64 MiB ou leitura inválida");out.write(buffer,0,n);}return out.toByteArray();}
}
