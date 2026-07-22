package com.powerinspection.robot;

import java.time.Duration;
import java.time.Instant;
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

/** 仅访问回环 Bridge 管理 API；不复用旧的 Jetson 局域网 Client。 */
@Component
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "bridge")
public class RobotBridgeHeartbeatClient {
  private final RestClient restClient;

  public RobotBridgeHeartbeatClient(RobotProperties properties) {
    if (!StringUtils.hasText(properties.getBridgeAdminToken())) throw new IllegalStateException("app.robot.bridge-admin-token is required in bridge mode");
    if (!StringUtils.hasText(properties.getHeartbeatBridgeBaseUrl())) throw new IllegalStateException("app.robot.heartbeat-bridge-base-url is required in bridge mode");
    ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
      .withConnectTimeout(Duration.ofSeconds(Math.max(1, properties.getBridgeConnectTimeoutSeconds())))
      .withReadTimeout(Duration.ofSeconds(Math.max(1, properties.getBridgeReadTimeoutSeconds())));
    this.restClient = RestClient.builder()
      .baseUrl(trimTrailingSlash(properties.getHeartbeatBridgeBaseUrl()))
      .requestFactory(ClientHttpRequestFactories.get(settings))
      .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getBridgeAdminToken())
      .build();
  }

  public List<String> configuredRobotIds() {
    Object values = get("/bridge/v1/health").get("robots");
    if (!(values instanceof Collection<?> robots)) throw new BridgeRobotClientException(BridgeRobotClientException.Reason.INVALID_PAYLOAD);
    return robots.stream().filter(String.class::isInstance).map(String.class::cast).filter(StringUtils::hasText).distinct().toList();
  }

  public BridgeRobotSnapshot robot(String robotId) {
    Map<String, Object> body = get("/bridge/v1/robots/" + robotId);
    if (!robotId.equals(text(body.get("robotId")))) throw new BridgeRobotClientException(BridgeRobotClientException.Reason.INVALID_PAYLOAD);
    String lastSeen = text(body.get("lastSeen"));
    if (lastSeen.isBlank()) {
      return new BridgeRobotSnapshot(robotId, null, "", "", "", null, "", 0, Map.of(), null, null,
        true, false, null, false, null, null);
    }
    Instant lastHeartbeatAt;
    try { lastHeartbeatAt = Instant.parse(lastSeen); }
    catch (RuntimeException ex) { throw new BridgeRobotClientException(BridgeRobotClientException.Reason.INVALID_PAYLOAD); }
    Object health = body.get("health");
    if (!(health instanceof Map<?, ?> healthMap)) throw new BridgeRobotClientException(BridgeRobotClientException.Reason.INVALID_PAYLOAD);
    Map<String, Object> normalizedHealth = new LinkedHashMap<>();
    healthMap.forEach((key, value) -> {
      if (key != null && value != null) normalizedHealth.put(String.valueOf(key), value);
    });
    Object capabilities = body.get("capabilities");
    Map<?, ?> capabilityMap;
    if (capabilities == null) capabilityMap = Map.of();
    else if (capabilities instanceof Map<?, ?> value) capabilityMap = value;
    else throw new BridgeRobotClientException(BridgeRobotClientException.Reason.INVALID_PAYLOAD);
    boolean reportedRemote = bool(capabilityMap.get("remoteImmediateStart"), true);
    boolean reportedLocal = bool(capabilityMap.get("localConfirmStart"), false);
    String localProtocolVersion = nullableText(capabilityMap.get("localConfirmProtocolVersion"));
    boolean localReady = bool(healthMap.get("localConfirmStartReady"), false);
    String localError = nullableText(healthMap.get("localConfirmStartError"));
    String executionId = nullableText(body.get("executionId"));
    if (executionId == null) executionId = nullableText(body.get("activeExecutionId"));
    BridgePatrolSnapshot patrol = PatrolSnapshotParser.parse(body.get("patrol"));
    return new BridgeRobotSnapshot(robotId, lastHeartbeatAt, text(body.get("protocolVersion")), text(body.get("bootId")), text(body.get("state")),
      executionId, text(body.get("softwareVersion")), number(body.get("acceptedEventSequence")), Map.copyOf(normalizedHealth),
      patrol, GnssFixParser.parse(body.get("gnssFix")), reportedRemote, reportedLocal, localProtocolVersion,
      localReady, localError, instant(body.get("capabilityReportedAt")));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> get(String path) {
    try {
      Map<String, Object> body = restClient.get().uri(path).retrieve().body(Map.class);
      if (body == null) throw new BridgeRobotClientException(BridgeRobotClientException.Reason.INVALID_PAYLOAD);
      return body;
    } catch (BridgeRobotClientException ex) {
      throw ex;
    } catch (RestClientResponseException ex) {
      throw new BridgeRobotClientException(ex.getStatusCode().value() == 401 ? BridgeRobotClientException.Reason.AUTH_FAILED : BridgeRobotClientException.Reason.UNREACHABLE);
    } catch (RestClientException ex) {
      throw new BridgeRobotClientException(BridgeRobotClientException.Reason.UNREACHABLE);
    }
  }

  private static String trimTrailingSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
  private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
  private static String nullableText(Object value) {
    if (value == null) return null;
    if (!(value instanceof String)) throw new BridgeRobotClientException(BridgeRobotClientException.Reason.INVALID_PAYLOAD);
    String result = ((String) value).trim();
    return result.isEmpty() ? null : result;
  }
  private static boolean bool(Object value, boolean defaultValue) {
    if (value == null) return defaultValue;
    if (value instanceof Boolean result) return result;
    throw new BridgeRobotClientException(BridgeRobotClientException.Reason.INVALID_PAYLOAD);
  }
  private static Instant instant(Object value) {
    String text = text(value);
    if (text.isBlank()) return null;
    try { return Instant.parse(text); }
    catch (RuntimeException ex) { throw new BridgeRobotClientException(BridgeRobotClientException.Reason.INVALID_PAYLOAD); }
  }
  private static long number(Object value) {
    if (value instanceof Number number) return Math.max(0, number.longValue());
    try { return Math.max(0, Long.parseLong(text(value))); }
    catch (NumberFormatException ex) { return 0; }
  }
}
