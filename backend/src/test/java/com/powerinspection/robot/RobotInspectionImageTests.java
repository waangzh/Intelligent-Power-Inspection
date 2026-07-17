package com.powerinspection.robot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RobotInspectionImageTests {
  @Test
  void readsCanonicalInspectionImageFromRobotTelemetry() {
    RobotInspectionImage image = RobotInspectionImage.fromRobot(Map.of(
      "telemetry", Map.of(
        "inspectionImage", Map.of(
          "url", "http://robot-bridge/images/latest.jpg",
          "width", 1280,
          "height", 720
        )
      )
    ));

    assertThat(image).isEqualTo(new RobotInspectionImage(
      "http://robot-bridge/images/latest.jpg",
      1280,
      720
    ));
  }

  @Test
  void returnsNullWhenRobotHasNoRealInspectionImage() {
    assertThat(RobotInspectionImage.fromRobot(Map.of("telemetry", Map.of("online", true))))
      .isNull();
  }
}
