package com.powerinspection.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@ConditionalOnProperty(prefix = "app.model", name = "mode", havingValue = "http")
public class HttpLocateAnythingGateway implements LocateAnythingGateway {
  private final RestClient restClient;
  private final String generationMode;

  public HttpLocateAnythingGateway(ModelProperties properties) {
    this.restClient = HttpModelSupport.restClient(
      properties.getLocateAnything().getBaseUrl(),
      properties.getLocateAnything().getTimeoutSeconds(),
      properties.getServiceToken()
    );
    this.generationMode = properties.getLocateAnything().getGenerationMode();
  }

  @Override
  @SuppressWarnings("unchecked")
  public LocateAnythingResult detectCheckpoint(LocateAnythingRequest request) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("requestId", text(request.task().get("id")) + "_" + text(request.checkpoint().get("id")));
    payload.put("imageUrl", request.imageUrl());
    payload.put("imageWidth", request.imageWidth());
    payload.put("imageHeight", request.imageHeight());
    payload.put("detections", request.detections());
    payload.put("generationMode", generationMode);

    try {
      Map<String, Object> response = restClient.post()
        .uri("/v1/locate/checkpoint")
        .body(payload)
        .retrieve()
        .body(Map.class);
      return result(response);
    } catch (RestClientException ex) {
      throw new ModelServiceException("LocateAnything 模型服务调用失败", ex);
    }
  }

  private LocateAnythingResult result(Map<String, Object> response) {
    if (response == null) {
      return new LocateAnythingResult(List.of(), List.of(), null);
    }
    if (!"SUCCEEDED".equals(text(response.get("status")))) {
      throw new ModelServiceException("LocateAnything 模型服务返回失败状态: " + text(response.get("status")));
    }
    Object rawFindings = response.get("findings");
    String resultImageUrl = text(response.get("resultImageUrl"));
    if (!(rawFindings instanceof List<?> list)) {
      return new LocateAnythingResult(List.of(), strings(response.get("warnings")), resultImageUrl);
    }
    List<LocateAnythingFinding> items = new ArrayList<>();
    for (Object raw : list) {
      if (raw instanceof Map<?, ?> map) {
        Map<String, Object> finding = normalize(map);
        items.add(new LocateAnythingFinding(
          text(finding.get("itemId")),
          text(finding.get("type")),
          text(finding.get("prompt")),
          number(finding.get("score"), 0),
          intList(firstNonNull(finding.get("pixelBox"), firstNonNull(finding.get("bbox"), finding.get("normalizedBox")))),
          text(finding.getOrDefault("label", "abnormal")),
          firstText(text(finding.get("imageUrl")), resultImageUrl),
          finding
        ));
      }
    }
    return new LocateAnythingResult(items, strings(response.get("warnings")), resultImageUrl);
  }

  private String firstText(String preferred, String fallback) {
    return preferred == null || preferred.isBlank() ? fallback : preferred;
  }

  private List<String> strings(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().filter(item -> item != null).map(Object::toString).toList();
  }

  private Map<String, Object> normalize(Map<?, ?> raw) {
    Map<String, Object> item = new LinkedHashMap<>();
    raw.forEach((key, value) -> item.put(String.valueOf(key), value));
    return item;
  }

  private Object firstNonNull(Object left, Object right) {
    return left != null ? left : right;
  }

  private List<Integer> intList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::intValue).toList();
  }

  private double number(Object value, double fallback) {
    return value instanceof Number number ? number.doubleValue() : fallback;
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }
}
