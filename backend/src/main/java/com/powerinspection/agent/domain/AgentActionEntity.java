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
@Table(name = "agent_actions")
public class AgentActionEntity {
  @Id private String id;

  @Column(name = "case_id", nullable = false)
  private String caseId;

  @Column(name = "run_id", nullable = false)
  private String runId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AgentEnums.ActionType type;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "risk_level", nullable = false)
  private AgentEnums.RiskLevel riskLevel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AgentEnums.ActionStatus status;

  @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
  private String payloadJson;

  @Column(nullable = false)
  private double confidence;

  @Column(name = "evidence_ids_json", nullable = false, columnDefinition = "LONGTEXT")
  private String evidenceIdsJson;

  @Column(name = "payload_audit_json", columnDefinition = "LONGTEXT")
  private String payloadAuditJson;

  @Enumerated(EnumType.STRING)
  @Column(name = "policy_decision", nullable = false)
  private AgentEnums.PolicyDecisionType policyDecision;

  @Column(name = "policy_code", nullable = false)
  private String policyCode;

  @Column(name = "policy_reason", nullable = false, columnDefinition = "LONGTEXT")
  private String policyReason;

  @Column(name = "requires_approval", nullable = false)
  private boolean requiresApproval;

  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "requested_by_id")
  private String requestedById;

  @Column(name = "approved_by_id")
  private String approvedById;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "approval_comment")
  private String approvalComment;

  @Column(name = "rejected_by_id")
  private String rejectedById;

  @Column(name = "rejected_at")
  private Instant rejectedAt;

  @Column(name = "rejection_comment")
  private String rejectionComment;

  @Column(name = "execution_started_at")
  private Instant executionStartedAt;

  @Column(name = "execution_completed_at")
  private Instant executionCompletedAt;

  @Column(name = "result_json", columnDefinition = "LONGTEXT")
  private String resultJson;

  @Column(name = "error_code")
  private String errorCode;

  @Column(name = "error_message", columnDefinition = "LONGTEXT")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  public String getId() {
    return id;
  }

  public void setId(String value) {
    id = value;
  }

  public String getCaseId() {
    return caseId;
  }

  public void setCaseId(String value) {
    caseId = value;
  }

  public String getRunId() {
    return runId;
  }

  public void setRunId(String value) {
    runId = value;
  }

  public AgentEnums.ActionType getType() {
    return type;
  }

  public void setType(AgentEnums.ActionType value) {
    type = value;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String value) {
    title = value;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String value) {
    reason = value;
  }

  public AgentEnums.RiskLevel getRiskLevel() {
    return riskLevel;
  }

  public void setRiskLevel(AgentEnums.RiskLevel value) {
    riskLevel = value;
  }

  public AgentEnums.ActionStatus getStatus() {
    return status;
  }

  public void setStatus(AgentEnums.ActionStatus value) {
    status = value;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String value) {
    payloadJson = value;
  }

  public double getConfidence() {
    return confidence;
  }

  public void setConfidence(double value) {
    confidence = value;
  }

  public String getEvidenceIdsJson() {
    return evidenceIdsJson;
  }

  public void setEvidenceIdsJson(String value) {
    evidenceIdsJson = value;
  }

  public String getPayloadAuditJson() {
    return payloadAuditJson;
  }

  public void setPayloadAuditJson(String value) {
    payloadAuditJson = value;
  }

  public AgentEnums.PolicyDecisionType getPolicyDecision() {
    return policyDecision;
  }

  public void setPolicyDecision(AgentEnums.PolicyDecisionType value) {
    policyDecision = value;
  }

  public String getPolicyCode() {
    return policyCode;
  }

  public void setPolicyCode(String value) {
    policyCode = value;
  }

  public String getPolicyReason() {
    return policyReason;
  }

  public void setPolicyReason(String value) {
    policyReason = value;
  }

  public boolean isRequiresApproval() {
    return requiresApproval;
  }

  public void setRequiresApproval(boolean value) {
    requiresApproval = value;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String value) {
    idempotencyKey = value;
  }

  public String getRequestedById() {
    return requestedById;
  }

  public void setRequestedById(String value) {
    requestedById = value;
  }

  public String getApprovedById() {
    return approvedById;
  }

  public void setApprovedById(String value) {
    approvedById = value;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public void setApprovedAt(Instant value) {
    approvedAt = value;
  }

  public String getApprovalComment() {
    return approvalComment;
  }

  public void setApprovalComment(String value) {
    approvalComment = value;
  }

  public String getRejectedById() {
    return rejectedById;
  }

  public void setRejectedById(String value) {
    rejectedById = value;
  }

  public Instant getRejectedAt() {
    return rejectedAt;
  }

  public void setRejectedAt(Instant value) {
    rejectedAt = value;
  }

  public String getRejectionComment() {
    return rejectionComment;
  }

  public void setRejectionComment(String value) {
    rejectionComment = value;
  }

  public Instant getExecutionStartedAt() {
    return executionStartedAt;
  }

  public void setExecutionStartedAt(Instant value) {
    executionStartedAt = value;
  }

  public Instant getExecutionCompletedAt() {
    return executionCompletedAt;
  }

  public void setExecutionCompletedAt(Instant value) {
    executionCompletedAt = value;
  }

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String value) {
    resultJson = value;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String value) {
    errorCode = value;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String value) {
    errorMessage = value;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant value) {
    createdAt = value;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant value) {
    updatedAt = value;
  }

  public long getVersion() {
    return version;
  }
}
