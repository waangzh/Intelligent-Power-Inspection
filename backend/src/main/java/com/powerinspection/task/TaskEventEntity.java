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
@Table(name = "task_events")
public class TaskEventEntity {
  private static final Set<String> KNOWN = Set.of(
    "id", "taskId", "type", "message", "checkpointName", "imageUrl", "createdAt", "updatedAt"
  );

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
    entity.apply(map);
    return entity;
  }

  public void apply(Map<String, Object> map) {
    id = text(map.get("id"));
    taskId = first(map.get("taskId"), "");
    type = first(map.get("type"), "INFO");
    message = text(map.get("message"));
    checkpointName = text(map.get("checkpointName"));
    imageUrl = text(map.get("imageUrl"));
    payloadJson = EntityPayloadCodec.extraJson(map, KNOWN);
    createdAt = first(map.get("createdAt"), Instant.now().toString());
    updatedAt = first(map.get("updatedAt"), createdAt);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id);
    map.put("taskId", taskId);
    map.put("type", type);
    if (message != null) map.put("message", message);
    if (checkpointName != null) map.put("checkpointName", checkpointName);
    if (imageUrl != null) map.put("imageUrl", imageUrl);
    map.put("createdAt", createdAt);
    map.put("updatedAt", updatedAt);
    EntityPayloadCodec.mergeExtra(map, payloadJson);
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
}
