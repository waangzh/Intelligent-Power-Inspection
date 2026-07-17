package com.powerinspection.workorder;

import com.powerinspection.domain.EntityPayloadCodec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "work_orders")
public class WorkOrderEntity {
  private static final Set<String> KNOWN = Set.of(
    "id", "title", "description", "locationDescription", "alarmId", "taskId", "siteId", "source", "status",
    "priority", "assigneeId", "assigneeName", "createdById", "createdByName", "agentActionId",
    "agentIdempotencyKey", "claimedAt", "closedAt", "resolution", "review", "version", "createdAt", "updatedAt"
  );

  @Id
  private String id;
  @Column(nullable = false)
  private String title;
  @Column(columnDefinition = "LONGTEXT")
  private String description;
  @Column(name = "location_description")
  private String locationDescription;
  @Column(name = "alarm_id", unique = true)
  private String alarmId;
  @Column(name = "task_id")
  private String taskId;
  @Column(name = "site_id")
  private String siteId;
  @Column(nullable = false)
  private String source;
  @Column(nullable = false)
  private String status;
  @Column(nullable = false)
  private String priority;
  @Column(name = "assignee_id")
  private String assigneeId;
  @Column(name = "assignee_name")
  private String assigneeName;
  @Column(name = "created_by_id", nullable = false)
  private String createdById;
  @Column(name = "created_by_name", nullable = false)
  private String createdByName;
  @Column(name = "agent_action_id")
  private String agentActionId;
  @Column(name = "agent_idempotency_key")
  private String agentIdempotencyKey;
  @Column(name = "claimed_at")
  private String claimedAt;
  @Column(name = "closed_at")
  private String closedAt;
  @Column(name = "resolution", columnDefinition = "LONGTEXT")
  private String resolution;
  @Column(name = "review_json", columnDefinition = "LONGTEXT")
  private String reviewJson;
  @Column(name = "extra_json", columnDefinition = "LONGTEXT")
  private String extraJson;
  @Column(name = "created_at", nullable = false)
  private String createdAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private Long version;

  public static WorkOrderEntity fromMap(Map<String, Object> map) {
    WorkOrderEntity entity = new WorkOrderEntity();
    entity.apply(map);
    return entity;
  }

  public void apply(Map<String, Object> map) {
    id = text(map.get("id"));
    title = first(map.get("title"), id);
    description = text(map.get("description"));
    locationDescription = text(map.get("locationDescription"));
    alarmId = text(map.get("alarmId"));
    taskId = text(map.get("taskId"));
    siteId = text(map.get("siteId"));
    source = first(map.get("source"), "MANUAL");
    status = first(map.get("status"), "PENDING");
    priority = first(map.get("priority"), "MEDIUM");
    assigneeId = text(map.get("assigneeId"));
    assigneeName = text(map.get("assigneeName"));
    createdById = first(map.get("createdById"), "system");
    createdByName = first(map.get("createdByName"), "系统");
    agentActionId = text(map.get("agentActionId"));
    agentIdempotencyKey = text(map.get("agentIdempotencyKey"));
    claimedAt = text(map.get("claimedAt"));
    closedAt = text(map.get("closedAt"));
    resolution = text(map.get("resolution"));
    reviewJson = EntityPayloadCodec.write(map.get("review"));
    extraJson = EntityPayloadCodec.extraJson(map, KNOWN);
    createdAt = first(map.get("createdAt"), java.time.Instant.now().toString());
    updatedAt = first(map.get("updatedAt"), createdAt);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id);
    map.put("title", title);
    map.put("description", description);
    if (locationDescription != null) map.put("locationDescription", locationDescription);
    if (alarmId != null) map.put("alarmId", alarmId);
    if (taskId != null) map.put("taskId", taskId);
    if (siteId != null) map.put("siteId", siteId);
    map.put("source", source);
    map.put("status", status);
    map.put("priority", priority);
    if (assigneeId != null) map.put("assigneeId", assigneeId);
    if (assigneeName != null) map.put("assigneeName", assigneeName);
    map.put("createdById", createdById);
    map.put("createdByName", createdByName);
    if (agentActionId != null) map.put("agentActionId", agentActionId);
    if (agentIdempotencyKey != null) map.put("agentIdempotencyKey", agentIdempotencyKey);
    if (claimedAt != null) map.put("claimedAt", claimedAt);
    if (closedAt != null) map.put("closedAt", closedAt);
    if (resolution != null) map.put("resolution", resolution);
    Object review = EntityPayloadCodec.readValue(reviewJson);
    if (review != null) map.put("review", review);
    map.put("createdAt", createdAt);
    map.put("updatedAt", updatedAt);
    EntityPayloadCodec.mergeExtra(map, extraJson);
    return map;
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  private static String first(Object value, String fallback) {
    String text = text(value);
    return text == null || text.isBlank() ? fallback : text;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getAlarmId() { return alarmId; }
  public void setAlarmId(String alarmId) { this.alarmId = alarmId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Long getVersion() { return version; }
  public void setVersion(Long version) { this.version = version; }
  public String getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
