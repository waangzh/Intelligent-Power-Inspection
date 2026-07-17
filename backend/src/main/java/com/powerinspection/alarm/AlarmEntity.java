package com.powerinspection.alarm;

import com.powerinspection.domain.EntityPayloadCodec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "alarms")
public class AlarmEntity {
  private static final Set<String> KNOWN = Set.of(
    "id", "siteId", "routeId", "robotId", "taskId", "type", "severity", "status", "message",
    "routeName", "checkpointName", "imageUrl", "acknowledged", "workOrderId",
    "workOrderModeApplied", "workOrderConversionSource", "workOrderConversionStatus",
    "workOrderConversionError", "workOrderConversionFailedAt", "convertedAt", "version", "createdAt", "updatedAt"
  );

  @Id
  private String id;
  @Column(name = "site_id")
  private String siteId;
  @Column(name = "route_id")
  private String routeId;
  @Column(name = "robot_id")
  private String robotId;
  @Column(name = "task_id")
  private String taskId;
  private String type;
  @Column(nullable = false)
  private String severity;
  private String status;
  @Column(nullable = false)
  private String message;
  @Column(name = "route_name")
  private String routeName;
  @Column(name = "checkpoint_name")
  private String checkpointName;
  @Column(name = "image_url")
  private String imageUrl;
  @Column(nullable = false)
  private Boolean acknowledged = false;
  @Column(name = "work_order_id")
  private String workOrderId;
  @Column(name = "work_order_mode_applied")
  private String workOrderModeApplied;
  @Column(name = "work_order_conversion_source")
  private String workOrderConversionSource;
  @Column(name = "work_order_conversion_status")
  private String workOrderConversionStatus;
  @Column(name = "work_order_conversion_error")
  private String workOrderConversionError;
  @Column(name = "work_order_conversion_failed_at")
  private String workOrderConversionFailedAt;
  @Column(name = "converted_at")
  private String convertedAt;
  @Column(name = "extra_json", columnDefinition = "LONGTEXT")
  private String extraJson;
  @Column(name = "created_at", nullable = false)
  private String createdAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private Long version;

  public static AlarmEntity fromMap(Map<String, Object> map) {
    AlarmEntity entity = new AlarmEntity();
    entity.apply(map);
    return entity;
  }

  public void apply(Map<String, Object> map) {
    id = text(map.get("id"));
    siteId = text(map.get("siteId"));
    routeId = text(map.get("routeId"));
    robotId = text(map.get("robotId"));
    taskId = text(map.get("taskId"));
    type = text(map.get("type"));
    severity = first(map.get("severity"), "MEDIUM");
    status = text(map.get("status"));
    message = first(map.get("message"), "");
    routeName = text(map.get("routeName"));
    checkpointName = text(map.get("checkpointName"));
    imageUrl = text(map.get("imageUrl"));
    acknowledged = bool(map.get("acknowledged"), false);
    workOrderId = text(map.get("workOrderId"));
    workOrderModeApplied = text(map.get("workOrderModeApplied"));
    workOrderConversionSource = text(map.get("workOrderConversionSource"));
    workOrderConversionStatus = text(map.get("workOrderConversionStatus"));
    workOrderConversionError = text(map.get("workOrderConversionError"));
    workOrderConversionFailedAt = text(map.get("workOrderConversionFailedAt"));
    convertedAt = text(map.get("convertedAt"));
    extraJson = EntityPayloadCodec.extraJson(map, KNOWN);
    createdAt = first(map.get("createdAt"), Instant.now().toString());
    updatedAt = first(map.get("updatedAt"), createdAt);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id);
    put(map, "siteId", siteId);
    put(map, "routeId", routeId);
    put(map, "robotId", robotId);
    put(map, "taskId", taskId);
    put(map, "type", type);
    map.put("severity", severity);
    put(map, "status", status);
    map.put("message", message);
    put(map, "routeName", routeName);
    put(map, "checkpointName", checkpointName);
    put(map, "imageUrl", imageUrl);
    map.put("acknowledged", Boolean.TRUE.equals(acknowledged));
    put(map, "workOrderId", workOrderId);
    put(map, "workOrderModeApplied", workOrderModeApplied);
    put(map, "workOrderConversionSource", workOrderConversionSource);
    put(map, "workOrderConversionStatus", workOrderConversionStatus);
    put(map, "workOrderConversionError", workOrderConversionError);
    put(map, "workOrderConversionFailedAt", workOrderConversionFailedAt);
    put(map, "convertedAt", convertedAt);
    map.put("createdAt", createdAt);
    map.put("updatedAt", updatedAt);
    EntityPayloadCodec.mergeExtra(map, extraJson);
    return map;
  }

  private static void put(Map<String, Object> map, String key, String value) {
    if (value != null) map.put(key, value);
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  private static String first(Object value, String fallback) {
    String text = text(value);
    return text == null || text.isBlank() ? fallback : text;
  }

  private static boolean bool(Object value, boolean fallback) {
    if (value instanceof Boolean bool) return bool;
    if (value == null) return fallback;
    return Boolean.parseBoolean(value.toString());
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public Long getVersion() { return version; }
  public void setVersion(Long version) { this.version = version; }
}
