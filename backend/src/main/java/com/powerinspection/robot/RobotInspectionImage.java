package com.powerinspection.robot;

import java.util.Map;

public record RobotInspectionImage(String url, Integer width, Integer height) {
  public static RobotInspectionImage fromRobot(Map<String, Object> robot) {
    if (!(robot.get("telemetry") instanceof Map<?, ?> telemetry)) {
      return null;
    }
    if (telemetry.get("inspectionImage") instanceof Map<?, ?> image) {
      return create(image.get("url"), image.get("width"), image.get("height"));
    }
    return create(telemetry.get("inspectionImageUrl"), telemetry.get("inspectionImageWidth"), telemetry.get("inspectionImageHeight"));
  }

  private static RobotInspectionImage create(Object url, Object width, Object height) {
    String value = url == null ? null : url.toString().trim();
    if (value == null || value.isEmpty()) {
      return null;
    }
    return new RobotInspectionImage(value, integer(width), integer(height));
  }

  private static Integer integer(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return value == null ? null : Integer.valueOf(value.toString());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
