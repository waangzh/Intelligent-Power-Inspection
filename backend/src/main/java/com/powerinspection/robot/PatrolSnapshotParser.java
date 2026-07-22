package com.powerinspection.robot;

import java.util.Map;

final class PatrolSnapshotParser {
  private PatrolSnapshotParser() {}

  static BridgePatrolSnapshot parse(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) return null;
    return new BridgePatrolSnapshot(
        text(map.get("routeId")),
        text(map.get("targetId")),
        text(map.get("targetName")),
        intValue(map.get("targetIndex")),
        intValue(map.get("targetCount")),
        text(map.get("navigationPhase")),
        intValue(map.get("cycleIndex")),
        intValue(map.get("loopMaxCycles")),
        bool(map.get("loopInfinite")),
        finiteDouble(map.get("loopWaitRemainingSec")),
        text(map.get("lastError")));
  }

  private static String text(Object value) {
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
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

  private static Boolean bool(Object value) {
    return value instanceof Boolean b ? b : null;
  }
}
