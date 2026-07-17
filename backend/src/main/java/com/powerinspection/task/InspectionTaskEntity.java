package com.powerinspection.task;

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
@Table(name = "inspection_tasks")
public class InspectionTaskEntity {
  private static final Set<String> KNOWN = Set.of(
    "id", "name", "siteId", "routeId", "robotId", "routeRevisionId", "executionId",
    "status", "progress", "currentCheckpointSeq", "startedAt", "completedAt",
    "createdAt", "updatedAt"
  );

  @Id
  private String id;
  @Column(nullable = false)
  private String name;
  @Column(name = "site_id")
  private String siteId;
  @Column(name = "route_id", nullable = false)
  private String routeId;
  @Column(name = "robot_id", nullable = false)
  private String robotId;
  @Column(name = "route_revision_id")
  private String routeRevisionId;
  @Column(name = "execution_id")
  private String executionId;
  @Column(nullable = false)
  private String status;
  @Column(nullable = false)
  private int progress;
  @Column(name = "current_checkpoint_seq", nullable = false)
  private int currentCheckpointSeq;
  @Column(name = "started_at")
  private String startedAt;
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

  public static InspectionTaskEntity fromMap(Map<String, Object> map) {
    InspectionTaskEntity entity = new InspectionTaskEntity();
    entity.apply(map);
    return entity;
  }

  public void apply(Map<String, Object> map) {
    id = text(map.get("id"));
    name = first(map.get("name"), id);
    siteId = text(map.get("siteId"));
    routeId = first(map.get("routeId"), "");
    robotId = first(map.get("robotId"), "");
    routeRevisionId = text(map.get("routeRevisionId"));
    executionId = text(map.get("executionId"));
    status = first(map.get("status"), "CREATED");
    progress = integer(map.get("progress"), 0);
    currentCheckpointSeq = integer(map.get("currentCheckpointSeq"), 0);
    startedAt = text(map.get("startedAt"));
    completedAt = text(map.get("completedAt"));
    extraJson = EntityPayloadCodec.extraJson(map, KNOWN);
    createdAt = first(map.get("createdAt"), Instant.now().toString());
    updatedAt = first(map.get("updatedAt"), createdAt);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id);
    map.put("name", name);
    if (siteId != null) map.put("siteId", siteId);
    map.put("routeId", routeId);
    map.put("robotId", robotId);
    if (routeRevisionId != null) map.put("routeRevisionId", routeRevisionId);
    if (executionId != null) map.put("executionId", executionId);
    map.put("status", status);
    map.put("progress", progress);
    map.put("currentCheckpointSeq", currentCheckpointSeq);
    if (startedAt != null) map.put("startedAt", startedAt);
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
