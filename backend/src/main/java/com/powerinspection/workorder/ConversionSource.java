package com.powerinspection.workorder;

public enum ConversionSource {
  MANUAL,
  AUTO,
  AGENT;

  public static ConversionSource from(String value) {
    if (value == null || value.isBlank()) {
      return MANUAL;
    }
    return ConversionSource.valueOf(value.trim().toUpperCase());
  }
}
