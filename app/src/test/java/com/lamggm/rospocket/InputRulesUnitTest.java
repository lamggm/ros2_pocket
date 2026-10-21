package com.lamggm.rospocket;
import org.junit.Test;
import static org.junit.Assert.*;
public class InputRulesUnitTest {
 @Test public void doesNotConfuseVpnUnderlayWithWifi() {
  assertFalse(NetworkRules.selectableWifi(true,true,false));
  assertTrue(NetworkRules.selectableWifi(true,false,true));
  assertFalse(NetworkRules.selectableWifi(false,false,true));
  assertFalse(NetworkRules.selectableWifi(true,false,false));
 }
 @Test public void validatesOptionalPeerWithoutDnsOrInvalidOctets(){
  for(String value:new String[]{"","127.0.0.1","192.0.2.1"})assertTrue(NetworkRules.validPeer(value));
  for(String value:new String[]{"256.0.0.1","peer.test","1.2.3.4:7","1.2.3","1.2.3.999","-1.0.0.0"})assertFalse(NetworkRules.validPeer(value));
 }
 @Test public void acceptsPortableDomains() { for(int n:new int[]{0,1,67,101}) assertEquals(n,InputRules.domain(" "+n+" ")); }
 @Test public void rejectsMalformedAndOutOfRangeDomains() {
  for(String s:new String[]{"","-1","102","1.5","NaN","2147483648","67x"}) {
   try {InputRules.domain(s); fail("accepted: "+s);}catch(IllegalArgumentException expected){}
  }
 }
}
