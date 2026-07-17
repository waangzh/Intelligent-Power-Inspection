package com.powerinspection.workorder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "work_order_transitions")
public class WorkOrderTransitionEntity {
  @Id
  private String id;
  @Column(name = "work_order_id", nullable = false)
  private String workOrderId;
  @Column(name = "from_status")
  private String fromStatus;
  @Column(name = "to_status", nullable = false)
  private String toStatus;
  @Column(nullable = false)
  private String source;
  @Column(name = "actor_id")
  private String actorId;
  @Column(columnDefinition = "LONGTEXT")
  private String remark;
  @Column(name = "created_at", nullable = false)
  private String createdAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getWorkOrderId() { return workOrderId; }
  public void setWorkOrderId(String workOrderId) { this.workOrderId = workOrderId; }
  public String getFromStatus() { return fromStatus; }
  public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
  public String getToStatus() { return toStatus; }
  public void setToStatus(String toStatus) { this.toStatus = toStatus; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
  public String getActorId() { return actorId; }
  public void setActorId(String actorId) { this.actorId = actorId; }
  public String getRemark() { return remark; }
  public void setRemark(String remark) { this.remark = remark; }
  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
