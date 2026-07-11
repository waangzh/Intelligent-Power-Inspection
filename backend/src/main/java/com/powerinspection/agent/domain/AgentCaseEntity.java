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
@Table(name = "agent_cases")
public class AgentCaseEntity {
  @Id private String id;
  @Column(nullable = false) private String title;
  @Column(nullable = false, columnDefinition = "LONGTEXT") private String goal;
  @Column(name = "operator_note", columnDefinition = "LONGTEXT") private String operatorNote;
  @Column(name = "trigger_type", nullable = false) private String triggerType;
  @Column(name = "task_id") private String taskId;
  @Column(name = "alarm_id") private String alarmId;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private AgentEnums.CaseStatus status;
  @Column(nullable = false) private String priority;
  @Column(name = "created_by_id", nullable = false) private String createdById;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Column(name = "resolved_at") private Instant resolvedAt;
  @Column(name = "closed_at") private Instant closedAt;
  @Version private long version;
  public String getId() { return id; } public void setId(String value) { id = value; }
  public String getTitle() { return title; } public void setTitle(String value) { title = value; }
  public String getGoal() { return goal; } public void setGoal(String value) { goal = value; }
  public String getOperatorNote() { return operatorNote; } public void setOperatorNote(String value) { operatorNote = value; }
  public String getTriggerType() { return triggerType; } public void setTriggerType(String value) { triggerType = value; }
  public String getTaskId() { return taskId; } public void setTaskId(String value) { taskId = value; }
  public String getAlarmId() { return alarmId; } public void setAlarmId(String value) { alarmId = value; }
  public AgentEnums.CaseStatus getStatus() { return status; } public void setStatus(AgentEnums.CaseStatus value) { status = value; }
  public String getPriority() { return priority; } public void setPriority(String value) { priority = value; }
  public String getCreatedById() { return createdById; } public void setCreatedById(String value) { createdById = value; }
  public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
  public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
  public Instant getResolvedAt() { return resolvedAt; } public void setResolvedAt(Instant value) { resolvedAt = value; }
  public Instant getClosedAt() { return closedAt; } public void setClosedAt(Instant value) { closedAt = value; }
  public long getVersion() { return version; }
}
