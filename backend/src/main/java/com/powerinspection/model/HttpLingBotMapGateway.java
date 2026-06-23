package com.powerinspection.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnProperty(prefix = "app.model", name = "mode", havingValue = "http")
public class HttpLingBotMapGateway implements LingBotMapGateway {
  private final RestClient restClient;

  public HttpLingBotMapGateway(ModelProperties properties) {
    this.restClient = HttpModelSupport.restClient(
      properties.getLingbotMap().getBaseUrl(),
      properties.getLingbotMap().getTimeoutSeconds(),
      properties.getServiceToken()
    );
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> createJob(Map<String, Object> payload) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("requestId", text(payload.get("id")));
    body.put("siteId", payload.get("siteId"));
    body.put("inputKind", payload.getOrDefault("inputKind", "video"));
    body.put("videoUrl", payload.get("videoUrl"));
    body.put("imageFolderUrl", payload.get("imageFolderUrl"));
    body.put("mode", payload.getOrDefault("mode", "windowed"));
    body.put("fps", payload.getOrDefault("fps", 10));
    body.put("stride", payload.getOrDefault("stride", 1));
    body.put("firstK", payload.get("firstK"));
    body.put("keyframeInterval", payload.getOrDefault("keyframeInterval", 5));
    body.put("windowSize", payload.getOrDefault("windowSize", 16));
    body.put("outputProfile", payload.getOrDefault("outputProfile", "preview"));
    body.put("maskSky", payload.getOrDefault("maskSky", false));

    try {
      Map<String, Object> response = restClient.post()
        .uri("/v1/reconstruction/jobs")
        .body(body)
        .retrieve()
        .body(Map.class);
      Map<String, Object> job = new LinkedHashMap<>(payload);
      mergeStatus(job, response);
      job.put("modelProvider", "lingbot-map");
      job.put("externalJobId", response == null ? null : response.get("jobId"));
      return job;
    } catch (RestClientException ex) {
      throw new ModelServiceException("LingBot-Map 模型服务创建任务失败", ex);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> advanceJob(Map<String, Object> current) {
    String externalJobId = text(current.get("externalJobId"));
    if (externalJobId == null || externalJobId.isBlank()) {
      return failed(current, "缺少外部建图任务 ID");
    }
    try {
      Map<String, Object> response = restClient.get()
        .uri("/v1/reconstruction/jobs/{jobId}", externalJobId)
        .retrieve()
        .body(Map.class);
      Map<String, Object> job = new LinkedHashMap<>(current);
      mergeStatus(job, response);
      return job;
    } catch (RestClientException ex) {
      throw new ModelServiceException("LingBot-Map 模型服务查询任务失败", ex);
    }
  }

  @Override
  public Map<String, Object> pointCloud(Map<String, Object> job) {
    Object rawArtifacts = job.get("artifacts");
    if (rawArtifacts instanceof Map<?, ?> artifacts && artifacts.get("pointCloudUrl") != null) {
      return Map.of("id", job.get("id"), "url", artifacts.get("pointCloudUrl"));
    }
    return Map.of("id", job.get("id"), "url", "/mock/pointcloud/" + job.get("id") + ".ply");
  }

  private void mergeStatus(Map<String, Object> job, Map<String, Object> response) {
    if (response == null) {
      job.put("status", "FAILED");
      job.put("errorMessage", "模型服务无响应");
      return;
    }
    String mappedStatus = mapStatus(text(response.get("status")));
    job.put("status", mappedStatus);
    job.put("progress", number(response.get("progress"), number(job.get("progress"), 0)));
    copyIfPresent(job, response, "message", "message");
    copyIfPresent(job, response, "frameCount", "frameCount");
    copyIfPresent(job, response, "pointCount", "pointCount");
    copyIfPresent(job, response, "mapId", "mapId");
    copyIfPresent(job, response, "artifacts", "artifacts");
    copyIfPresent(job, response, "warnings", "warnings");
    if ("COMPLETED".equals(mappedStatus)) {
      job.put("completedAt", Instant.now().toString());
    }
    if ("FAILED".equals(mappedStatus)) {
      copyIfPresent(job, response, "errorMessage", "errorMessage");
    }
  }

  private Map<String, Object> failed(Map<String, Object> current, String message) {
    Map<String, Object> job = new LinkedHashMap<>(current);
    job.put("status", "FAILED");
    job.put("errorMessage", message);
    return job;
  }

  private void copyIfPresent(Map<String, Object> target, Map<String, Object> source, String sourceKey, String targetKey) {
    if (source.containsKey(sourceKey)) {
      target.put(targetKey, source.get(sourceKey));
    }
  }

  private String mapStatus(String status) {
    return switch (status) {
      case "QUEUED" -> "PENDING";
      case "RUNNING" -> "PROCESSING";
      case "SUCCEEDED" -> "COMPLETED";
      case "CANCELLED" -> "CANCELLED";
      case "FAILED" -> "FAILED";
      default -> "FAILED";
    };
  }

  private int number(Object value, int fallback) {
    return value instanceof Number number ? number.intValue() : fallback;
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }
}
