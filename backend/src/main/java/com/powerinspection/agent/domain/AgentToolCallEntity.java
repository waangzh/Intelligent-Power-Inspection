package com.powerinspection.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_tool_calls")
public class AgentToolCallEntity {
  @Id private String id;
  @Column(name = "case_id", nullable = false) private String caseId;
  @Column(name = "run_id", nullable = false) private String runId;
  @Column(name = "step_no", nullable = false) private int stepNo;
  @Column(name = "sequence_no") private Integer sequenceNo;
  @Column(name = "tool_name", nullable = false) private String toolName;
  @Column(name = "arguments_json", nullable = false, columnDefinition = "LONGTEXT") private String argumentsJson;
  @Column(name = "arguments_hash") private String argumentsHash;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private AgentEnums.ToolCallStatus status;
  @Column(nullable = false) private String reason;
  @Column(name = "started_at", nullable = false) private Instant startedAt;
  @Column(name = "completed_at") private Instant completedAt;
  @Column(name = "duration_ms") private Long durationMs;
  @Column(name = "result_summary") private String resultSummary;
  @Column(name = "error_code") private String errorCode;
  @Column(name = "error_message", columnDefinition = "LONGTEXT") private String errorMessage;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  public String getId() { return id; } public void setId(String value) { id = value; }
  public String getCaseId() { return caseId; } public void setCaseId(String value) { caseId = value; }
  public String getRunId() { return runId; } public void setRunId(String value) { runId = value; }
  public int getStepNo() { return stepNo; } public void setStepNo(int value) { stepNo = value; }
  public Integer getSequenceNo() { return sequenceNo; } public void setSequenceNo(Integer value) { sequenceNo = value; }
  public String getToolName() { return toolName; } public void setToolName(String value) { toolName = value; }
  public String getArgumentsJson() { return argumentsJson; } public void setArgumentsJson(String value) { argumentsJson = value; }
  public String getArgumentsHash() { return argumentsHash; } public void setArgumentsHash(String value) { argumentsHash = value; }
  public AgentEnums.ToolCallStatus getStatus() { return status; } public void setStatus(AgentEnums.ToolCallStatus value) { status = value; }
  public String getReason() { return reason; } public void setReason(String value) { reason = value; }
  public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant value) { startedAt = value; }
  public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant value) { completedAt = value; }
  public Long getDurationMs() { return durationMs; } public void setDurationMs(Long value) { durationMs = value; }
  public String getResultSummary() { return resultSummary; } public void setResultSummary(String value) { resultSummary = value; }
  public String getErrorCode() { return errorCode; } public void setErrorCode(String value) { errorCode = value; }
  public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String value) { errorMessage = value; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
}
