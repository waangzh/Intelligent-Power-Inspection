package com.powerinspection.user;

public enum Permission {
  TASK_VIEW("task:view"),
  TASK_CREATE("task:create"),
  TASK_DISPATCH("task:dispatch"),
  TASK_CONTROL("task:control"),
  SITE_EDIT("site:edit"),
  ROUTE_EDIT("route:edit"),
  ALARM_ACK("alarm:ack"),
  ROBOT_MANAGE("robot:manage"),
  DETECTION_MANAGE("detection:manage"),
  USER_MANAGE("user:manage"),
  RECORD_EXPORT("record:export");

  private final String value;

  Permission(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
