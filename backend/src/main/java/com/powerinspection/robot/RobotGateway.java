package com.powerinspection.robot;

import java.util.Map;

public interface RobotGateway {
  void dispatchTask(Map<String, Object> robot, Map<String, Object> task, Map<String, Object> route);

  void pauseTask(Map<String, Object> robot, Map<String, Object> task);

  void resumeTask(Map<String, Object> robot, Map<String, Object> task);

  void takeoverTask(Map<String, Object> robot, Map<String, Object> task);

  void cancelTask(Map<String, Object> robot, Map<String, Object> task);

  /** High-risk device stop; must not be implemented as a soft task cancel. */
  void emergencyStopTask(Map<String, Object> robot, Map<String, Object> task);

  RobotProgressSnapshot advanceTask(Map<String, Object> robot, Map<String, Object> task, Map<String, Object> route);
}
