package com.powerinspection.robot;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "http")
public class MobileBridgeRobotSyncService {
  private final MobileBridgeClient bridgeClient;
  private final RobotProperties properties;
  private final DataStoreService dataStore;
  private final SimpMessagingTemplate messagingTemplate;

  public MobileBridgeRobotSyncService(MobileBridgeClient bridgeClient, RobotProperties properties, DataStoreService dataStore, SimpMessagingTemplate messagingTemplate) {
    this.bridgeClient = bridgeClient; this.properties = properties; this.dataStore = dataStore; this.messagingTemplate = messagingTemplate;
  }

  @Scheduled(fixedDelayString = "${app.robot.poll-interval-ms:2000}")
  public void sync() {
    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, properties.getRobotId());
    if (robot == null) return;
    Map<String, Object> telemetry = new LinkedHashMap<>();
    bridgeClient.fetchStatus().ifPresentOrElse(status -> { telemetry.putAll(status); telemetry.put("bridgeReachable", true); }, () -> telemetry.put("bridgeReachable", false));
    bridgeClient.fetchPatrolStatus().ifPresent(patrol -> telemetry.put("patrol", patrol));
    telemetry.put("bridgeBaseUrl", properties.getBridgeBaseUrl());
    telemetry.put("bridgeSyncedAt", Instant.now().toString());
    robot.put("telemetry", telemetry);
    robot.put("lastOnlineAt", Instant.now().toString());
    robot.put("status", Boolean.TRUE.equals(telemetry.get("bridgeReachable")) && Boolean.TRUE.equals(telemetry.get("online")) ? "ONLINE" : "OFFLINE");
    if (telemetry.get("pose") instanceof Map<?, ?> pose) robot.put("position", Map.of("lat", value(pose, "y"), "lng", value(pose, "x"), "x", value(pose, "x"), "y", value(pose, "y"), "yaw", value(pose, "yaw")));
    Map<String, Object> saved = dataStore.upsert(DataCategory.ROBOT, robot);
    messagingTemplate.convertAndSend("/topic/robots/" + saved.get("id"), saved);
    messagingTemplate.convertAndSend("/topic/robots", saved);
  }

  private Object value(Map<?, ?> raw, String key) { Object value = raw.get(key); return value == null ? 0 : value; }
}
