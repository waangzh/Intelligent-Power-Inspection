package com.powerinspection.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "agent_runs")
public class AgentRunEntity {
  @Id private String id;
  @Column(name = "case_id", nullable = false) private String caseId;
  @Column(name = "run_number", nullable = false) private int runNumber;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private AgentEnums.RunStatus status;
  @Column(name = "goal_snapshot", nullable = false, columnDefinition = "LONGTEXT") private String goalSnapshot;
  @Column(name = "input_snapshot_json", nullable = false, columnDefinition = "LONGTEXT") private String inputSnapshotJson;
  @Column(name = "conclusion_json", columnDefinition = "LONGTEXT") private String conclusionJson;
  @Column(name = "planner_type", nullable = false) private String plannerType;
  @Column(name = "degraded", nullable = false) private boolean degraded;
  @Column(name = "degradation_reason") private String degradationReason;
  @Column(name = "pending_question_json", columnDefinition = "LONGTEXT") private String pendingQuestionJson;
  @Column(name = "model_name") private String modelName;
  @Column(name = "prompt_version") private String promptVersion;
  @Column(name = "reanalysis_reason") private String reanalysisReason;
  @Column(name = "started_at") private Instant startedAt;
  @Column(name = "completed_at") private Instant completedAt;
  @Column(name = "error_code") private String errorCode;
  @Column(name = "error_message", columnDefinition = "LONGTEXT") private String errorMessage;
  @Column(name = "created_by_id", nullable = false) private String createdById;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Version private long version;
  public String getId() { return id; } public void setId(String value) { id = value; }
  public String getCaseId() { return caseId; } public void setCaseId(String value) { caseId = value; }
  public int getRunNumber() { return runNumber; } public void setRunNumber(int value) { runNumber = value; }
  public AgentEnums.RunStatus getStatus() { return status; } public void setStatus(AgentEnums.RunStatus value) { status = value; }
  public String getGoalSnapshot() { return goalSnapshot; } public void setGoalSnapshot(String value) { goalSnapshot = value; }
  public String getInputSnapshotJson() { return inputSnapshotJson; } public void setInputSnapshotJson(String value) { inputSnapshotJson = value; }
  public String getConclusionJson() { return conclusionJson; } public void setConclusionJson(String value) { conclusionJson = value; }
  public String getPlannerType() { return plannerType; } public void setPlannerType(String value) { plannerType = value; }
  public boolean isDegraded() { return degraded; } public void setDegraded(boolean value) { degraded = value; }
  public String getDegradationReason() { return degradationReason; } public void setDegradationReason(String value) { degradationReason = value; }
  public String getPendingQuestionJson() { return pendingQuestionJson; } public void setPendingQuestionJson(String value) { pendingQuestionJson = value; }
  public String getModelName() { return modelName; } public void setModelName(String value) { modelName = value; }
  public String getPromptVersion() { return promptVersion; } public void setPromptVersion(String value) { promptVersion = value; }
  public String getReanalysisReason() { return reanalysisReason; } public void setReanalysisReason(String value) { reanalysisReason = value; }
  public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant value) { startedAt = value; }
  public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant value) { completedAt = value; }
  public String getErrorCode() { return errorCode; } public void setErrorCode(String value) { errorCode = value; }
  public String getErrorMessage() { return errorMessage; } public void setErrorMessage(String value) { errorMessage = value; }
  public String getCreatedById() { return createdById; } public void setCreatedById(String value) { createdById = value; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
  public long getVersion() { return version; }
}
