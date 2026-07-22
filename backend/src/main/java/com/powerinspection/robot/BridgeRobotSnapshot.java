package com.powerinspection.robot;

import java.time.Instant;
import java.util.Map;

/** Robot Bridge 管理 API 返回的、已脱离原始 HTTP 载荷的心跳快照。 */
public record BridgeRobotSnapshot(
  String robotId,
  Instant lastHeartbeatAt,
  String protocolVersion,
  String bootId,
  String state,
  String executionId,
  String softwareVersion,
  long acceptedEventSequence,
  Map<String, Object> health,
  BridgePatrolSnapshot patrol,
  BridgeGnssFix gnssFix,
  boolean reportedSupportsRemoteImmediateStart,
  boolean reportedSupportsLocalConfirmStart,
  String localConfirmProtocolVersion,
  boolean localConfirmStartReady,
  String localConfirmStartError,
  Instant capabilityReportedAt
) {
  public BridgeRobotSnapshot(
      String robotId, Instant lastHeartbeatAt, String protocolVersion, String bootId,
      String state, String softwareVersion, long acceptedEventSequence,
      Map<String, Object> health, BridgeGnssFix gnssFix) {
    this(robotId, lastHeartbeatAt, protocolVersion, bootId, state, null, softwareVersion,
      acceptedEventSequence, health, null, gnssFix, true, false, null, false, null, null);
  }
}
