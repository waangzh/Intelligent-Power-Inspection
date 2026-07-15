package com.powerinspection.robot;

public record RobotBridgeExecutionStartResult(boolean accepted, String commandId, String state, String executionId) {}
