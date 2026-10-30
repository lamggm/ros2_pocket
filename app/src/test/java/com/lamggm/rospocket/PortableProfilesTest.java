package com.lamggm.rospocket;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;
import org.json.*;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Framework JSON/AtomicFile, no device or DDS claims. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class PortableProfilesTest {
 @Rule public TemporaryFolder tmp=new TemporaryFolder();
 static JSONObject visual() throws Exception {
  return new JSONObject().put("mode","camera")
   .put("scan",new JSONObject().put("wire","rt/bench/scan").put("type","sensor_msgs::msg::dds_::LaserScan_"))
   .put("camera",new JSONObject().put("wire","rt/bench/camera").put("type","sensor_msgs::msg::dds_::CompressedImage_"));
 }
 static byte[] schemaTwo(JSONObject visual) throws Exception {
  JSONObject p=BenchProfilesTest.profile().put("visual",visual);
  return new JSONObject().put("schema",2).put("profiles",new JSONArray().put(p)).put("applied",p).toString().getBytes(StandardCharsets.UTF_8);
 }
 static void reject(byte[] bytes) throws Exception {
  try{BenchProfiles.decode(bytes);fail("Invalid configuration must be rejected before save");}catch(IllegalArgumentException|JSONException expected){}
 }
 @Test public void schemaTwoRoundTripPreservesBothSensorsAndActiveMode() throws Exception {
  BenchProfiles book=BenchProfiles.decode(schemaTwo(visual()));
  JSONObject out=new JSONObject(new String(book.encode(),StandardCharsets.UTF_8));
  assertEquals(2,out.getInt("schema"));
  assertEquals(visual().toString(),out.getJSONArray("profiles").getJSONObject(0).getJSONObject("visual").toString());
  assertEquals(visual().toString(),out.getJSONObject("applied").getJSONObject("visual").toString());
 }
 @Test public void schemaOneLoadPreservesOldBytesUntilAnExplicitCommit() throws Exception {
  byte[] old=BenchProfilesTest.catalog(BenchProfilesTest.profile());File file=new File(tmp.getRoot(),"profiles.json");Files.write(file.toPath(),old);
  ProfileStore store=new ProfileStore(file);BenchProfiles book=store.load();assertArrayEquals(old,Files.readAllBytes(file.toPath()));
  store.commit(book);JSONObject out=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));assertEquals(2,out.getInt("schema"));
  JSONObject v=out.getJSONObject("applied").getJSONObject("visual");assertEquals("scan",v.getString("mode"));assertTrue(v.isNull("scan"));assertTrue(v.isNull("camera"));
 }
 @Test public void wrongVisualModeTypesAndUnknownFieldsCannotBeImportedAsConfiguration() throws Exception {
  reject(schemaTwo(visual().put("mode","video")));
  reject(schemaTwo(visual().put("sample",42)));
  JSONObject v=visual();v.getJSONObject("scan").put("type","sensor_msgs::msg::dds_::Image_");reject(schemaTwo(v));
  v=visual();v.getJSONObject("camera").put("type","sensor_msgs::msg::dds_::LaserScan_");reject(schemaTwo(v));
  v=visual();v.getJSONObject("camera").put("wire","rt/invalid topic");reject(schemaTwo(v));
 }
 @Test public void unicodeEscapesRequireAsciiHexDigits()throws Exception {
  reject("{\"sch\\u００６５ma\":1,\"profiles\":[],\"applied\":null}".getBytes(StandardCharsets.UTF_8));
 }
 @Test public void portableEnvelopeValidatesFormatSchemaFieldsAndRenewsIdentity()throws Exception {
  BenchProfiles.Profile source=BenchProfiles.decode(schemaTwo(visual())).profiles.get(0);
  byte[] bytes=BenchProfiles.exportProfile(source);BenchProfiles.Profile first=BenchProfiles.importProfile(bytes),second=BenchProfiles.importProfile(bytes);
  assertNotEquals(source.id,first.id);assertNotEquals(first.id,second.id);assertEquals(source.peer,first.peer);assertEquals(source.visual.camera.type,first.visual.camera.type);
  JSONObject envelope=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
  for(String field:new String[]{"format","schema","profile"}){
   JSONObject invalid=new JSONObject(envelope.toString());invalid.put(field,field.equals("schema")?3:JSONObject.NULL);
   try{BenchProfiles.importProfile(invalid.toString().getBytes(StandardCharsets.UTF_8));fail("Wrong envelope field must fail");}catch(IllegalArgumentException|JSONException expected){}
  }
  envelope.put("samples",new JSONArray());try{BenchProfiles.importProfile(envelope.toString().getBytes(StandardCharsets.UTF_8));fail("Telemetry must not be admitted");}catch(IllegalArgumentException|JSONException expected){}
 }
 @Test public void externalJsonMustRejectCommentsDuplicatesAndNonJsonSyntax() throws Exception {
  for(String text:new String[]{"{\"schema\":1,\"schema\":1,\"profiles\":[],\"applied\":null}","{/*comment*/\"schema\":1,\"profiles\":[],\"applied\":null}","{'schema':1,'profiles':[],'applied':null}","{\"schema\":1,\"profiles\":[],\"applied\":null,}"})reject(text.getBytes(StandardCharsets.UTF_8));
 }
}
