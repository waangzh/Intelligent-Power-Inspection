package com.powerinspection.robot;

import java.util.List;

public record RobotHeartbeatStatusPage(List<RobotHeartbeatStatusView> items, int page, int size, long total) {}
