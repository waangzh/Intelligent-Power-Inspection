package com.powerinspection.robot;

import java.time.Instant;
import java.util.List;

public record RobotTrackView(
    String robotId,
    Instant start,
    Instant end,
    List<RobotTrackPointView> points
) {}
