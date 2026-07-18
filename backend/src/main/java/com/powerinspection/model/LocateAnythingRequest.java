package com.powerinspection.model;

import java.util.List;
import java.util.Map;

public record LocateAnythingRequest(
  Map<String, Object> task,
  Map<String, Object> route,
  Map<String, Object> checkpoint,
  String imageUrl,
  Integer imageWidth,
  Integer imageHeight,
  List<Map<String, Object>> detections
) {
}
