package com.powerinspection.robot;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

final class GnssFixParser {
  private static final Set<String> FIX_TYPES = Set.of(
      "NO_FIX", "SINGLE_POINT", "DGPS", "RTK_FIXED", "RTK_FLOAT", "UNKNOWN");

  private GnssFixParser() {}

  static BridgeGnssFix parse(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) return null;
    Double latitude = finiteDouble(map.get("latitude"));
    Double longitude = finiteDouble(map.get("longitude"));
    Double altitude = finiteDouble(map.get("altitude"));
    Integer quality = intValue(map.get("quality"));
    String fixType = text(map.get("fixType"));
    if (!FIX_TYPES.contains(fixType)) fixType = "UNKNOWN";
    Integer satellites = intValue(map.get("satellites"));
    if (satellites != null && satellites < 0) satellites = null;
    Double hdop = finiteDouble(map.get("hdop"));
    Double differentialAge = finiteDouble(map.get("differentialAge"));
    String baseStationId = text(map.get("baseStationId"));
    Double ageSec = finiteDouble(map.get("ageSec"));
    Instant observedAt = instant(map.get("observedAt"));
    boolean stale = bool(map.get("stale"));
    boolean coordinateValid = coordinateValid(latitude, longitude);
    boolean valid = bool(map.get("valid")) && coordinateValid && quality != null && quality > 0 && !stale;
    if (!coordinateValid && (latitude != null || longitude != null)) return null;
    return new BridgeGnssFix(
        valid,
        stale,
        text(map.get("frame")),
        latitude,
        longitude,
        altitude,
        quality,
        fixType,
        satellites,
        hdop,
        differentialAge,
        baseStationId,
        ageSec,
        observedAt);
  }

  static boolean coordinateValid(Double latitude, Double longitude) {
    return latitude != null
        && longitude != null
        && Double.isFinite(latitude)
        && Double.isFinite(longitude)
        && latitude >= -90.0
        && latitude <= 90.0
        && longitude >= -180.0
        && longitude <= 180.0
        && !(latitude == 0.0 && longitude == 0.0);
  }

  private static boolean bool(Object value) {
    return value instanceof Boolean b && b;
  }

  private static String text(Object value) {
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static Double finiteDouble(Object value) {
    if (value instanceof Number number) {
      double parsed = number.doubleValue();
      return Double.isFinite(parsed) ? parsed : null;
    }
    if (value == null) return null;
    try {
      double parsed = Double.parseDouble(String.valueOf(value));
      return Double.isFinite(parsed) ? parsed : null;
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Integer intValue(Object value) {
    if (value instanceof Number number) return number.intValue();
    if (value == null) return null;
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static Instant instant(Object value) {
    if (value == null) return null;
    try {
      return Instant.parse(String.valueOf(value));
    } catch (RuntimeException ex) {
      return null;
    }
  }
}
