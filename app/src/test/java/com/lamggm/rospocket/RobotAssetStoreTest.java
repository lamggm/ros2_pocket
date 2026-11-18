package com.lamggm.rospocket;
import java.io.*;
import java.nio.file.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotAssetStoreTest {
 @Test public void keepsVerifiedBundleAcrossReloadAndPreservesItWhenReplacementIsInvalid()throws Exception{File root=Files.createTempDirectory(new File(System.getProperty("java.io.tmpdir")).toPath(),"mesh-store-").toFile();try{RobotAssetStore s=new RobotAssetStore(root);assertNull(s.load());RobotDescription model=RobotDescription.parse(RobotDescriptionTest.SMALL);byte[] bytes=RobotAssetBundleTest.archive("meshes/base.glb",null,"robo_real");assertTrue(s.save(bytes,model).matches(model));assertTrue(new RobotAssetStore(root).load().matches(model));assertThrows(IllegalArgumentException.class,()->s.save(new byte[]{0},model));assertThrows(IllegalArgumentException.class,()->s.save(bytes,RobotDescription.parse(RobotDescriptionTest.SMALL.replace("robo_real","wrong"))));assertTrue(new RobotAssetStore(root).load().matches(model));}finally{for(File p:root.listFiles())p.delete();root.delete();}}
 @Test public void aSecondDescriptionKeepsTheFirstPackageBytes()throws Exception{
  File root=Files.createTempDirectory(new File(System.getProperty("java.io.tmpdir")).toPath(),"mesh-multiple-").toFile();try{
   RobotDescription one=RobotDescription.parse(RobotDescriptionTest.SMALL),two=RobotDescription.parse(RobotDescriptionTest.SMALL.replace("robo_real","other"));
   byte[] first=RobotAssetBundleTest.archive("meshes/base.glb",null,"robo_real"),second=RobotAssetBundleTest.archiveForDescription("meshes/base.glb",null,"other",RobotDescriptionTest.SMALL.replace("robo_real","other"));
   RobotAssetStore s=new RobotAssetStore(root);s.save(first,one);s.save(second,two);
   assertArrayEquals(first,Files.readAllBytes(new File(root,"robot-assets-v1.zip").toPath()));
   RobotAssetStore reopened=new RobotAssetStore(root);assertTrue(reopened.loadFor(one).matches(one));assertTrue(reopened.loadFor(two).matches(two));assertNull(reopened.loadFor(RobotDescription.parse(RobotDescriptionTest.SMALL+" ")));
  }finally{try(var paths=Files.walk(root.toPath())){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
 }
 @Test public void fullBundleOfSameDescriptionLeavesLegacyLodUntouched()throws Exception{
  File root=Files.createTempDirectory(new File(System.getProperty("java.io.tmpdir")).toPath(),"mesh-variants-").toFile();try{
   RobotDescription model=RobotDescription.parse(RobotDescriptionTest.SMALL);
   byte[] lod=RobotAssetBundleTest.archive("meshes/base.glb",null,"robo_real");RobotAssetStore store=new RobotAssetStore(root);
   store.save(lod,model);store.save(RobotFullBundleTest.bundle("full"),model);
   assertArrayEquals(lod,Files.readAllBytes(new File(root,"robot-assets-v1.zip").toPath()));assertTrue(new RobotAssetStore(root).loadFor(model).fullGeometry());
  }finally{try(var paths=Files.walk(root.toPath())){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new))Files.delete(p);}}
 }
 @Test public void readsBoundedStreamsAndRejectsStalledReads()throws Exception{assertArrayEquals(new byte[]{1,2,3},RobotAssetStore.read(new ByteArrayInputStream(new byte[]{1,2,3})));InputStream zero=new InputStream(){public int read(){return 0;}public int read(byte[] b,int o,int n){return 0;}};assertThrows(IOException.class,()->RobotAssetStore.read(zero));}
}
