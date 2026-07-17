package com.powerinspection.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "task_events")
public class TaskEventEntity {
  @Id
  private String id;
  @Column(name = "task_id", nullable = false)
  private String taskId;
  @Column(nullable = false)
  private String type;
  private String message;
  @Column(name = "checkpoint_name")
  private String checkpointName;
  @Column(name = "image_url")
  private String imageUrl;
  @Column(name = "payload_json", columnDefinition = "LONGTEXT")
  private String payloadJson;
  @Column(name = "created_at", nullable = false)
  private String createdAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private Long version;

  public static TaskEventEntity fromMap(Map<String, Object> map) {
    TaskEventEntity entity = new TaskEventEntity();
    entity.id = text(map.get("id"));
    entity.taskId = first(map.get("taskId"), "");
    entity.type = first(map.get("type"), "INFO");
    entity.message = text(map.get("message"));
    entity.checkpointName = text(map.get("checkpointName"));
    entity.imageUrl = text(map.get("imageUrl"));
    try { entity.payloadJson = new ObjectMapper().writeValueAsString(map); } catch (Exception ignored) { entity.payloadJson = null; }
    entity.createdAt = first(map.get("createdAt"), Instant.now().toString());
    entity.updatedAt = first(map.get("updatedAt"), entity.createdAt);
    return entity;
  }

  private static String text(Object value) { return value == null ? null : value.toString(); }
  private static String first(Object value, String fallback) {
    String text = text(value);
    return text == null || text.isBlank() ? fallback : text;
  }
}
