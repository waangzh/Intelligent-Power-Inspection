package com.powerinspection.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_steps")
public class AgentStepEntity {
  @Id private String id;
  @Column(name = "case_id", nullable = false) private String caseId;
  @Column(name = "run_id", nullable = false) private String runId;
  @Column(name = "sequence_no", nullable = false) private int sequenceNo;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private AgentEnums.StepType type;
  @Column(nullable = false) private String summary;
  @Column(name = "detail_json", columnDefinition = "LONGTEXT") private String detailJson;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  public String getId() { return id; } public void setId(String value) { id = value; }
  public String getCaseId() { return caseId; } public void setCaseId(String value) { caseId = value; }
  public String getRunId() { return runId; } public void setRunId(String value) { runId = value; }
  public int getSequenceNo() { return sequenceNo; } public void setSequenceNo(int value) { sequenceNo = value; }
  public AgentEnums.StepType getType() { return type; } public void setType(AgentEnums.StepType value) { type = value; }
  public String getSummary() { return summary; } public void setSummary(String value) { summary = value; }
  public String getDetailJson() { return detailJson; } public void setDetailJson(String value) { detailJson = value; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
}
