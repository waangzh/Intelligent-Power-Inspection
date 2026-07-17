package com.powerinspection.user;

public enum Permission {
  TASK_VIEW("task:view"),
  TASK_CREATE("task:create"),
  TASK_DISPATCH("task:dispatch"),
  TASK_CONTROL("task:control"),
  TASK_TAKEOVER("task:takeover"),
  TASK_ESTOP("task:estop"),
  SITE_EDIT("site:edit"),
  ROUTE_EDIT("route:edit"),
  ALARM_ACK("alarm:ack"),
  ROBOT_MANAGE("robot:manage"),
  DETECTION_MANAGE("detection:manage"),
  USER_MANAGE("user:manage"),
  RECORD_EXPORT("record:export"),
  WORKORDER_VIEW("workorder:view"),
  WORKORDER_CREATE("workorder:create"),
  WORKORDER_PROCESS("workorder:process"),
  WORKORDER_REVIEW("workorder:review"),
  ALARM_POLICY("alarm:policy"),
  AGENT_VIEW("agent:view"),
  AGENT_RUN("agent:run"),
  AGENT_APPROVE("agent:approve"),
  AGENT_ADMIN("agent:admin");

  private final String value;

  Permission(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
