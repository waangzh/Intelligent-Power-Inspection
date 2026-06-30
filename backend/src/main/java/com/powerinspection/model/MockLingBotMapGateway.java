package com.powerinspection.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.model", name = "mode", havingValue = "mock")
public class MockLingBotMapGateway implements LingBotMapGateway {
  @Override
  public Map<String, Object> createJob(Map<String, Object> payload) {
    Map<String, Object> job = new LinkedHashMap<>(payload);
    String id = text(job.get("id"));
    job.putIfAbsent("status", "PENDING");
    job.putIfAbsent("progress", 0);
    job.putIfAbsent("pointCount", 0);
    job.putIfAbsent("videoCount", 0);
    job.putIfAbsent("modelProvider", "mock");
    job.putIfAbsent("mapId", "map_" + id);
    job.putIfAbsent("artifacts", artifacts(id));
    return job;
  }

  @Override
  public Map<String, Object> advanceJob(Map<String, Object> current) {
    Map<String, Object> job = new LinkedHashMap<>(current);
    if ("COMPLETED".equals(job.get("status"))) {
      return job;
    }

    int progress = number(job.get("progress"));
    job.put("status", "PROCESSING");
    job.put("progress", Math.min(100, progress + 15));
    job.put("pointCount", number(job.get("pointCount")) + 120000);
    job.put("videoCount", number(job.get("videoCount")) + 2);
    job.putIfAbsent("modelProvider", "mock");
    job.putIfAbsent("mapId", "map_" + text(job.get("id")));
    job.putIfAbsent("artifacts", artifacts(text(job.get("id"))));
    if (number(job.get("progress")) >= 100) {
      job.put("status", "COMPLETED");
      job.put("progress", 100);
      job.put("completedAt", Instant.now().toString());
    }
    return job;
  }

  @Override
  public Map<String, Object> pointCloud(Map<String, Object> job) {
    String id = text(job.get("id"));
    Object rawArtifacts = job.get("artifacts");
    if (rawArtifacts instanceof Map<?, ?> artifacts && artifacts.get("pointCloudUrl") != null) {
      return Map.of("id", id, "url", artifacts.get("pointCloudUrl"));
    }
    return Map.of("id", id, "url", "/mock/pointcloud/" + id + ".ply");
  }

  private Map<String, Object> artifacts(String id) {
    return Map.of("pointCloudUrl", "/mock/pointcloud/" + id + ".ply");
  }

  private int number(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    return Integer.parseInt(value.toString());
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }
}
