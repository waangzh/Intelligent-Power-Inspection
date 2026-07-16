package com.powerinspection.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RobotBridgeExecutionClientTests {
  @Test
  void convertsBridgeEventRobotIdBackToThePlatformId() {
    RobotProperties properties = new RobotProperties();
    properties.setHeartbeatBridgeBaseUrl("http://127.0.0.1:8001");
    properties.setBridgeAdminToken("admin-token");
    properties.setBridgeRobotIdMappings(Map.of("robot_001", "robot-001"));
    RobotBridgeExecutionClient client = new RobotBridgeExecutionClient(properties, new ObjectMapper(), new RobotBridgeIdMapper(properties));

    RobotBridgeExecutionEvent event = client.normalizeEvent(Map.of("robot_id", "robot-001", "sequence", 1));

    assertEquals("robot_001", event.text("robot_id"));
  }
}
