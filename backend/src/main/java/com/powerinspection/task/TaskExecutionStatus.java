package com.powerinspection.task;

import java.util.Set;

public enum TaskExecutionStatus {
  CREATED,
  STARTING,
  RUNNING,
  PAUSING,
  PAUSED,
  RESUMING,
  CANCELLING,
  CANCELLED,
  TAKEOVER_PENDING,
  MANUAL_TAKEOVER,
  COMPLETED,
  START_FAILED,
  FAILED,
  DISCONNECTED,
  RECOVERING;

  public static final Set<String> TERMINAL = Set.of(COMPLETED.name(), START_FAILED.name(), FAILED.name(), CANCELLED.name());
  public static final Set<String> ACTIVE = Set.of(STARTING.name(), RUNNING.name(), PAUSING.name(), PAUSED.name(), RESUMING.name(),
    CANCELLING.name(), TAKEOVER_PENDING.name(), MANUAL_TAKEOVER.name(), DISCONNECTED.name(), RECOVERING.name());
  public static final Set<String> NON_TERMINAL = Set.of(CREATED.name(), STARTING.name(), RUNNING.name(), PAUSING.name(), PAUSED.name(),
    RESUMING.name(), CANCELLING.name(), TAKEOVER_PENDING.name(), MANUAL_TAKEOVER.name(), DISCONNECTED.name(), RECOVERING.name());
}
