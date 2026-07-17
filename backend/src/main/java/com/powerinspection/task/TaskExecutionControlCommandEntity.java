package com.powerinspection.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "task_execution_control_commands")
public class TaskExecutionControlCommandEntity {
  @Id private String id;
  @Column(name = "task_id", nullable = false) private String taskId;
  @Column(name = "execution_id", nullable = false) private String executionId;
  @Column(name = "robot_id", nullable = false) private String robotId;
  @Column(name = "deployment_id", nullable = false) private String deploymentId;
  @Column(nullable = false) private String action;
  @Column(name = "request_id", nullable = false, unique = true) private String requestId;
  @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
  @Column(nullable = false) private String status;
  @Column(name = "command_id") private String commandId;
  @Column(name = "prior_execution_status", nullable = false) private String priorExecutionStatus;
  @Column(name = "takeover_reason", length = 500) private String takeoverReason;
  @Column(name = "requested_by_id", nullable = false) private String requestedById;
  @Column(name = "requested_by_name", nullable = false) private String requestedByName;
  @Column(name = "requested_at", nullable = false) private String requestedAt;
  @Column(name = "last_attempt_at") private String lastAttemptAt;
  @Column(name = "acked_at") private String ackedAt;
  @Column(name = "confirmed_at") private String confirmedAt;
  @Column(name = "recovery_action") private String recoveryAction;
  @Column(name = "result_code") private String resultCode;
  @Column(name = "result_message", length = 500) private String resultMessage;
  @Column(name = "created_at", nullable = false) private String createdAt;
  @Column(name = "updated_at", nullable = false) private String updatedAt;
  @Version private long version;

  public String getId() { return id; } public void setId(String value) { id = value; }
  public String getTaskId() { return taskId; } public void setTaskId(String value) { taskId = value; }
  public String getExecutionId() { return executionId; } public void setExecutionId(String value) { executionId = value; }
  public String getRobotId() { return robotId; } public void setRobotId(String value) { robotId = value; }
  public String getDeploymentId() { return deploymentId; } public void setDeploymentId(String value) { deploymentId = value; }
  public String getAction() { return action; } public void setAction(String value) { action = value; }
  public String getRequestId() { return requestId; } public void setRequestId(String value) { requestId = value; }
  public String getRequestFingerprint() { return requestFingerprint; } public void setRequestFingerprint(String value) { requestFingerprint = value; }
  public String getStatus() { return status; } public void setStatus(String value) { status = value; }
  public String getCommandId() { return commandId; } public void setCommandId(String value) { commandId = value; }
  public String getPriorExecutionStatus() { return priorExecutionStatus; } public void setPriorExecutionStatus(String value) { priorExecutionStatus = value; }
  public String getTakeoverReason() { return takeoverReason; } public void setTakeoverReason(String value) { takeoverReason = value; }
  public String getRequestedById() { return requestedById; } public void setRequestedById(String value) { requestedById = value; }
  public String getRequestedByName() { return requestedByName; } public void setRequestedByName(String value) { requestedByName = value; }
  public String getRequestedAt() { return requestedAt; } public void setRequestedAt(String value) { requestedAt = value; }
  public String getLastAttemptAt() { return lastAttemptAt; } public void setLastAttemptAt(String value) { lastAttemptAt = value; }
  public String getAckedAt() { return ackedAt; } public void setAckedAt(String value) { ackedAt = value; }
  public String getConfirmedAt() { return confirmedAt; } public void setConfirmedAt(String value) { confirmedAt = value; }
  public String getRecoveryAction() { return recoveryAction; } public void setRecoveryAction(String value) { recoveryAction = value; }
  public String getResultCode() { return resultCode; } public void setResultCode(String value) { resultCode = value; }
  public String getResultMessage() { return resultMessage; } public void setResultMessage(String value) { resultMessage = value; }
  public String getCreatedAt() { return createdAt; } public void setCreatedAt(String value) { createdAt = value; }
  public String getUpdatedAt() { return updatedAt; } public void setUpdatedAt(String value) { updatedAt = value; }
}
