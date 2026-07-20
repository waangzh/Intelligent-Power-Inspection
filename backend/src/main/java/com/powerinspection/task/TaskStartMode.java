package com.powerinspection.task;

public enum TaskStartMode {
  REMOTE_IMMEDIATE,
  LOCAL_CONFIRM;

  public static TaskStartMode defaulted(TaskStartMode value) {
    return value == null ? REMOTE_IMMEDIATE : value;
  }
}
