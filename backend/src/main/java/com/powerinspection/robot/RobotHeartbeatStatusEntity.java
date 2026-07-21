package com.powerinspection.robot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "robot_heartbeat_status")
public class RobotHeartbeatStatusEntity {
  @Id @Column(name = "robot_id", nullable = false, length = 128) private String robotId;
  @Column(name = "connection_status", nullable = false, length = 32) private String connectionStatus;
  @Column(name = "offline_reason", length = 64) private String offlineReason;
  @Column(name = "last_heartbeat_at") private Instant lastHeartbeatAt;
  @Column(name = "last_online_at") private Instant lastOnlineAt;
  @Column(name = "status_updated_at", nullable = false) private Instant statusUpdatedAt;
  @Column(name = "source_name", nullable = false, length = 64) private String sourceName;
  @Column(name = "bridge_configured") private Boolean bridgeConfigured;
  @Column(name = "protocol_version", length = 32) private String protocolVersion;
  @Column(name = "boot_id", length = 128) private String bootId;
  @Column(name = "software_version", length = 128) private String softwareVersion;
  @Column(name = "robot_state", length = 64) private String robotState;
  @Column(name = "accepted_event_sequence", nullable = false) private long acceptedEventSequence;
  @Column(name = "diagnostic_summary", length = 1000) private String diagnosticSummary;
  @Column(name = "reported_remote_immediate_start", nullable = false) private boolean reportedRemoteImmediateStart = true;
  @Column(name = "reported_local_confirm_start", nullable = false) private boolean reportedLocalConfirmStart;
  @Column(name = "local_confirm_protocol_version", length = 32) private String localConfirmProtocolVersion;
  @Column(name = "local_confirm_start_ready", nullable = false) private boolean localConfirmStartReady;
  @Column(name = "local_confirm_start_error", length = 128) private String localConfirmStartError;
  @Column(name = "capability_reported_at") private Instant capabilityReportedAt;

  public String getRobotId() { return robotId; }
  public void setRobotId(String robotId) { this.robotId = robotId; }
  public String getConnectionStatus() { return connectionStatus; }
  public void setConnectionStatus(String connectionStatus) { this.connectionStatus = connectionStatus; }
  public String getOfflineReason() { return offlineReason; }
  public void setOfflineReason(String offlineReason) { this.offlineReason = offlineReason; }
  public Instant getLastHeartbeatAt() { return lastHeartbeatAt; }
  public void setLastHeartbeatAt(Instant lastHeartbeatAt) { this.lastHeartbeatAt = lastHeartbeatAt; }
  public Instant getLastOnlineAt() { return lastOnlineAt; }
  public void setLastOnlineAt(Instant lastOnlineAt) { this.lastOnlineAt = lastOnlineAt; }
  public Instant getStatusUpdatedAt() { return statusUpdatedAt; }
  public void setStatusUpdatedAt(Instant statusUpdatedAt) { this.statusUpdatedAt = statusUpdatedAt; }
  public String getSourceName() { return sourceName; }
  public void setSourceName(String sourceName) { this.sourceName = sourceName; }
  public Boolean getBridgeConfigured() { return bridgeConfigured; }
  public void setBridgeConfigured(Boolean bridgeConfigured) { this.bridgeConfigured = bridgeConfigured; }
  public String getProtocolVersion() { return protocolVersion; }
  public void setProtocolVersion(String protocolVersion) { this.protocolVersion = protocolVersion; }
  public String getBootId() { return bootId; }
  public void setBootId(String bootId) { this.bootId = bootId; }
  public String getSoftwareVersion() { return softwareVersion; }
  public void setSoftwareVersion(String softwareVersion) { this.softwareVersion = softwareVersion; }
  public String getRobotState() { return robotState; }
  public void setRobotState(String robotState) { this.robotState = robotState; }
  public long getAcceptedEventSequence() { return acceptedEventSequence; }
  public void setAcceptedEventSequence(long acceptedEventSequence) { this.acceptedEventSequence = acceptedEventSequence; }
  public String getDiagnosticSummary() { return diagnosticSummary; }
  public void setDiagnosticSummary(String diagnosticSummary) { this.diagnosticSummary = diagnosticSummary; }
  public boolean isReportedRemoteImmediateStart() { return reportedRemoteImmediateStart; }
  public void setReportedRemoteImmediateStart(boolean value) { reportedRemoteImmediateStart = value; }
  public boolean isReportedLocalConfirmStart() { return reportedLocalConfirmStart; }
  public void setReportedLocalConfirmStart(boolean value) { reportedLocalConfirmStart = value; }
  public String getLocalConfirmProtocolVersion() { return localConfirmProtocolVersion; }
  public void setLocalConfirmProtocolVersion(String value) { localConfirmProtocolVersion = value; }
  public boolean isLocalConfirmStartReady() { return localConfirmStartReady; }
  public void setLocalConfirmStartReady(boolean value) { localConfirmStartReady = value; }
  public String getLocalConfirmStartError() { return localConfirmStartError; }
  public void setLocalConfirmStartError(String value) { localConfirmStartError = value; }
  public Instant getCapabilityReportedAt() { return capabilityReportedAt; }
  public void setCapabilityReportedAt(Instant value) { capabilityReportedAt = value; }
}
