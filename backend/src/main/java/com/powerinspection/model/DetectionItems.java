package com.powerinspection.model;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DetectionItems {
  private static final Map<String, String> DEFAULT_LABELS = Map.of(
    "PERSON", "人员",
    "HELMET", "安全帽",
    "OBSTACLE", "障碍物",
    "FIRE", "明火烟雾",
    "SWITCH", "刀闸开关",
    "METER", "压力表",
    "OIL_LEAK", "漏油区域",
    "FOREIGN_OBJECT", "异物"
  );

  private DetectionItems() {
  }

  public static List<Map<String, Object>> enabled(List<Map<String, Object>> items) {
    if (items == null) {
      return List.of();
    }
    return items.stream()
      .filter(item -> !Boolean.FALSE.equals(item.get("enabled")))
      .map(DetectionItems::withDisplayLabel)
      .toList();
  }

  public static String displayLabel(String type) {
    return DEFAULT_LABELS.getOrDefault(type, type == null ? "检测目标" : type);
  }

  private static Map<String, Object> withDisplayLabel(Map<String, Object> source) {
    Map<String, Object> item = new LinkedHashMap<>(source);
    Object configured = item.get("displayLabel");
    if (configured == null || configured.toString().isBlank()) {
      item.put("displayLabel", displayLabel(item.get("type") == null ? null : item.get("type").toString()));
    }
    return item;
  }
}
