package com.lamggm.rospocket;
public final class InputRules {
 public static int domain(String text) {
  if (text==null || !text.trim().matches("[0-9]{1,3}")) throw new IllegalArgumentException("Domain ID: inteiro de 0 a 101.");
  int domain=Integer.parseInt(text.trim());
  if (domain>101) throw new IllegalArgumentException("Domain ID: use 0 a 101 nesta versão.");
  return domain;
 }
}
