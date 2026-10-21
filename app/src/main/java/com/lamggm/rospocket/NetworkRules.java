package com.lamggm.rospocket;
public final class NetworkRules {
 public static boolean validPeer(String peer) {
  if(peer.isEmpty())return true;
  if(!peer.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
  for(String part:peer.split("\\."))if(Integer.parseInt(part)>255)return false;
  return true;
 }
 public static boolean selectableWifi(boolean wifi,boolean vpn,boolean notVpn) {return wifi && !vpn && notVpn;}
}
