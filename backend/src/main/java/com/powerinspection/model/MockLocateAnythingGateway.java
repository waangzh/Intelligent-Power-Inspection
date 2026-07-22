package com.powerinspection.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.model", name = "mode", havingValue = "mock")
public class MockLocateAnythingGateway implements LocateAnythingGateway {
  private final Random random = new Random();

  @Override
  public LocateAnythingResult detectCheckpoint(LocateAnythingRequest request) {
    if (random.nextDouble() > 0.55 || request.detections() == null) {
      return new LocateAnythingResult(List.of(), List.of(), null);
    }
    List<Map<String, Object>> enabled = DetectionItems.enabled(request.detections());
    if (enabled.isEmpty()) {
      return new LocateAnythingResult(List.of(), List.of(), null);
    }

    Map<String, Object> detection = enabled.get(random.nextInt(enabled.size()));
    String type = text(detection.get("type"));
    String prompt = text(detection.get("prompt"));
    String checkpointId = text(request.checkpoint().get("id"));
    String imageUrl = "https://picsum.photos/seed/" + checkpointId + "_" + System.currentTimeMillis() + "/400/240";
    Map<String, Object> rawResult = new LinkedHashMap<>();
    rawResult.put("provider", "mock");
    rawResult.put("model", "LocateAnything");
    rawResult.put("inputImageUrl", request.imageUrl());

    return new LocateAnythingResult(List.of(new LocateAnythingFinding(
      text(detection.get("itemId")),
      type,
      prompt,
      0.75 + random.nextDouble() * 0.2,
      List.of(80, 60, 320, 220),
      text(detection.get("displayLabel")),
      imageUrl,
      rawResult
    )), List.of(), imageUrl);
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }
}
