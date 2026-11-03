package com.lamggm.rospocket;

import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class GraphFormatProfilesTest {
 @Test public void humbleGraphFormatSurvivesPortableRoundTrip() throws Exception {
  BenchProfiles.Profile source=BenchProfiles.decode(PortableProfilesTest.schemaTwo(PortableProfilesTest.visual())).profiles.get(0);
  JSONObject envelope=new JSONObject(new String(BenchProfiles.exportProfile(source),StandardCharsets.UTF_8));
  envelope.put("schema",3);envelope.getJSONObject("profile").put("graphFormat","humble");
  final BenchProfiles.Profile imported;
  try{imported=BenchProfiles.importProfile(envelope.toString().getBytes(StandardCharsets.UTF_8));}
  catch(IllegalArgumentException|JSONException unsupported){throw new AssertionError("Schema3 must preserve the explicit Humble graph format",unsupported);}
  JSONObject result=new JSONObject(new String(BenchProfiles.exportProfile(imported),StandardCharsets.UTF_8));
  assertEquals(3,result.getInt("schema"));
  assertEquals("humble",result.getJSONObject("profile").getString("graphFormat"));
  assertNotEquals(source.id,imported.id);
 }
 @Test public void legacyCatalogLoadDefaultsJazzyWithoutRewritingOldBytes() throws Exception {
  for(byte[] old:new byte[][]{BenchProfilesTest.catalog(BenchProfilesTest.profile()),PortableProfilesTest.schemaTwo(PortableProfilesTest.visual())}){
   java.io.File file=java.io.File.createTempFile("graph-profile-",".json");
   try{
    java.nio.file.Files.write(file.toPath(),old);ProfileStore store=new ProfileStore(file);BenchProfiles book=store.load();
    assertArrayEquals(old,java.nio.file.Files.readAllBytes(file.toPath()));
    assertEquals("jazzy",book.profiles.get(0).graphFormat);assertEquals("jazzy",book.applied.graphFormat);
    store.commit(book);JSONObject committed=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
    assertEquals(3,committed.getInt("schema"));assertEquals("jazzy",committed.getJSONObject("applied").getString("graphFormat"));
   }finally{java.nio.file.Files.deleteIfExists(file.toPath());}
  }
 }
 @Test public void appliedAndRenamedHumbleProfileSurviveLocalCatalogRoundTrip() throws Exception {
  BenchProfiles.Profile old=BenchProfiles.decode(PortableProfilesTest.schemaTwo(PortableProfilesTest.visual())).profiles.get(0);
  BenchProfiles.Profile humble=new BenchProfiles.Profile(old.id,old.name,old.domain,old.peer,old.watches,old.plots,old.visual,"humble");
  BenchProfiles book=new BenchProfiles().save(humble).apply(humble);BenchProfiles restored=BenchProfiles.decode(book.encode());
  assertEquals("humble",restored.profiles.get(0).graphFormat);assertEquals("humble",restored.applied.graphFormat);
  assertEquals("humble",restored.profiles.get(0).rename("Outra bancada").graphFormat);
  assertEquals(old.visual.camera.type,restored.applied.visual.camera.type);
 }
 @Test public void unknownMissingOrWrongPrimitiveGraphFormatIsRejected() throws Exception {
  BenchProfiles.Profile old=BenchProfiles.decode(PortableProfilesTest.schemaTwo(PortableProfilesTest.visual())).profiles.get(0);
  for(Object format:new Object[]{"auto","HUMBLE","",true,24,JSONObject.NULL}){
   JSONObject envelope=new JSONObject(new String(BenchProfiles.exportProfile(old),StandardCharsets.UTF_8));envelope.getJSONObject("profile").put("graphFormat",format);
   try{BenchProfiles.importProfile(envelope.toString().getBytes(StandardCharsets.UTF_8));fail("Unknown/wrong-primitive graph format must fail");}catch(IllegalArgumentException|JSONException expected){}
  }
  JSONObject missing=new JSONObject(new String(BenchProfiles.exportProfile(old),StandardCharsets.UTF_8));missing.getJSONObject("profile").remove("graphFormat");
  try{BenchProfiles.importProfile(missing.toString().getBytes(StandardCharsets.UTF_8));fail("Schema3 requires explicit graphFormat");}catch(IllegalArgumentException|JSONException expected){}
 }
}
