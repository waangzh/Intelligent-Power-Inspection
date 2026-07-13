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

  public MobileBridgeClient(RobotProperties properties) {
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
      .withConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
      .withReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()));
    RestClient.Builder builder = RestClient.builder().baseUrl(trim(properties.getBridgeBaseUrl()))
      .requestFactory(ClientHttpRequestFactories.get(settings))
      .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json");
    if (StringUtils.hasText(properties.getToken())) {
      builder.defaultHeader("Authorization", "Bearer " + properties.getToken());
      builder.defaultHeader("X-API-Token", properties.getToken());
    }
    restClient = builder.build();
  }

  public Optional<Map<String, Object>> fetchStatus() { return get("/api/status"); }
  public Optional<Map<String, Object>> fetchPatrolStatus() { return get("/api/debug/patrol/status"); }
  public boolean sendPatrolCommand(String command) { return post("/api/debug/patrol/" + command); }
  public boolean emergencyStop() { return post("/api/stop"); }

  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> get(String path) {
    try {
      Map<String, Object> response = restClient.get().uri(path).retrieve().body(Map.class);
      if (response == null || !Boolean.TRUE.equals(response.get("ok")) || !(response.get("data") instanceof Map<?, ?> data)) return Optional.empty();
      Map<String, Object> result = new LinkedHashMap<>();
      data.forEach((key, value) -> result.put(String.valueOf(key), value));
      return Optional.of(result);
    } catch (RestClientException ex) { return Optional.empty(); }
  }

  @SuppressWarnings("unchecked")
  private boolean post(String path) {
    try {
      Map<String, Object> response = restClient.post().uri(path).retrieve().body(Map.class);
      return response != null && Boolean.TRUE.equals(response.get("ok"));
    } catch (RestClientException ex) { return false; }
  }

  private String trim(String baseUrl) {
    if (!StringUtils.hasText(baseUrl)) throw new IllegalStateException("app.robot.bridge-base-url 未配置");
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
