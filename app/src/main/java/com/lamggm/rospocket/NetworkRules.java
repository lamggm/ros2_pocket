package com.lamggm.rospocket;
public final class NetworkRules {
 public static boolean selectableWifi(boolean wifi,boolean vpn,boolean notVpn) {return wifi && !vpn && notVpn;}
}
