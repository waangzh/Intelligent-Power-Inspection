package com.powerinspection.robot;

import java.time.Instant;
import java.util.List;

public record RobotTrackView(
    String robotId,
    String executionId,
    Instant start,
    Instant end,
    List<RobotTrackPointView> points
) {}
