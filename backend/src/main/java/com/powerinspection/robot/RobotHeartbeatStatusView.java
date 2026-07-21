package com.powerinspection.robot;

import java.time.Instant;

public record RobotHeartbeatStatusView(
  String robotId, String serialNo, String displayName, String connectionStatus, boolean online,
  Instant lastHeartbeatAt, Instant lastOnlineAt, Instant statusUpdatedAt, String offlineReason,
  Source source, String protocolVersion, String bootId, String softwareVersion, String robotState,
  long acceptedEventSequence, String diagnosticSummary,
  boolean reportedSupportsRemoteImmediateStart, boolean reportedSupportsLocalConfirmStart,
  String localConfirmProtocolVersion, boolean localConfirmProtocolCompatible,
  boolean localConfirmStartReady, String localConfirmStartError, Instant capabilityReportedAt
) {
  public RobotHeartbeatStatusView(
      String robotId, String serialNo, String displayName, String connectionStatus, boolean online,
      Instant lastHeartbeatAt, Instant lastOnlineAt, Instant statusUpdatedAt, String offlineReason,
      Source source, String protocolVersion, String bootId, String softwareVersion, String robotState,
      long acceptedEventSequence, String diagnosticSummary) {
    this(robotId, serialNo, displayName, connectionStatus, online, lastHeartbeatAt, lastOnlineAt,
      statusUpdatedAt, offlineReason, source, protocolVersion, bootId, softwareVersion, robotState,
      acceptedEventSequence, diagnosticSummary, true, false, null, false, false, null, null);
  }

  public record Source(String name, boolean bridgeConfigured) {}
}
