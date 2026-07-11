package com.powerinspection.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_action_execution_claims")
public class AgentActionExecutionClaimEntity {
  @Id private String id;
  @Column(name = "idempotency_key", nullable = false, unique = true) private String idempotencyKey;
  @Column(name = "action_id", nullable = false) private String actionId;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private AgentEnums.ExecutionClaimStatus status;
  @Column(name = "result_json", columnDefinition = "LONGTEXT") private String resultJson;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  public String getId() { return id; } public void setId(String value) { id = value; }
  public String getIdempotencyKey() { return idempotencyKey; } public void setIdempotencyKey(String value) { idempotencyKey = value; }
  public String getActionId() { return actionId; } public void setActionId(String value) { actionId = value; }
  public AgentEnums.ExecutionClaimStatus getStatus() { return status; } public void setStatus(AgentEnums.ExecutionClaimStatus value) { status = value; }
  public String getResultJson() { return resultJson; } public void setResultJson(String value) { resultJson = value; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
  public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}
