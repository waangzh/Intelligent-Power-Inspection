package com.powerinspection.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "task_executions")
public class TaskExecutionEntity {
  @Id
  @Column(name = "task_id")
  private String taskId;
  @Column(name = "execution_id", nullable = false, unique = true)
  private String executionId;
  @Column(name = "route_revision_id", nullable = false)
  private String routeRevisionId;
  @Column(name = "robot_id", nullable = false)
  private String robotId;
  @Column(name = "route_content_sha256", nullable = false, length = 64)
  private String routeContentSha256;
  @Column(name = "map_image_sha256", nullable = false, length = 64)
  private String mapImageSha256;
  @Column(nullable = false)
  private String status;
  @Column(name = "last_robot_sequence", nullable = false)
  private long lastRobotSequence;
  @Column(name = "last_error_code")
  private String lastErrorCode;
  @Column(name = "last_error_message")
  private String lastErrorMessage;
  @Column(name = "created_at", nullable = false)
  private String createdAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private long version;

  public String getTaskId() { return taskId; }
  public void setTaskId(String taskId) { this.taskId = taskId; }
  public String getExecutionId() { return executionId; }
  public void setExecutionId(String executionId) { this.executionId = executionId; }
  public String getRouteRevisionId() { return routeRevisionId; }
  public void setRouteRevisionId(String routeRevisionId) { this.routeRevisionId = routeRevisionId; }
  public String getRobotId() { return robotId; }
  public void setRobotId(String robotId) { this.robotId = robotId; }
  public String getRouteContentSha256() { return routeContentSha256; }
  public void setRouteContentSha256(String routeContentSha256) { this.routeContentSha256 = routeContentSha256; }
  public String getMapImageSha256() { return mapImageSha256; }
  public void setMapImageSha256(String mapImageSha256) { this.mapImageSha256 = mapImageSha256; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public long getLastRobotSequence() { return lastRobotSequence; }
  public void setLastRobotSequence(long lastRobotSequence) { this.lastRobotSequence = lastRobotSequence; }
  public String getLastErrorCode() { return lastErrorCode; }
  public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
  public String getLastErrorMessage() { return lastErrorMessage; }
  public void setLastErrorMessage(String lastErrorMessage) { this.lastErrorMessage = lastErrorMessage; }
  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
  public String getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
