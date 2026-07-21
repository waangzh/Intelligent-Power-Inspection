package com.powerinspection.robot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "robot_local_confirm_policy_audit")
public class RobotLocalConfirmPolicyAuditEntity {
  @Id private String id;
  @Column(name = "robot_id", nullable = false, length = 64) private String robotId;
  @Column(name = "operator_id", nullable = false, length = 64) private String operatorId;
  @Column(name = "operator_name", nullable = false, length = 120) private String operatorName;
  @Column(name = "previous_enabled", nullable = false) private boolean previousEnabled;
  @Column(name = "new_enabled", nullable = false) private boolean newEnabled;
  @Column(name = "changed_at", nullable = false) private Instant changedAt;

  public String getId() { return id; }
  public void setId(String value) { id = value; }
  public String getRobotId() { return robotId; }
  public void setRobotId(String value) { robotId = value; }
  public String getOperatorId() { return operatorId; }
  public void setOperatorId(String value) { operatorId = value; }
  public String getOperatorName() { return operatorName; }
  public void setOperatorName(String value) { operatorName = value; }
  public boolean isPreviousEnabled() { return previousEnabled; }
  public void setPreviousEnabled(boolean value) { previousEnabled = value; }
  public boolean isNewEnabled() { return newEnabled; }
  public void setNewEnabled(boolean value) { newEnabled = value; }
  public Instant getChangedAt() { return changedAt; }
  public void setChangedAt(Instant value) { changedAt = value; }
}
