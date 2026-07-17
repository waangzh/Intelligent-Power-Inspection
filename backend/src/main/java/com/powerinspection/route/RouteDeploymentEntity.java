package com.powerinspection.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "route_deployments")
public class RouteDeploymentEntity {
  @Id
  private String id;
  @Column(name = "route_revision_id", nullable = false)
  private String routeRevisionId;
  @Column(name = "robot_id", nullable = false)
  private String robotId;
  @Column(name = "request_id", nullable = false, unique = true)
  private String requestId;
  @Column(nullable = false)
  private String state;
  @Column(name = "attempt_no", nullable = false)
  private int attemptNo;
  @Column(name = "last_attempt_at")
  private String lastAttemptAt;
  @Column(name = "next_reconcile_at")
  private String nextReconcileAt;
  @Column(name = "remote_summary_json", columnDefinition = "LONGTEXT")
  private String remoteSummaryJson;
  @Column(name = "error_code")
  private String errorCode;
  @Column(name = "error_message")
  private String errorMessage;
  @Column(name = "created_at", nullable = false)
  private String createdAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private long version;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getRouteRevisionId() { return routeRevisionId; }
  public void setRouteRevisionId(String routeRevisionId) { this.routeRevisionId = routeRevisionId; }
  public String getRobotId() { return robotId; }
  public void setRobotId(String robotId) { this.robotId = robotId; }
  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }
  public String getState() { return state; }
  public void setState(String state) { this.state = state; }
  public int getAttemptNo() { return attemptNo; }
  public void setAttemptNo(int attemptNo) { this.attemptNo = attemptNo; }
  public String getLastAttemptAt() { return lastAttemptAt; }
  public void setLastAttemptAt(String lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
  public String getNextReconcileAt() { return nextReconcileAt; }
  public void setNextReconcileAt(String nextReconcileAt) { this.nextReconcileAt = nextReconcileAt; }
  public String getRemoteSummaryJson() { return remoteSummaryJson; }
  public void setRemoteSummaryJson(String remoteSummaryJson) { this.remoteSummaryJson = remoteSummaryJson; }
  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
  public String getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
  public long getVersion() { return version; }
}
