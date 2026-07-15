package com.powerinspection.robot;

public record RobotBridgeExecutionSnapshot(
  String executionId, String robotId, String deploymentId, String state, long lastEventSequence, String lastError
) {}
