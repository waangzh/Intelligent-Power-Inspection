package com.powerinspection.sms;

public enum SmsMode {
  OFF,
  MOCK,
  /** Aliyun Phone Number Verification Service (号码认证 / 短信认证). */
  PNVS;

  public static SmsMode from(String raw) {
    if (raw == null || raw.isBlank()) {
      return MOCK;
    }
    return switch (raw.trim().toLowerCase()) {
      case "off", "disabled", "false", "0" -> OFF;
      case "pnvs", "dypns", "aliyun", "alibaba", "real", "号码认证" -> PNVS;
      default -> MOCK;
    };
  }
}
