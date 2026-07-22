package com.powerinspection.detection;

import com.powerinspection.common.ApiException;
import java.util.Map;
import java.util.Set;

final class DetectionRiskRules {
  private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

  private DetectionRiskRules() {}

  static void normalize(Map<?, ?> source, Map<String, Object> target) {
    target.put("alarmEnabled", Boolean.TRUE.equals(source.get("alarmEnabled")));
    target.put("alarmOnFinding", Boolean.TRUE.equals(source.get("alarmOnFinding")));
    Object rawSeverity = source.get("alarmSeverity");
    String severity = rawSeverity == null ? "MEDIUM" : rawSeverity.toString();
    if (!SEVERITIES.contains(severity)) {
      throw ApiException.badRequest("告警级别必须是 LOW、MEDIUM、HIGH 或 CRITICAL");
    }
    target.put("alarmSeverity", severity);
    Object rawMessage = source.get("alarmMessage");
    target.put("alarmMessage", rawMessage == null ? "" : rawMessage.toString());
  }
}
