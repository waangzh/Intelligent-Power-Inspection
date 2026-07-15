package com.powerinspection.task;

public enum TaskExecutionControlCommandStatus {
  PENDING_SEND,
  SENDING,
  QUEUED,
  ACKED,
  RECONCILING,
  CONFIRMED,
  FAILED;
}
