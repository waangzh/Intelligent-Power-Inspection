package com.powerinspection.robot;

/** 由平台服务端计算的连接状态；不接受设备提交的 online 字段。 */
public enum RobotConnectionStatus {
  CONNECTED,
  OFFLINE,
  UNKNOWN,
  BRIDGE_UNREACHABLE,
  BRIDGE_UNCONFIGURED
}
