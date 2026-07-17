package com.powerinspection.robot;

class BridgeRobotClientException extends RuntimeException {
  enum Reason { AUTH_FAILED, UNREACHABLE, INVALID_PAYLOAD }
  private final Reason reason;
  BridgeRobotClientException(Reason reason) { this.reason = reason; }
  Reason getReason() { return reason; }
}
