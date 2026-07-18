package com.powerinspection.task;

public enum TaskExecutionControlAction {
  PAUSE,
  RESUME,
  TAKEOVER,
  CANCEL,
  ESTOP;

  public String bridgePath() {
    return this == ESTOP ? "emergency-stop" : name().toLowerCase();
  }
}
