package com.lamggm.rospocket;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class RobotAssetBundleTest {
 static final String URI="package://robot/meshes/base.dae";
 static byte[] archive(String file,String digest,String robot)throws Exception{return archiveForDescription(file,digest,robot,RobotDescriptionTest.SMALL);}
 static byte[] archiveForDescription(String file,String digest,String robot,String description)throws Exception{byte[] glb=RobotGlbTest.glb(RobotGlbTest.json(),RobotGlbTest.binary());JSONObject manifest=new JSONObject().put("schema",1).put("robot_name",robot).put("description_sha256",RobotGlb.sha(description.getBytes(StandardCharsets.UTF_8))).put("meshes",new JSONArray().put(new JSONObject().put("uri",URI).put("file",file).put("sha256",digest==null?RobotGlb.sha(glb):digest)));ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(bytes)){z.putNextEntry(new ZipEntry("manifest.json"));z.write(manifest.toString().getBytes(StandardCharsets.UTF_8));z.closeEntry();z.putNextEntry(new ZipEntry(file));z.write(glb);z.closeEntry();}return bytes.toByteArray();}
 @Test public void bindsActualUriAndDescriptionFingerprintBeforeProvidingGeometry()throws Exception{RobotAssetBundle b=RobotAssetBundle.decode(archive("meshes/base.glb",null,"robo_real"));assertTrue(b.matches(RobotDescription.parse(RobotDescriptionTest.SMALL)));assertEquals(1,b.meshCount());assertEquals(1,b.triangles(URI).size());assertFalse(b.matches(RobotDescription.parse(RobotDescriptionTest.SMALL.replace("robo_real","other"))));assertFalse(b.matches(RobotDescription.parse(RobotDescriptionTest.SMALL+" ")));assertNull(b.triangles("package://other/base.dae"));}
 @Test public void rejectsTraversalChangedPayloadAndMalformedZip()throws Exception{byte[] traversal=archive("../escape.glb",null,"robo_real");assertThrows(IllegalArgumentException.class,()->RobotAssetBundle.decode(traversal));byte[] changed=archive("meshes/base.glb","0".repeat(64),"robo_real");assertThrows(IllegalArgumentException.class,()->RobotAssetBundle.decode(changed));assertThrows(IllegalArgumentException.class,()->RobotAssetBundle.decode(new byte[]{1,2,3}));}
}
