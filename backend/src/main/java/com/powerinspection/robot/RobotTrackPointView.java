package com.powerinspection.robot;

import java.time.Instant;

public record RobotTrackPointView(
    double latitude,
    double longitude,
    Double altitude,
    String fixType,
    Integer satellites,
    Double hdop,
    String robotState,
    String navigationPhase,
    String targetId,
    Integer cycleIndex,
    Instant observedAt
) {}
