package com.powerinspection.task;

public enum TaskExecutionControlAction {
  PAUSE,
  RESUME,
  TAKEOVER,
  CANCEL;

  public String bridgePath() {
    return name().toLowerCase();
  }
}
