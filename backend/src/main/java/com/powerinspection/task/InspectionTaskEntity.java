package com.powerinspection.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "inspection_tasks")
public class InspectionTaskEntity {
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
    entity.id = text(map.get("id"));
    entity.name = first(map.get("name"), entity.id);
    entity.siteId = text(map.get("siteId"));
    entity.routeId = first(map.get("routeId"), "");
    entity.robotId = first(map.get("robotId"), "");
    entity.routeRevisionId = text(map.get("routeRevisionId"));
    entity.executionId = text(map.get("executionId"));
    entity.status = first(map.get("status"), "CREATED");
    entity.progress = integer(map.get("progress"), 0);
    entity.currentCheckpointSeq = integer(map.get("currentCheckpointSeq"), 0);
    entity.startedAt = text(map.get("startedAt"));
    entity.completedAt = text(map.get("completedAt"));
    entity.createdAt = first(map.get("createdAt"), Instant.now().toString());
    entity.updatedAt = first(map.get("updatedAt"), entity.createdAt);
    return entity;
  }

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
