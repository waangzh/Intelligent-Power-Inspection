package com.powerinspection.workorder;

import java.util.LinkedHashMap;
import java.util.Map;

public class CreateWorkOrderFromAlarmRequest {
  private String title;
  private String description;
  private String locationDescription;
  private String priority;
  private String assigneeName;
  private String taskId;
  private String agentActionId;
  private String agentIdempotencyKey;

  public static CreateWorkOrderFromAlarmRequest fromMap(Map<String, Object> body) {
    CreateWorkOrderFromAlarmRequest request = new CreateWorkOrderFromAlarmRequest();
    if (body == null) {
      return request;
    }
    request.title = text(body.get("title"));
    request.description = text(body.get("description"));
    request.locationDescription = text(body.get("locationDescription"));
    request.priority = text(body.get("priority"));
    request.assigneeName = text(body.get("assigneeName"));
    request.taskId = text(body.get("taskId"));
    request.agentActionId = text(body.get("agentActionId"));
    request.agentIdempotencyKey = text(body.get("agentIdempotencyKey"));
    return request;
  }

  public Map<String, Object> overrides() {
    Map<String, Object> values = new LinkedHashMap<>();
    put(values, "title", title);
    put(values, "description", description);
    put(values, "locationDescription", locationDescription);
    put(values, "priority", priority);
    put(values, "assigneeName", assigneeName);
    put(values, "taskId", taskId);
    put(values, "agentActionId", agentActionId);
    put(values, "agentIdempotencyKey", agentIdempotencyKey);
    return values;
  }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getLocationDescription() { return locationDescription; }
  public void setLocationDescription(String locationDescription) { this.locationDescription = locationDescription; }
  public String getPriority() { return priority; }
  public void setPriority(String priority) { this.priority = priority; }
  public String getAssigneeName() { return assigneeName; }
  public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }
  public String getTaskId() { return taskId; }
  public void setTaskId(String taskId) { this.taskId = taskId; }
  public String getAgentActionId() { return agentActionId; }
  public void setAgentActionId(String agentActionId) { this.agentActionId = agentActionId; }
  public String getAgentIdempotencyKey() { return agentIdempotencyKey; }
  public void setAgentIdempotencyKey(String agentIdempotencyKey) { this.agentIdempotencyKey = agentIdempotencyKey; }

  private static void put(Map<String, Object> values, String key, String value) {
    if (value != null && !value.isBlank()) {
      values.put(key, value);
    }
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }
}
