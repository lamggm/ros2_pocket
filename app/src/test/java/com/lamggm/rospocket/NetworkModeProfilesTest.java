package com.lamggm.rospocket;
import org.json.*;
import java.nio.charset.StandardCharsets;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class NetworkModeProfilesTest {
 static JSONObject envelope()throws Exception{BenchProfiles.Profile p=BenchProfiles.decode(BenchProfilesTest.catalog(BenchProfilesTest.profile())).profiles.get(0);JSONObject e=new JSONObject(new String(BenchProfiles.exportProfile(p),StandardCharsets.UTF_8));e.put("schema",4);e.getJSONObject("profile").put("networkMode","vpn");return e;}
 @Test public void explicitVpnModeSurvivesPortableAndLocalRoundTrip()throws Exception{
  JSONObject e=envelope();BenchProfiles.Profile profile;
  try{profile=BenchProfiles.importProfile(e.toString().getBytes(StandardCharsets.UTF_8));}catch(IllegalArgumentException|JSONException old){throw new AssertionError("Profile schema4 must retain explicit routed VPN mode",old);}
  BenchProfiles book=new BenchProfiles().save(profile.rename("VPN bench")).apply(profile);BenchProfiles restored=BenchProfiles.decode(book.encode());JSONObject out=new JSONObject(new String(BenchProfiles.exportProfile(restored.profiles.get(0)),StandardCharsets.UTF_8));assertEquals(4,out.getInt("schema"));assertEquals("vpn",out.getJSONObject("profile").getString("networkMode"));JSONObject applied=new JSONObject(new String(restored.encode(),StandardCharsets.UTF_8));assertEquals("vpn",applied.getJSONObject("applied").getString("networkMode"));
 }
 @Test public void oldSchemasDefaultToLanWithoutRewritingOnLoad()throws Exception{
  JSONObject old3=envelope();old3.put("schema",3);old3.getJSONObject("profile").remove("networkMode");
  for(byte[] raw:new byte[][]{BenchProfilesTest.catalog(BenchProfilesTest.profile()),PortableProfilesTest.schemaTwo(PortableProfilesTest.visual()),new JSONObject().put("schema",3).put("profiles",new JSONArray().put(old3.getJSONObject("profile"))).put("applied",old3.getJSONObject("profile")).toString().getBytes(StandardCharsets.UTF_8)}){
   java.io.File file=java.io.File.createTempFile("network-profile-",".json");try{java.nio.file.Files.write(file.toPath(),raw);BenchProfiles book=new ProfileStore(file).load();assertEquals("lan",book.profiles.get(0).networkMode);assertEquals("lan",book.applied.networkMode);assertArrayEquals(raw,java.nio.file.Files.readAllBytes(file.toPath()));}finally{file.delete();}
  }
 }
 @Test public void unknownModeAndVpnWithoutPeerAreRejectedStrictly()throws Exception{
  for(Object mode:new Object[]{"auto","VPN","",true,4,JSONObject.NULL}){JSONObject e=envelope();e.getJSONObject("profile").put("networkMode",mode);try{BenchProfiles.importProfile(e.toString().getBytes(StandardCharsets.UTF_8));fail("Invalid network mode admitted");}catch(IllegalArgumentException|JSONException expected){}}
  JSONObject e=envelope();e.getJSONObject("profile").put("peer","");try{BenchProfiles.importProfile(e.toString().getBytes(StandardCharsets.UTF_8));fail("Peerless VPN admitted");}catch(IllegalArgumentException|JSONException expected){}
 }
}
