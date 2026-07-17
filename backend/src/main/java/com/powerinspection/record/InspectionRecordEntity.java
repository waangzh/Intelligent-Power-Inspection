package com.powerinspection.record;

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
@Table(name = "inspection_records")
public class InspectionRecordEntity {
  private static final Set<String> KNOWN = Set.of(
    "id", "taskId", "siteId", "routeId", "robotId", "taskName", "routeName", "robotName",
    "alarmCount", "checkpointCount", "duration", "summary", "completedAt", "version", "createdAt", "updatedAt"
  );

  @Id
  private String id;
  @Column(name = "task_id")
  private String taskId;
  @Column(name = "site_id")
  private String siteId;
  @Column(name = "route_id")
  private String routeId;
  @Column(name = "robot_id")
  private String robotId;
  @Column(name = "task_name")
  private String taskName;
  @Column(name = "route_name")
  private String routeName;
  @Column(name = "robot_name")
  private String robotName;
  @Column(name = "alarm_count", nullable = false)
  private int alarmCount;
  @Column(name = "checkpoint_count", nullable = false)
  private int checkpointCount;
  private String duration;
  private String summary;
  @Column(name = "completed_at")
  private String completedAt;
  @Column(name = "extra_json", columnDefinition = "LONGTEXT")
  private String extraJson;
  @Column(name = "created_at", nullable = false)
  private String createdAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private Long version;

  public static InspectionRecordEntity fromMap(Map<String, Object> map) {
    InspectionRecordEntity entity = new InspectionRecordEntity();
    entity.apply(map);
    return entity;
  }

  public void apply(Map<String, Object> map) {
    id = text(map.get("id"));
    taskId = text(map.get("taskId"));
    siteId = text(map.get("siteId"));
    routeId = text(map.get("routeId"));
    robotId = text(map.get("robotId"));
    taskName = text(map.get("taskName"));
    routeName = text(map.get("routeName"));
    robotName = text(map.get("robotName"));
    alarmCount = integer(map.get("alarmCount"), 0);
    checkpointCount = integer(map.get("checkpointCount"), 0);
    duration = text(map.get("duration"));
    summary = text(map.get("summary"));
    completedAt = text(map.get("completedAt"));
    extraJson = EntityPayloadCodec.extraJson(map, KNOWN);
    createdAt = first(map.get("createdAt"), Instant.now().toString());
    updatedAt = first(map.get("updatedAt"), createdAt);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id);
    if (taskId != null) map.put("taskId", taskId);
    if (siteId != null) map.put("siteId", siteId);
    if (routeId != null) map.put("routeId", routeId);
    if (robotId != null) map.put("robotId", robotId);
    if (taskName != null) map.put("taskName", taskName);
    if (routeName != null) map.put("routeName", routeName);
    if (robotName != null) map.put("robotName", robotName);
    map.put("alarmCount", alarmCount);
    map.put("checkpointCount", checkpointCount);
    if (duration != null) map.put("duration", duration);
    if (summary != null) map.put("summary", summary);
    if (completedAt != null) map.put("completedAt", completedAt);
    map.put("createdAt", createdAt);
    map.put("updatedAt", updatedAt);
    EntityPayloadCodec.mergeExtra(map, extraJson);
    return map;
  }

  public String getId() { return id; }
  public Long getVersion() { return version; }
  public void setVersion(Long version) { this.version = version; }

  private static String text(Object value) { return value == null ? null : value.toString(); }
  private static String first(Object value, String fallback) {
    String text = text(value);
    return text == null || text.isBlank() ? fallback : text;
  }
  private static int integer(Object value, int fallback) {
    if (value instanceof Number number) return number.intValue();
    if (value == null) return fallback;
    try { return Integer.parseInt(value.toString()); } catch (Exception ex) { return fallback; }
  }
}
