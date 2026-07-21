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
  String softwareVersion,
  long acceptedEventSequence,
  Map<String, Object> health,
  BridgeGnssFix gnssFix
) {}
