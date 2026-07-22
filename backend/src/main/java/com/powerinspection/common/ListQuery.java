package com.powerinspection.common;

import java.util.LinkedHashMap;
import java.util.Map;

public class ListQuery {
  private int page;
  private int size = 20;
  private String sort = "updatedAt";
  private String direction = "desc";
  private String updatedAfter;
  private String q;
  private String siteId;
  private String routeId;
  private String robotId;
  private String taskId;
  private String status;
  private String severity;
  private String acknowledged;
  private String read;
  private String type;
  private String enabled;
  private String sourceType;
  private String detectionRunId;
  private String imageId;
  private String checkpointId;
  private String itemId;

  public Map<String, String> filters(String... allowed) {
    Map<String, String> values = new LinkedHashMap<>();
    for (String field : allowed) {
      String value = switch (field) {
        case "siteId" -> siteId;
        case "routeId" -> routeId;
        case "robotId" -> robotId;
        case "taskId" -> taskId;
        case "status" -> status;
        case "severity" -> severity;
        case "acknowledged" -> acknowledged;
        case "read" -> read;
        case "type" -> type;
        case "enabled" -> enabled;
        case "sourceType" -> sourceType;
        case "detectionRunId" -> detectionRunId;
        case "imageId" -> imageId;
        case "checkpointId" -> checkpointId;
        case "itemId" -> itemId;
        default -> null;
      };
      if (value != null && !value.isBlank()) values.put(field, value);
    }
    return values;
  }

  public int getPage() { return page; }
  public void setPage(int page) { this.page = page; }
  public int getSize() { return size; }
  public void setSize(int size) { this.size = size; }
  public String getSort() { return sort; }
  public void setSort(String sort) { this.sort = sort; }
  public String getDirection() { return direction; }
  public void setDirection(String direction) { this.direction = direction; }
  public String getUpdatedAfter() { return updatedAfter; }
  public void setUpdatedAfter(String updatedAfter) { this.updatedAfter = updatedAfter; }
  public String getQ() { return q; }
  public void setQ(String q) { this.q = q; }
  public String getSiteId() { return siteId; }
  public void setSiteId(String siteId) { this.siteId = siteId; }
  public String getRouteId() { return routeId; }
  public void setRouteId(String routeId) { this.routeId = routeId; }
  public String getRobotId() { return robotId; }
  public void setRobotId(String robotId) { this.robotId = robotId; }
  public String getTaskId() { return taskId; }
  public void setTaskId(String taskId) { this.taskId = taskId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getSeverity() { return severity; }
  public void setSeverity(String severity) { this.severity = severity; }
  public String getAcknowledged() { return acknowledged; }
  public void setAcknowledged(String acknowledged) { this.acknowledged = acknowledged; }
  public String getRead() { return read; }
  public void setRead(String read) { this.read = read; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public String getEnabled() { return enabled; }
  public void setEnabled(String enabled) { this.enabled = enabled; }
  public String getSourceType() { return sourceType; }
  public void setSourceType(String sourceType) { this.sourceType = sourceType; }
  public String getDetectionRunId() { return detectionRunId; }
  public void setDetectionRunId(String detectionRunId) { this.detectionRunId = detectionRunId; }
  public String getImageId() { return imageId; }
  public void setImageId(String imageId) { this.imageId = imageId; }
  public String getCheckpointId() { return checkpointId; }
  public void setCheckpointId(String checkpointId) { this.checkpointId = checkpointId; }
  public String getItemId() { return itemId; }
  public void setItemId(String itemId) { this.itemId = itemId; }
}
