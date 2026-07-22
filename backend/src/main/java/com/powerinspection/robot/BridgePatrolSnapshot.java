package com.powerinspection.robot;

import java.util.Map;

public record BridgePatrolSnapshot(
    String routeId,
    String targetId,
    String targetName,
    Integer targetIndex,
    Integer targetCount,
    String navigationPhase,
    Integer cycleIndex,
    Integer loopMaxCycles,
    Boolean loopInfinite,
    Double loopWaitRemainingSec,
    String lastError
) {
  static BridgePatrolSnapshot parse(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) return null;
    return new BridgePatrolSnapshot(
        text(map.get("routeId")),
        text(map.get("targetId")),
        text(map.get("targetName")),
        integer(map.get("targetIndex")),
        integer(map.get("targetCount")),
        text(map.get("navigationPhase")),
        integer(map.get("cycleIndex")),
        integer(map.get("loopMaxCycles")),
        map.get("loopInfinite") instanceof Boolean value ? value : null,
        finiteDouble(map.get("loopWaitRemainingSec")),
        text(map.get("lastError")));
  }

  private static String text(Object value) {
    if (value == null) return null;
    String result = String.valueOf(value).trim();
    return result.isEmpty() ? null : result;
  }

  private static Integer integer(Object value) {
    if (!(value instanceof Number number)) return null;
    double raw = number.doubleValue();
    if (!Double.isFinite(raw) || raw != Math.rint(raw) || raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) return null;
    return (int) raw;
  }

  private static Double finiteDouble(Object value) {
    if (!(value instanceof Number number)) return null;
    double result = number.doubleValue();
    return Double.isFinite(result) ? result : null;
  }
}
