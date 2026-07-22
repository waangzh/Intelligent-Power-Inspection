package com.powerinspection.robot;

import java.time.Instant;

public record RobotLocationView(
    String robotId,
    boolean online,
    boolean locationAvailable,
    boolean realtime,
    String state,
    String executionId,
    BridgeGnssFix gnssFix
) {}
