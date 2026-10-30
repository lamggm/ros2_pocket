package com.lamggm.rospocket;

import android.util.AtomicFile;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class ProfileStoreTest {
 @Rule public TemporaryFolder tmp=new TemporaryFolder();
 private BenchProfiles sample() throws Exception{return BenchProfiles.decode(BenchProfilesTest.catalog(BenchProfilesTest.profile()));}
 @Test public void explicitSaveAndReloadKeepAllSettings() throws Exception {
  File file=new File(tmp.getRoot(),"profiles.json");ProfileStore s=new ProfileStore(file);assertTrue(s.load().profiles.isEmpty());s.commit(sample());
  assertTrue("Commit must create the catalog",file.isFile());assertArrayEquals(sample().encode(),new ProfileStore(file).load().encode());
 }
 @Test public void interruptedStagingRetainsPreviousCommittedConfiguration() throws Exception {
  File file=new File(tmp.getRoot(),"profiles.json");ProfileStore s=new ProfileStore(file);s.load();s.commit(sample());
  AtomicFile atomic=new AtomicFile(file);FileOutputStream abandoned=atomic.startWrite();abandoned.write("incomplete".getBytes(StandardCharsets.UTF_8));abandoned.close();
  assertArrayEquals(sample().encode(),new ProfileStore(file).load().encode());
 }
 @Test public void failedWriteKeepsCommittedBytes() throws Exception {
  File file=new File(tmp.getRoot(),"profiles.json");Files.write(file.toPath(),sample().encode());
  ProfileStore s=new ProfileStore(new AtomicFile(file){@Override public FileOutputStream startWrite() throws IOException{throw new IOException("disk failure");}});s.load();
  try{s.commit(new BenchProfiles());fail("I/O failure must propagate");}catch(IOException expected){}
  assertArrayEquals(sample().encode(),Files.readAllBytes(file.toPath()));
 }
 @Test public void corruptOrUnknownSchemaCannotBeSilentlyOverwritten() throws Exception {
  for(String raw:new String[]{"{broken","{\"schema\":3,\"profiles\":[],\"applied\":null}"}){
   File file=new File(tmp.getRoot(),"invalid-"+raw.length());byte[] old=raw.getBytes(StandardCharsets.UTF_8);Files.write(file.toPath(),old);ProfileStore s=new ProfileStore(file);
   try{s.load();fail("Must expose load failure");}catch(IOException expected){}
   try{s.commit(sample());fail("Must not overwrite unknown/corrupt state");}catch(IOException expected){}
   assertArrayEquals(old,Files.readAllBytes(file.toPath()));
  }
 }
 @Test public void partialPayloadFailureRollsBackTheOwnedStagingFile() throws Exception {
  File file=new File(tmp.getRoot(),"profiles.json");Files.write(file.toPath(),sample().encode());
  AtomicFile atomic=new AtomicFile(file){@Override public FileOutputStream startWrite()throws IOException{return new FileOutputStream(new File(file.getPath()+".new")){@Override public void write(byte[] bytes)throws IOException{super.write(bytes,0,bytes.length/2);throw new IOException("interrupted payload write");}};}};
  ProfileStore s=new ProfileStore(atomic);s.load();try{s.commit(new BenchProfiles());fail("Partial payload must not commit");}catch(IOException expected){}
  assertArrayEquals(sample().encode(),new ProfileStore(file).load().encode());assertFalse(new File(file.getPath()+".new").exists());
 }
 @Test public void staleCatalogCannotOverwriteAnotherActivitysSave() throws Exception {
  File file=new File(tmp.getRoot(),"profiles.json");ProfileStore old=new ProfileStore(file),current=new ProfileStore(file);old.load();current.load();current.commit(sample());
  try{old.commit(new BenchProfiles());fail("Stale writer must reload instead of erasing profiles");}catch(IOException expected){}
  assertArrayEquals(sample().encode(),Files.readAllBytes(file.toPath()));
 }
}
