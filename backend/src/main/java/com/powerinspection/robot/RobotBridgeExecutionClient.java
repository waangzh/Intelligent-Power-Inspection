package com.powerinspection.robot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Spring 服务端到内网 Bridge 管理 API 的唯一执行客户端。 */
@Component
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "bridge")
public class RobotBridgeExecutionClient {
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public RobotBridgeExecutionClient(RobotProperties properties, ObjectMapper objectMapper) {
    if (!StringUtils.hasText(properties.getBridgeAdminToken()) || !StringUtils.hasText(properties.getHeartbeatBridgeBaseUrl())) {
      throw new IllegalStateException("Bridge execution client requires server-side bridge configuration");
    }
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
      .withConnectTimeout(Duration.ofSeconds(Math.max(1, properties.getBridgeConnectTimeoutSeconds())))
      .withReadTimeout(Duration.ofSeconds(Math.max(1, properties.getBridgeReadTimeoutSeconds())));
    this.restClient = RestClient.builder()
      .baseUrl(trim(properties.getHeartbeatBridgeBaseUrl()))
      .requestFactory(ClientHttpRequestFactories.get(settings))
      .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getBridgeAdminToken())
      .build();
    this.objectMapper = objectMapper;
  }

  public RobotBridgeExecutionStartResult start(String executionId, Map<String, Object> request) {
    Map<String, Object> body = post("/bridge/v1/executions/{executionId}/start", executionId, request);
    return new RobotBridgeExecutionStartResult(bool(body.get("accepted")), text(body.get("commandId")), text(body.get("state")), text(body.get("executionId")));
  }

  public RobotBridgeExecutionSnapshot execution(String executionId) {
    Map<String, Object> body = get("/bridge/v1/executions/{executionId}", executionId);
    return new RobotBridgeExecutionSnapshot(text(body.get("executionId")), text(body.get("robotId")), text(body.get("deploymentId")),
      text(body.get("state")), number(body.get("lastEventSequence")), sanitize(text(body.get("lastError"))));
  }

  @SuppressWarnings("unchecked")
  public List<RobotBridgeExecutionEvent> events(String executionId, long afterSequence) {
    Map<String, Object> body = get("/bridge/v1/executions/{executionId}/events?afterSequence={afterSequence}&limit=100", executionId, Math.max(0, afterSequence));
    Object value = body.get("events");
    if (!(value instanceof Collection<?> items)) throw RobotBridgeExecutionException.unknown("INVALID_BRIDGE_PAYLOAD", "Bridge 事件响应格式无效");
    List<RobotBridgeExecutionEvent> result = new ArrayList<>();
    for (Object item : items) {
      if (!(item instanceof Map<?, ?> map)) throw RobotBridgeExecutionException.unknown("INVALID_BRIDGE_PAYLOAD", "Bridge 事件条目格式无效");
      Map<String, Object> normalized = new LinkedHashMap<>();
      map.forEach((key, nested) -> normalized.put(String.valueOf(key), nested));
      result.add(new RobotBridgeExecutionEvent(Map.copyOf(normalized)));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> get(String path, Object... variables) {
    try {
      Map<String, Object> body = restClient.get().uri(path, variables).retrieve().body(Map.class);
      if (body == null) throw RobotBridgeExecutionException.unknown("INVALID_BRIDGE_PAYLOAD", "Bridge 未返回执行数据");
      return body;
    } catch (RobotBridgeExecutionException ex) {
      throw ex;
    } catch (RestClientResponseException ex) {
      throw responseException(ex);
    } catch (RestClientException ex) {
      throw RobotBridgeExecutionException.unknown("BRIDGE_UNREACHABLE", "无法连接 Bridge，执行结果等待保守对账");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> post(String path, String executionId, Map<String, Object> request) {
    try {
      Map<String, Object> body = restClient.post().uri(path, executionId).body(request).retrieve().body(Map.class);
      if (body == null) throw RobotBridgeExecutionException.unknown("INVALID_BRIDGE_PAYLOAD", "Bridge 未返回启动回执");
      return body;
    } catch (RobotBridgeExecutionException ex) {
      throw ex;
    } catch (RestClientResponseException ex) {
      throw responseException(ex);
    } catch (RestClientException ex) {
      throw RobotBridgeExecutionException.unknown("BRIDGE_UNREACHABLE", "无法连接 Bridge，启动结果等待保守对账");
    }
  }

  private RobotBridgeExecutionException responseException(RestClientResponseException ex) {
    String fallback = ex.getStatusCode().is4xxClientError() ? "Bridge 明确拒绝执行请求" : "Bridge 服务暂不可用，执行结果等待保守对账";
    String code = errorCode(ex, "BRIDGE_HTTP_" + ex.getStatusCode().value());
    String message = errorMessage(ex, fallback);
    return ex.getStatusCode().is4xxClientError()
      ? RobotBridgeExecutionException.explicit(code, message)
      : RobotBridgeExecutionException.unknown(code, message);
  }

  private String errorCode(RestClientResponseException ex, String fallback) {
    try {
      Map<String, Object> body = objectMapper.readValue(ex.getResponseBodyAsString(), new TypeReference<Map<String, Object>>() {});
      String code = text(body.get("code"));
      return code.matches("[A-Z0-9_]{1,64}") ? code : fallback;
    } catch (Exception ignored) { return fallback; }
  }

  private String errorMessage(RestClientResponseException ex, String fallback) {
    try {
      Map<String, Object> body = objectMapper.readValue(ex.getResponseBodyAsString(), new TypeReference<Map<String, Object>>() {});
      String message = sanitize(text(body.get("message")));
      return message.isBlank() ? fallback : message;
    } catch (Exception ignored) { return fallback; }
  }

  private static String trim(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
  private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
  private static boolean bool(Object value) { return value instanceof Boolean flag && flag; }
  private static long number(Object value) {
    if (value instanceof Number number) return Math.max(0, number.longValue());
    try { return Math.max(0, Long.parseLong(text(value))); } catch (NumberFormatException ex) { return 0; }
  }
  private static String sanitize(String value) {
    String compact = value.replaceAll("[\\r\\n]+", " ").trim();
    compact = compact.replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[REDACTED]");
    compact = compact.replaceAll("(?i)(authorization|token|password)=[^\\s,;]+", "$1=[REDACTED]");
    return compact.substring(0, Math.min(500, compact.length()));
  }
}
