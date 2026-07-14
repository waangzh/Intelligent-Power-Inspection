package com.powerinspection.robot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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

/** 仅由 Spring 服务端访问回环 Bridge 管理 API。 */
@Component
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "bridge")
public class RobotBridgeDeploymentClient {
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public RobotBridgeDeploymentClient(RobotProperties properties, ObjectMapper objectMapper) {
    if (!StringUtils.hasText(properties.getBridgeAdminToken())) {
      throw new IllegalStateException("app.robot.bridge-admin-token is required in bridge mode");
    }
    if (!StringUtils.hasText(properties.getHeartbeatBridgeBaseUrl())) {
      throw new IllegalStateException("app.robot.heartbeat-bridge-base-url is required in bridge mode");
    }
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
      .withConnectTimeout(Duration.ofSeconds(Math.max(1, properties.getBridgeConnectTimeoutSeconds())))
      .withReadTimeout(Duration.ofSeconds(Math.max(1, properties.getBridgeReadTimeoutSeconds())));
    this.restClient = RestClient.builder()
      .baseUrl(trimTrailingSlash(properties.getHeartbeatBridgeBaseUrl()))
      .requestFactory(ClientHttpRequestFactories.get(settings))
      .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getBridgeAdminToken())
      .build();
    this.objectMapper = objectMapper;
  }

  @SuppressWarnings("unchecked")
  public RobotBridgeDeploymentResult sync(String deploymentId) {
    try {
      Map<String, Object> body = restClient.post()
        .uri("/bridge/v1/deployments/{deploymentId}/sync", deploymentId)
        .retrieve()
        .body(Map.class);
      if (body == null) throw RobotBridgeDeploymentException.failed("INVALID_BRIDGE_PAYLOAD", "Bridge 未返回部署结果");
      return new RobotBridgeDeploymentResult(
        text(body.get("deploymentId")), text(body.get("state")), text(body.get("schemaVersion")), text(body.get("robotId")),
        text(body.get("routeRevisionId")), text(body.get("routeRevisionContentSha256")), text(body.get("routePayloadSha256")),
        text(body.get("routeContentSha256")), text(body.get("mapAssetId")), text(body.get("mapImageSha256")),
        text(body.get("yamlName")), text(body.get("pgmName"))
      );
    } catch (RobotBridgeDeploymentException ex) {
      throw ex;
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().is4xxClientError()) {
        throw RobotBridgeDeploymentException.failed(errorCode(ex, "BRIDGE_HTTP_" + ex.getStatusCode().value()), errorMessage(ex, "Bridge 明确拒绝部署请求"));
      }
      throw RobotBridgeDeploymentException.unknown("BRIDGE_HTTP_" + ex.getStatusCode().value(), "Bridge 服务暂不可用，部署结果待对账");
    } catch (RestClientException ex) {
      throw RobotBridgeDeploymentException.unknown("BRIDGE_UNREACHABLE", "无法连接 Bridge，部署结果待对账");
    }
  }

  private String errorCode(RestClientResponseException ex, String fallback) {
    try {
      Map<String, Object> body = objectMapper.readValue(ex.getResponseBodyAsString(), new TypeReference<Map<String, Object>>() {});
      String code = text(body.get("code"));
      return code.matches("[A-Z0-9_]{1,64}") ? code : fallback;
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private String errorMessage(RestClientResponseException ex, String fallback) {
    try {
      Map<String, Object> body = objectMapper.readValue(ex.getResponseBodyAsString(), new TypeReference<Map<String, Object>>() {});
      String message = sanitize(text(body.get("message")));
      return message.isBlank() ? fallback : message;
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static String trimTrailingSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
  private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
  private static String sanitize(String value) {
    String compact = value.replaceAll("[\\r\\n]+", " ").trim();
    compact = compact.replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[REDACTED]");
    compact = compact.replaceAll("(?i)(authorization|token|password)=[^\\s,;]+", "$1=[REDACTED]");
    return compact.substring(0, Math.min(500, compact.length()));
  }
}
