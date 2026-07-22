package com.powerinspection.robot;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.robot")
public class RobotProperties {
  /** simulation = 平台模拟；http = 对接 ylhb mobile bridge */
  private String mode = "simulation";
  private String bridgeBaseUrl = "http://127.0.0.1:8000";
  private String robotId = "robot_001";
  private int pollIntervalMs = 2000;
  private int timeoutSeconds = 5;
  private String token = "";
  private boolean allowRegistration = false;
  /** Heartbeat Bridge 管理 API，仅由 Spring 服务端访问。 */
  private String heartbeatBridgeBaseUrl = "http://127.0.0.1:8001";
  private String bridgeAdminToken = "";
  private int bridgeConnectTimeoutSeconds = 3;
  private int bridgeReadTimeoutSeconds = 10;
  private int heartbeatSyncIntervalMs = 3000;
  private int heartbeatTimeoutSeconds = 12;
  /** 轨迹跳点过滤：相邻两点推算速度超过该值（m/s）则丢弃。 */
  private double maxGpsSpeedMps = 5.0;
  /** 平台侧 GPS 过期阈值（秒），基于 receivedAt - observedAt 重算。 */
  private int gpsStaleTimeoutSeconds = 5;
  /** 轨迹保留天数，0 表示不自动清理。 */
  private int trackRetentionDays = 90;
  /** 平台 robotId 到 Bridge/Jetson robotId 的显式映射。 */
  private Map<String, String> bridgeRobotIdMappings = new LinkedHashMap<>();
  /** 与 Bridge 的 PLATFORM_BEARER_TOKEN 相同，仅用于识别 Bridge 回读部署的请求。 */
  private String bridgePlatformToken = "";

  public String getMode() {
    return mode;
  }

  public void setMode(String mode) {
    this.mode = mode;
  }

  public String getBridgeBaseUrl() {
    return bridgeBaseUrl;
  }

  public void setBridgeBaseUrl(String bridgeBaseUrl) {
    this.bridgeBaseUrl = bridgeBaseUrl;
  }

  public String getRobotId() {
    return robotId;
  }

  public void setRobotId(String robotId) {
    this.robotId = robotId;
  }

  public int getPollIntervalMs() {
    return pollIntervalMs;
  }

  public void setPollIntervalMs(int pollIntervalMs) {
    this.pollIntervalMs = pollIntervalMs;
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public void setTimeoutSeconds(int timeoutSeconds) {
    this.timeoutSeconds = timeoutSeconds;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public boolean isAllowRegistration() {
    return allowRegistration;
  }

  public void setAllowRegistration(boolean allowRegistration) {
    this.allowRegistration = allowRegistration;
  }

  public boolean isHttpMode() {
    return "http".equalsIgnoreCase(mode);
  }

  public boolean isBridgeMode() {
    return "bridge".equalsIgnoreCase(mode);
  }

  public String getHeartbeatBridgeBaseUrl() { return heartbeatBridgeBaseUrl; }
  public void setHeartbeatBridgeBaseUrl(String heartbeatBridgeBaseUrl) { this.heartbeatBridgeBaseUrl = heartbeatBridgeBaseUrl; }
  public String getBridgeAdminToken() { return bridgeAdminToken; }
  public void setBridgeAdminToken(String bridgeAdminToken) { this.bridgeAdminToken = bridgeAdminToken; }
  public int getBridgeConnectTimeoutSeconds() { return bridgeConnectTimeoutSeconds; }
  public void setBridgeConnectTimeoutSeconds(int bridgeConnectTimeoutSeconds) { this.bridgeConnectTimeoutSeconds = bridgeConnectTimeoutSeconds; }
  public int getBridgeReadTimeoutSeconds() { return bridgeReadTimeoutSeconds; }
  public void setBridgeReadTimeoutSeconds(int bridgeReadTimeoutSeconds) { this.bridgeReadTimeoutSeconds = bridgeReadTimeoutSeconds; }
  public int getHeartbeatSyncIntervalMs() { return heartbeatSyncIntervalMs; }
  public void setHeartbeatSyncIntervalMs(int heartbeatSyncIntervalMs) { this.heartbeatSyncIntervalMs = heartbeatSyncIntervalMs; }
  public int getHeartbeatTimeoutSeconds() { return heartbeatTimeoutSeconds; }
  public void setHeartbeatTimeoutSeconds(int heartbeatTimeoutSeconds) { this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds; }
  public double getMaxGpsSpeedMps() { return maxGpsSpeedMps; }
  public void setMaxGpsSpeedMps(double maxGpsSpeedMps) { this.maxGpsSpeedMps = maxGpsSpeedMps; }
  public int getGpsStaleTimeoutSeconds() { return gpsStaleTimeoutSeconds; }
  public void setGpsStaleTimeoutSeconds(int gpsStaleTimeoutSeconds) { this.gpsStaleTimeoutSeconds = gpsStaleTimeoutSeconds; }
  public int getTrackRetentionDays() { return trackRetentionDays; }
  public void setTrackRetentionDays(int trackRetentionDays) { this.trackRetentionDays = trackRetentionDays; }
  public Map<String, String> getBridgeRobotIdMappings() { return bridgeRobotIdMappings; }
  public void setBridgeRobotIdMappings(Map<String, String> bridgeRobotIdMappings) {
    this.bridgeRobotIdMappings = bridgeRobotIdMappings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bridgeRobotIdMappings);
  }
  public String getBridgePlatformToken() { return bridgePlatformToken; }
  public void setBridgePlatformToken(String bridgePlatformToken) { this.bridgePlatformToken = bridgePlatformToken; }
}
