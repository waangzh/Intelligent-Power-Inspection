package com.powerinspection.detection;

import com.powerinspection.common.ApiException;
import java.util.Map;
import java.util.Set;

final class DetectionRiskRules {
  private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
  private static final Set<String> ALARM_MODES = Set.of("OFF", "ON_FINDING");

  private DetectionRiskRules() {}

  static void normalize(Map<?, ?> source, Map<String, Object> target) {
    target.put("alarmMode", alarmMode(source));
    Object rawSeverity = source.get("alarmSeverity");
    String severity = rawSeverity == null ? "MEDIUM" : rawSeverity.toString();
    if (!SEVERITIES.contains(severity)) {
      throw ApiException.badRequest("告警级别必须是 LOW、MEDIUM、HIGH 或 CRITICAL");
    }
    target.put("alarmSeverity", severity);
    Object rawMessage = source.get("alarmMessage");
    target.put("alarmMessage", rawMessage == null ? "" : rawMessage.toString());
  }

  static boolean isOnFinding(Map<?, ?> source) {
    return "ON_FINDING".equals(alarmMode(source));
  }

  private static String alarmMode(Map<?, ?> source) {
    Object configured = source.get("alarmMode");
    if (configured != null) {
      String mode = configured.toString();
      if (!ALARM_MODES.contains(mode)) {
        throw ApiException.badRequest("告警规则必须是 OFF 或 ON_FINDING");
      }
      return mode;
    }
    return Boolean.TRUE.equals(source.get("alarmEnabled"))
        && Boolean.TRUE.equals(source.get("alarmOnFinding"))
        ? "ON_FINDING"
        : "OFF";
  }
}
