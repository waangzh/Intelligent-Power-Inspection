package com.powerinspection.robot;

import java.time.Instant;

public record RobotHeartbeatStatusView(
  String robotId, String serialNo, String displayName, String connectionStatus, boolean online,
  Instant lastHeartbeatAt, Instant lastOnlineAt, Instant statusUpdatedAt, String offlineReason,
  Source source, String protocolVersion, String bootId, String softwareVersion, String robotState,
  long acceptedEventSequence, String diagnosticSummary
) {
  public record Source(String name, boolean bridgeConfigured) {}
}
