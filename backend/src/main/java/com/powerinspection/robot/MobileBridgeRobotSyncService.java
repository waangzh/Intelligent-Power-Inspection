package com.powerinspection.robot;

import com.powerinspection.common.ResourceChangeEvent;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "http")
public class MobileBridgeRobotSyncService {
  private static final List<String> ACTIVE_TASK_STATUSES = List.of("DISPATCHED", "RUNNING", "PAUSED", "MANUAL_TAKEOVER");

  private final MobileBridgeClient bridgeClient;
  private final RobotProperties properties;
  private final DataStoreService dataStore;
  private final SimpMessagingTemplate messagingTemplate;

  public MobileBridgeRobotSyncService(
    MobileBridgeClient bridgeClient,
    RobotProperties properties,
    DataStoreService dataStore,
    SimpMessagingTemplate messagingTemplate
  ) {
    this.bridgeClient = bridgeClient;
    this.properties = properties;
    this.dataStore = dataStore;
    this.messagingTemplate = messagingTemplate;
  }

  @Scheduled(fixedDelayString = "${app.robot.poll-interval-ms:2000}")
  @Transactional
  public void sync() {
    String robotId = properties.getRobotId();
    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, robotId);
    if (robot == null) {
      return;
    }

    Optional<Map<String, Object>> statusOpt = bridgeClient.fetchStatus();
    Optional<Map<String, Object>> patrolOpt = bridgeClient.fetchPatrolStatus();
    boolean bridgeReachable = statusOpt.isPresent();

    Map<String, Object> telemetry = new LinkedHashMap<>();
    telemetry.put("bridgeBaseUrl", properties.getBridgeBaseUrl());
    telemetry.put("bridgeReachable", bridgeReachable);
    telemetry.put("bridgeSyncedAt", Instant.now().toString());

    if (statusOpt.isPresent()) {
      Map<String, Object> status = statusOpt.get();
      telemetry.put("online", status.get("online"));
      telemetry.put("canStatus", status.get("can_status"));
      telemetry.put("zlacStatus", status.get("zlac_status"));
      telemetry.put("taskStatus", status.get("task_status"));
      telemetry.put("systemMode", status.get("system_mode"));
      telemetry.put("mappingStatus", status.get("mapping_status"));
      telemetry.put("nav2Status", status.get("nav2_status"));
      telemetry.put("lastOdomAgeSec", status.get("last_odom_age_sec"));
      telemetry.put("lastScanAgeSec", status.get("last_scan_age_sec"));
      telemetry.put("velocity", status.get("velocity"));
      telemetry.put("pose", status.get("pose"));
    } else {
      telemetry.put("online", false);
    }

    if (patrolOpt.isPresent()) {
      Map<String, Object> patrol = patrolOpt.get();
      telemetry.put("patrolState", patrol.get("state"));
      telemetry.put("patrolExecutorRunning", patrol.get("executor_running"));
      telemetry.put("patrolMessage", patrol.get("message"));
      telemetry.put("activeRouteId", patrol.get("active_route_id"));
      telemetry.put("activeTargetId", patrol.get("active_target_id"));
    } else {
      telemetry.put("patrolState", "unavailable");
    }

    robot.put("telemetry", telemetry);
    robot.put("lastOnlineAt", Instant.now().toString());
    robot.put("status", derivePlatformStatus(robot, telemetry));
    applyPose(robot, telemetry);
    syncTaskFromPatrol(robot, text(telemetry.get("patrolState")));

    dataStore.upsert(DataCategory.ROBOT, robot);
    publishRobot(robot);
  }

  private void applyPose(Map<String, Object> robot, Map<String, Object> telemetry) {
    Object pose = telemetry.get("pose");
    if (!(pose instanceof Map<?, ?> poseMap)) {
      return;
    }
    double x = toDouble(poseMap.get("x"));
    double y = toDouble(poseMap.get("y"));
    double yaw = toDouble(poseMap.get("yaw"));
    robot.put("position", Map.of("lat", y, "lng", x, "x", x, "y", y, "yaw", yaw));
  }

  private String derivePlatformStatus(Map<String, Object> robot, Map<String, Object> telemetry) {
    if (!Boolean.TRUE.equals(telemetry.get("bridgeReachable")) || !Boolean.TRUE.equals(telemetry.get("online"))) {
      return "OFFLINE";
    }
    String taskId = text(robot.get("currentTaskId"));
    if (!taskId.isBlank()) {
      Map<String, Object> task = dataStore.find(DataCategory.TASK, taskId);
      if (task != null && ACTIVE_TASK_STATUSES.contains(text(task.get("status")))) {
        return "BUSY";
      }
    }
    String patrolState = text(telemetry.get("patrolState"));
    if (List.of("running", "paused").contains(patrolState)) {
      return "BUSY";
    }
    return "ONLINE";
  }

  private void syncTaskFromPatrol(Map<String, Object> robot, String patrolState) {
    String taskId = text(robot.get("currentTaskId"));
    if (taskId == null || taskId.isBlank()) {
      return;
    }
    Map<String, Object> task = dataStore.find(DataCategory.TASK, taskId);
    if (task == null) {
      robot.put("currentTaskId", null);
      return;
    }

    String taskStatus = text(task.get("status"));
    switch (patrolState) {
      case "running" -> {
        if ("DISPATCHED".equals(taskStatus) || "PAUSED".equals(taskStatus) || "MANUAL_TAKEOVER".equals(taskStatus)) {
          task.put("status", "RUNNING");
          saveTask(task);
        }
      }
      case "paused" -> {
        if ("RUNNING".equals(taskStatus)) {
          task.put("status", "PAUSED");
          saveTask(task);
        }
      }
      case "succeeded" -> completeTask(task, robot);
      case "failed", "canceled" -> cancelTask(task, robot);
      default -> {
      }
    }
  }

  private void completeTask(Map<String, Object> task, Map<String, Object> robot) {
    if ("COMPLETED".equals(text(task.get("status")))) {
      return;
    }
    task.put("status", "COMPLETED");
    task.put("progress", 100);
    task.put("completedAt", Instant.now().toString());
    saveTask(task);
    robot.put("status", "ONLINE");
    robot.put("currentTaskId", null);
  }

  private void cancelTask(Map<String, Object> task, Map<String, Object> robot) {
    String status = text(task.get("status"));
    if ("CANCELLED".equals(status) || "ESTOPPED".equals(status)) {
      return;
    }
    task.put("status", "CANCELLED");
    task.put("completedAt", Instant.now().toString());
    saveTask(task);
    robot.put("status", "ONLINE");
    robot.put("currentTaskId", null);
  }

  private void saveTask(Map<String, Object> task) {
    Map<String, Object> saved = dataStore.upsert(DataCategory.TASK, task);
    ResourceChangeEvent taskEvent = ResourceChangeEvent.updated("task", saved.get("id"));
    messagingTemplate.convertAndSend("/topic/tasks/" + saved.get("id"), taskEvent);
    messagingTemplate.convertAndSend("/topic/tasks", taskEvent);
  }

  private void publishRobot(Map<String, Object> robot) {
    ResourceChangeEvent robotEvent = ResourceChangeEvent.updated("robot", robot.get("id"));
    messagingTemplate.convertAndSend("/topic/robots/" + robot.get("id"), robotEvent);
    messagingTemplate.convertAndSend("/topic/robots", robotEvent);
  }

  private String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private double toDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value == null) {
      return 0;
    }
    try {
      return Double.parseDouble(value.toString());
    } catch (NumberFormatException ex) {
      return 0;
    }
  }
}
