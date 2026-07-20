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
  @Column(name = "deployment_id")
  private String deploymentId;
  @Column(name = "executor_route_id")
  private String executorRouteId;
  @Column(name = "start_request_id", unique = true)
  private String startRequestId;
  @Column(name = "start_request_fingerprint", length = 64)
  private String startRequestFingerprint;
  @Column(name = "start_command_id")
  private String startCommandId;
  @Column(name = "start_mode", nullable = false)
  private String startMode = TaskStartMode.REMOTE_IMMEDIATE.name();
  @Column(name = "operator_id")
  private String operatorId;
  @Column(name = "start_requested_at")
  private String startRequestedAt;
  @Column(name = "robot_ready_at")
  private String robotReadyAt;
  @Column(name = "local_confirmed_at")
  private String localConfirmedAt;
  @Column(name = "started_at")
  private String startedAt;
  @Column(name = "start_attempt_no", nullable = false)
  private int startAttemptNo;
  @Column(name = "last_start_attempt_at")
  private String lastStartAttemptAt;
  @Column(name = "recovery_status")
  private String recoveryStatus;
  @Column(name = "current_target_id")
  private String currentTargetId;
  @Column(nullable = false)
  private int progress;
  @Column(name = "last_event_at")
  private String lastEventAt;
  @Column(name = "manual_reconciliation_required", nullable = false)
  private boolean manualReconciliationRequired;
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
  public String getDeploymentId() { return deploymentId; }
  public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
  public String getExecutorRouteId() { return executorRouteId; }
  public void setExecutorRouteId(String executorRouteId) { this.executorRouteId = executorRouteId; }
  public String getStartRequestId() { return startRequestId; }
  public void setStartRequestId(String startRequestId) { this.startRequestId = startRequestId; }
  public String getStartRequestFingerprint() { return startRequestFingerprint; }
  public void setStartRequestFingerprint(String startRequestFingerprint) { this.startRequestFingerprint = startRequestFingerprint; }
  public String getStartCommandId() { return startCommandId; }
  public void setStartCommandId(String startCommandId) { this.startCommandId = startCommandId; }
  public String getStartMode() { return startMode; }
  public void setStartMode(String startMode) { this.startMode = startMode; }
  public String getOperatorId() { return operatorId; }
  public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
  public String getStartRequestedAt() { return startRequestedAt; }
  public void setStartRequestedAt(String startRequestedAt) { this.startRequestedAt = startRequestedAt; }
  public String getRobotReadyAt() { return robotReadyAt; }
  public void setRobotReadyAt(String robotReadyAt) { this.robotReadyAt = robotReadyAt; }
  public String getLocalConfirmedAt() { return localConfirmedAt; }
  public void setLocalConfirmedAt(String localConfirmedAt) { this.localConfirmedAt = localConfirmedAt; }
  public String getStartedAt() { return startedAt; }
  public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
  public int getStartAttemptNo() { return startAttemptNo; }
  public void setStartAttemptNo(int startAttemptNo) { this.startAttemptNo = startAttemptNo; }
  public String getLastStartAttemptAt() { return lastStartAttemptAt; }
  public void setLastStartAttemptAt(String lastStartAttemptAt) { this.lastStartAttemptAt = lastStartAttemptAt; }
  public String getRecoveryStatus() { return recoveryStatus; }
  public void setRecoveryStatus(String recoveryStatus) { this.recoveryStatus = recoveryStatus; }
  public String getCurrentTargetId() { return currentTargetId; }
  public void setCurrentTargetId(String currentTargetId) { this.currentTargetId = currentTargetId; }
  public int getProgress() { return progress; }
  public void setProgress(int progress) { this.progress = progress; }
  public String getLastEventAt() { return lastEventAt; }
  public void setLastEventAt(String lastEventAt) { this.lastEventAt = lastEventAt; }
  public boolean isManualReconciliationRequired() { return manualReconciliationRequired; }
  public void setManualReconciliationRequired(boolean manualReconciliationRequired) { this.manualReconciliationRequired = manualReconciliationRequired; }
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
