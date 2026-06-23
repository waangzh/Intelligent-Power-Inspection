package com.powerinspection.robot;

import java.util.Map;

public record RobotProgressSnapshot(
  int progress,
  int currentCheckpointSeq,
  Map<String, Object> position
) {
}
