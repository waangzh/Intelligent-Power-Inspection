package com.powerinspection.robot;

import java.util.Map;

/** 保留协议字段供摄取服务核验；原始完整载荷不进入日志或 REST 响应。 */
public record RobotBridgeExecutionEvent(Map<String, Object> fields) {
  public String text(String key) {
    Object value = fields.get(key);
    return value == null ? "" : String.valueOf(value).trim();
  }

  public long sequence() {
    Object value = fields.get("sequence");
    if (value instanceof Number number) return number.longValue();
    try { return Long.parseLong(text("sequence")); }
    catch (NumberFormatException ex) { return 0; }
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> payload() {
    Object value = fields.get("payload");
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }
}
