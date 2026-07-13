package com.powerinspection.robot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.robot")
public class RobotProperties {
  private String mode = "simulation";
  private String bridgeBaseUrl = "http://127.0.0.1:8000";
  private String robotId = "robot_001";
  private int pollIntervalMs = 2000;
  private int timeoutSeconds = 5;
  private String token = "";

  public String getMode() { return mode; }
  public void setMode(String mode) { this.mode = mode; }
  public String getBridgeBaseUrl() { return bridgeBaseUrl; }
  public void setBridgeBaseUrl(String bridgeBaseUrl) { this.bridgeBaseUrl = bridgeBaseUrl; }
  public String getRobotId() { return robotId; }
  public void setRobotId(String robotId) { this.robotId = robotId; }
  public int getPollIntervalMs() { return pollIntervalMs; }
  public void setPollIntervalMs(int pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
  public int getTimeoutSeconds() { return timeoutSeconds; }
  public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
  public String getToken() { return token; }
  public void setToken(String token) { this.token = token; }
}
