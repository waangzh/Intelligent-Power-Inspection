package com.powerinspection.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_evidence")
public class AgentEvidenceEntity {
  @Id private String id;
  @Column(name = "case_id", nullable = false) private String caseId;
  @Column(name = "run_id", nullable = false) private String runId;
  @Column(name = "tool_call_id") private String toolCallId;
  @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false) private AgentEnums.EvidenceSourceType sourceType;
  @Column(name = "source_id") private String sourceId;
  @Column(nullable = false) private String title;
  @Column(nullable = false) private String summary;
  @Column(name = "content_type", nullable = false) private String contentType;
  @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT") private String payloadJson;
  @Column(name = "content_hash", nullable = false) private String contentHash;
  @Column(name = "collected_at", nullable = false) private Instant collectedAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  public String getId() { return id; } public void setId(String value) { id = value; }
  public String getCaseId() { return caseId; } public void setCaseId(String value) { caseId = value; }
  public String getRunId() { return runId; } public void setRunId(String value) { runId = value; }
  public String getToolCallId() { return toolCallId; } public void setToolCallId(String value) { toolCallId = value; }
  public AgentEnums.EvidenceSourceType getSourceType() { return sourceType; } public void setSourceType(AgentEnums.EvidenceSourceType value) { sourceType = value; }
  public String getSourceId() { return sourceId; } public void setSourceId(String value) { sourceId = value; }
  public String getTitle() { return title; } public void setTitle(String value) { title = value; }
  public String getSummary() { return summary; } public void setSummary(String value) { summary = value; }
  public String getContentType() { return contentType; } public void setContentType(String value) { contentType = value; }
  public String getPayloadJson() { return payloadJson; } public void setPayloadJson(String value) { payloadJson = value; }
  public String getContentHash() { return contentHash; } public void setContentHash(String value) { contentHash = value; }
  public Instant getCollectedAt() { return collectedAt; } public void setCollectedAt(Instant value) { collectedAt = value; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
}
