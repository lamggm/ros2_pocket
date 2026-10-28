package com.lamggm.rospocket;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Real Android JSON, local configuration only; no DDS or physical claims. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class BenchProfilesTest {
 static final String ID="f256c3e5-9792-4ca0-8f40-556e76c98282";
 static org.json.JSONObject profile() throws Exception {
  org.json.JSONArray watches=new org.json.JSONArray();
  for(int i=0;i<4;i++)watches.put(new org.json.JSONObject().put("wire","rt/bench/numeric_"+i).put("type","std_msgs::msg::dds_::Float64_").put("reliable",i%2==0).put("transientLocal",i==3));
  return new org.json.JSONObject().put("id",ID).put("name","Bancada São Paulo").put("domain",69).put("peer","192.0.2.5").put("watches",watches).put("plots",new org.json.JSONArray().put(new org.json.JSONObject().put("slot",3).put("path","data")));
 }
 static byte[] catalog(org.json.JSONObject p) throws Exception {return new org.json.JSONObject().put("schema",1).put("profiles",new org.json.JSONArray().put(p)).put("applied",p).toString().getBytes(StandardCharsets.UTF_8);}
 private void reject(byte[] bytes) throws Exception {
  try{BenchProfiles.decode(bytes);fail("Malformed/unknown profile must not load");}catch(IllegalArgumentException|org.json.JSONException expected){}
 }
 @Test public void fullFourWatchConfigurationSurvivesCodecWithoutLiveData() throws Exception {
  org.json.JSONObject p=profile();BenchProfiles book=BenchProfiles.decode(catalog(p));
  org.json.JSONObject result=new org.json.JSONObject(new String(book.encode(),StandardCharsets.UTF_8));
  assertEquals(1,result.getJSONArray("profiles").length());
  assertEquals(p.toString(),result.getJSONArray("profiles").getJSONObject(0).toString());
  assertEquals(p.toString(),result.getJSONObject("applied").toString());
 }
 @Test public void unknownSchemaAndWrongPrimitiveTypesAreRejected() throws Exception {
  reject("{\"schema\":2,\"profiles\":[],\"applied\":null}".getBytes(StandardCharsets.UTF_8));
  for(Object domain:new Object[]{true,"69",69.5,-1,102}){org.json.JSONObject p=profile().put("domain",domain);reject(catalog(p));}
  reject(catalog(profile().put("wanted",true)));
  org.json.JSONObject p=profile();p.getJSONArray("watches").getJSONObject(0).put("reliable",1);reject(catalog(p));
 }
 @Test public void boundsNamesTopicsTypesAndPlotPathsAreValidated() throws Exception {
  for(String name:new String[]{""," trailing ","bad\nname","a".repeat(49),"界".repeat(43)})reject(catalog(profile().put("name",name)));
  for(String peer:new String[]{"localhost","192.0.2.3:7400","999.0.0.1"})reject(catalog(profile().put("peer",peer)));
  for(String wire:new String[]{"rt/invalid topic","rt/1bad","rt/","/bench"}){org.json.JSONObject p=profile();p.getJSONArray("watches").getJSONObject(0).put("wire",wire);reject(catalog(p));}
  org.json.JSONObject p=profile();p.getJSONArray("watches").getJSONObject(3).put("type","custom::msg::dds_::Unknown_");reject(catalog(p));
  p=profile();p.getJSONArray("plots").getJSONObject(0).put("path","not_numeric");reject(catalog(p));
  p=profile();p.getJSONArray("watches").put(p.getJSONArray("watches").getJSONObject(0));reject(catalog(p));
 }
 @Test public void duplicateCatalogEntriesAndSeriesAndOversizeOrMalformedInputAreRejected() throws Exception {
  org.json.JSONObject p=profile();org.json.JSONObject root=new org.json.JSONObject(new String(catalog(p),StandardCharsets.UTF_8));root.getJSONArray("profiles").put(p);reject(root.toString().getBytes(StandardCharsets.UTF_8));
  p.getJSONArray("plots").put(p.getJSONArray("plots").getJSONObject(0));reject(catalog(p));
  reject(new byte[131073]);reject(new byte[]{(byte)0xc3,0x28});reject("{broken".getBytes(StandardCharsets.UTF_8));
  reject(("[".repeat(20)+"]".repeat(20)).getBytes(StandardCharsets.UTF_8));
 }
 @Test public void catalogLimitAndUniqueNamesAndAppliedSnapshotAreExplicit() throws Exception {
  java.util.List<BenchProfiles.Profile> rows=new java.util.ArrayList<>();BenchProfiles.Profile original=BenchProfiles.decode(catalog(profile())).profiles.get(0);
  BenchProfiles book=new BenchProfiles().save(original).apply(original);book=book.save(original.rename("Novo nome"));
  assertEquals("Novo nome",book.find(ID).name);assertEquals("Bancada São Paulo",book.applied.name);assertNull(book.delete(ID).applied);
  for(int i=0;i<16;i++)rows.add(new BenchProfiles.Profile(java.util.UUID.randomUUID().toString(),"Perfil "+i,0,"",original.watches,original.plots));
  assertEquals(16,new BenchProfiles(rows,null).profiles.size());
  try{new BenchProfiles(rows,null).save(original);fail("17 profiles must be rejected");}catch(IllegalArgumentException expected){}
  try{new BenchProfiles().save(original).save(new BenchProfiles.Profile(java.util.UUID.randomUUID().toString(),original.name.toUpperCase(java.util.Locale.ROOT),0,"",original.watches,original.plots));fail("Duplicate display names must be rejected");}catch(IllegalArgumentException expected){}
 }
 @Test public void standardNumericPathsIncludeOdometryButNeverTextOrBoolean() throws Exception {
  for(String path:new String[]{"header.stamp.sec","pose.pose.position.x","pose.pose.orientation.w","twist.twist.angular.z","twist.covariance[35]"})assertTrue(path,BenchProfiles.numeric("nav_msgs::msg::dds_::Odometry_",path));
  for(String path:new String[]{"child_frame_id","header.frame_id","pose.covariance[36]","pose.pose.orientation.q"})assertFalse(path,BenchProfiles.numeric("nav_msgs::msg::dds_::Odometry_",path));
  assertFalse(BenchProfiles.numeric("std_msgs::msg::dds_::String_","data"));assertFalse(BenchProfiles.numeric("std_msgs::msg::dds_::Bool_","data"));
 }
 @Test public void emptySchemaOneRoundTripsWithoutAnAppliedConfiguration() throws Exception {
  byte[] json="{\"schema\":1,\"profiles\":[],\"applied\":null}".getBytes(StandardCharsets.UTF_8);
  BenchProfiles book=BenchProfiles.decode(json);
  assertNotNull("Schema v1 must load rather than discard the catalog",book);
  assertTrue(book.profiles.isEmpty());assertNull(book.applied);
  assertEquals(new String(json,StandardCharsets.UTF_8),new String(book.encode(),StandardCharsets.UTF_8));
 }
}
