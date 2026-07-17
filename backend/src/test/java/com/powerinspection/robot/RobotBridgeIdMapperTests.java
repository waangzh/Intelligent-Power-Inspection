package com.powerinspection.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RobotBridgeIdMapperTests {
  @Test
  void convertsOnlyAtBridgeBoundaryAndAdaptsDeploymentView() {
    RobotBridgeIdMapper mapper = new RobotBridgeIdMapper(properties());

    assertEquals("robot-001", mapper.toBridgeId("robot_001"));
    assertEquals("robot_001", mapper.toPlatformId("robot-001"));
    assertEquals("other", mapper.toBridgeId("other"));
    assertEquals("robot-001", mapper.toBridgeDeploymentView(Map.of("robotId", "robot_001")).get("robotId"));
  }

  @Test
  void recognizesOnlyConfiguredBridgePlatformBearerToken() {
    RobotBridgeIdMapper mapper = new RobotBridgeIdMapper(properties());

    assertTrue(mapper.isBridgePlatformRequest("Bearer platform-token"));
    assertFalse(mapper.isBridgePlatformRequest("Bearer another-token"));
  }

  private RobotProperties properties() {
    RobotProperties properties = new RobotProperties();
    properties.setBridgeRobotIdMappings(Map.of("robot_001", "robot-001"));
    properties.setBridgePlatformToken("platform-token");
    return properties;
  }
}
