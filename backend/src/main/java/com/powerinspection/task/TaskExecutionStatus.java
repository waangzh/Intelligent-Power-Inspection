package com.powerinspection.task;

import java.util.Set;

public enum TaskExecutionStatus {
  CREATED,
  STARTING,
  RUNNING,
  COMPLETED,
  START_FAILED,
  FAILED,
  DISCONNECTED,
  RECOVERING;

  public static final Set<String> TERMINAL = Set.of(COMPLETED.name(), START_FAILED.name(), FAILED.name());
  public static final Set<String> NON_TERMINAL = Set.of(CREATED.name(), STARTING.name(), RUNNING.name(), DISCONNECTED.name(), RECOVERING.name());
}
