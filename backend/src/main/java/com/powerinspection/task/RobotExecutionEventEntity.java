package com.powerinspection.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "robot_execution_events")
public class RobotExecutionEventEntity {
  @Id
  private String id;
  @Column(name = "robot_id", nullable = false) private String robotId;
  @Column(name = "execution_id", nullable = false) private String executionId;
  @Column(name = "deployment_id", nullable = false) private String deploymentId;
  @Column(name = "event_id", nullable = false, unique = true) private String eventId;
  @Column(nullable = false) private long sequence;
  @Column(name = "event_type", nullable = false) private String eventType;
  @Column(name = "occurred_at", nullable = false) private String occurredAt;
  @Column(name = "received_at", nullable = false) private String receivedAt;
  @Column(name = "payload_summary", columnDefinition = "LONGTEXT") private String payloadSummary;
  @Column(name = "error_code") private String errorCode;
  @Column(name = "error_message") private String errorMessage;
  @Column(name = "processing_result", nullable = false) private String processingResult;
  @Column(name = "conflict_code") private String conflictCode;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getRobotId() { return robotId; }
  public void setRobotId(String robotId) { this.robotId = robotId; }
  public String getExecutionId() { return executionId; }
  public void setExecutionId(String executionId) { this.executionId = executionId; }
  public String getDeploymentId() { return deploymentId; }
  public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
  public String getEventId() { return eventId; }
  public void setEventId(String eventId) { this.eventId = eventId; }
  public long getSequence() { return sequence; }
  public void setSequence(long sequence) { this.sequence = sequence; }
  public String getEventType() { return eventType; }
  public void setEventType(String eventType) { this.eventType = eventType; }
  public String getOccurredAt() { return occurredAt; }
  public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
  public String getReceivedAt() { return receivedAt; }
  public void setReceivedAt(String receivedAt) { this.receivedAt = receivedAt; }
  public String getPayloadSummary() { return payloadSummary; }
  public void setPayloadSummary(String payloadSummary) { this.payloadSummary = payloadSummary; }
  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
  public String getProcessingResult() { return processingResult; }
  public void setProcessingResult(String processingResult) { this.processingResult = processingResult; }
  public String getConflictCode() { return conflictCode; }
  public void setConflictCode(String conflictCode) { this.conflictCode = conflictCode; }
}
