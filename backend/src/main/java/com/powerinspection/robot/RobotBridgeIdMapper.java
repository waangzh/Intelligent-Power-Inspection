package com.powerinspection.robot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 隔离平台与 Bridge 的机器人身份差异。
 *
 * <p>平台内部始终使用平台注册的 ID；仅在跨越 Bridge 边界时转换为设备 ID。</p>
 */
@Component
public class RobotBridgeIdMapper {
  private final Map<String, String> platformToBridge = new LinkedHashMap<>();
  private final Map<String, String> bridgeToPlatform = new LinkedHashMap<>();
  private final String bridgePlatformToken;

  public RobotBridgeIdMapper(RobotProperties properties) {
    properties.getBridgeRobotIdMappings().forEach(this::register);
    this.bridgePlatformToken = normalized(properties.getBridgePlatformToken());
  }

  public String toBridgeId(String platformRobotId) {
    String robotId = normalized(platformRobotId);
    return robotId == null ? "" : platformToBridge.getOrDefault(robotId, robotId);
  }

  public String toPlatformId(String bridgeRobotId) {
    String robotId = normalized(bridgeRobotId);
    return robotId == null ? "" : bridgeToPlatform.getOrDefault(robotId, robotId);
  }

  /** 仅向持有 Bridge 平台凭据的回读请求暴露 Bridge 侧 ID。 */
  public boolean isBridgePlatformRequest(String authorization) {
    if (bridgePlatformToken == null || authorization == null) return false;
    byte[] expected = ("Bearer " + bridgePlatformToken).getBytes(StandardCharsets.UTF_8);
    byte[] actual = authorization.trim().getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(expected, actual);
  }

  public Map<String, Object> toBridgeDeploymentView(Map<String, Object> deployment) {
    Map<String, Object> result = new LinkedHashMap<>(deployment);
    mapRobotId(result, "robotId");
    Object remoteSummary = result.get("remoteSummary");
    if (remoteSummary instanceof Map<?, ?> values) {
      Map<String, Object> summary = new LinkedHashMap<>();
      values.forEach((key, value) -> summary.put(String.valueOf(key), value));
      mapRobotId(summary, "robotId");
      result.put("remoteSummary", summary);
    }
    return result;
  }

  private void register(String platformValue, String bridgeValue) {
    String platformId = normalized(platformValue);
    String bridgeId = normalized(bridgeValue);
    if (platformId == null || bridgeId == null) {
      throw new IllegalStateException("app.robot.bridge-robot-id-mappings must contain non-blank platform and Bridge robot IDs");
    }
    String previousPlatform = bridgeToPlatform.putIfAbsent(bridgeId, platformId);
    if (previousPlatform != null && !previousPlatform.equals(platformId)) {
      throw new IllegalStateException("multiple platform robot IDs cannot map to the same Bridge robot ID: " + bridgeId);
    }
    platformToBridge.put(platformId, bridgeId);
  }

  private void mapRobotId(Map<String, Object> values, String field) {
    Object value = values.get(field);
    if (value != null) values.put(field, toBridgeId(String.valueOf(value)));
  }

  private static String normalized(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
