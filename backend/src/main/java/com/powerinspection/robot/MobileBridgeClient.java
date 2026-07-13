package com.powerinspection.robot;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "http")
public class MobileBridgeClient {
  private final RestClient restClient;
  private final RobotProperties properties;

  public MobileBridgeClient(RobotProperties properties) {
    this.properties = properties;
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
      .withConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
      .withReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
    RestClient.Builder builder = RestClient.builder()
      .baseUrl(trimTrailingSlash(properties.getBridgeBaseUrl()))
      .requestFactory(ClientHttpRequestFactories.get(settings))
      .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    if (StringUtils.hasText(properties.getToken())) {
      builder.defaultHeader("Authorization", "Bearer " + properties.getToken());
      builder.defaultHeader("X-API-Token", properties.getToken());
    }
    this.restClient = builder.build();
  }

  public Optional<Map<String, Object>> fetchStatus() {
    return fetchData("/api/status");
  }

  public Optional<Map<String, Object>> fetchPatrolStatus() {
    return fetchData("/api/debug/patrol/status");
  }

  public boolean sendPatrolCommand(String command) {
    return postOk("/api/debug/patrol/" + command);
  }

  public boolean emergencyStop() {
    return postOk("/api/stop");
  }

  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> fetchData(String path) {
    try {
      Map<String, Object> response = restClient.get()
        .uri(path)
        .retrieve()
        .body(Map.class);
      if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
        return Optional.empty();
      }
      Object data = response.get("data");
      if (data instanceof Map<?, ?> map) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        return Optional.of(normalized);
      }
      return Optional.empty();
    } catch (RestClientException ex) {
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  private boolean postOk(String path) {
    try {
      Map<String, Object> response = restClient.post()
        .uri(path)
        .retrieve()
        .body(Map.class);
      return response != null && Boolean.TRUE.equals(response.get("ok"));
    } catch (RestClientException ex) {
      return false;
    }
  }

  private static String trimTrailingSlash(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("app.robot.bridge-base-url 未配置");
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
