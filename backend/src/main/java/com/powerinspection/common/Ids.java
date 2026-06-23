package com.powerinspection.common;

import java.security.SecureRandom;

public final class Ids {
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final char[] CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

  private Ids() {
  }

  public static String next(String prefix) {
    char[] suffix = new char[7];
    for (int i = 0; i < suffix.length; i++) {
      suffix[i] = CHARS[RANDOM.nextInt(CHARS.length)];
    }
    return prefix + "_" + System.currentTimeMillis() + "_" + new String(suffix);
  }
}
