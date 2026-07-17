package com.powerinspection.robot;

import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "http")
public class HttpRobotGateway implements RobotGateway {
  private final MobileBridgeClient bridgeClient;
  private final DataStoreService dataStore;

  public HttpRobotGateway(MobileBridgeClient bridgeClient, DataStoreService dataStore) {
    this.bridgeClient = bridgeClient;
    this.dataStore = dataStore;
  }

  @Override
  public void dispatchTask(Map<String, Object> robot, Map<String, Object> task, Map<String, Object> route) {
    if (!bridgeClient.sendPatrolCommand("start")) {
      throw ApiException.badRequest("巡逻启动失败，请确认机器人 patrol executor 已运行");
    }
  }

  @Override
  public void pauseTask(Map<String, Object> robot, Map<String, Object> task) {
    if (!bridgeClient.sendPatrolCommand("pause")) {
      throw ApiException.badRequest("巡逻暂停失败");
    }
  }

  @Override
  public void resumeTask(Map<String, Object> robot, Map<String, Object> task) {
    if (!bridgeClient.sendPatrolCommand("resume")) {
      throw ApiException.badRequest("巡逻恢复失败");
    }
  }

  @Override
  public void takeoverTask(Map<String, Object> robot, Map<String, Object> task) {
    if (!bridgeClient.sendPatrolCommand("pause")) {
      throw ApiException.badRequest("人工接管失败：无法暂停巡逻");
    }
  }

  @Override
  public void cancelTask(Map<String, Object> robot, Map<String, Object> task) {
    boolean canceled = bridgeClient.sendPatrolCommand("cancel");
    bridgeClient.emergencyStop();
    if (!canceled) {
      throw ApiException.badRequest("巡逻取消失败");
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public RobotProgressSnapshot advanceTask(Map<String, Object> robot, Map<String, Object> task, Map<String, Object> route) {
    int progress = number(task.get("progress"));
    int checkpointSeq = number(task.get("currentCheckpointSeq"));
    Map<String, Object> position = positionFromRobot(robot);
    if (position == null) {
      List<Map<String, Object>> path = pathFromRoute(route);
      position = path.isEmpty() ? Map.of("lat", 0, "lng", 0, "x", 0, "y", 0, "yaw", 0) : path.get(0);
    }
    return new RobotProgressSnapshot(progress, checkpointSeq, position, RobotInspectionImage.fromRobot(robot));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> positionFromRobot(Map<String, Object> robot) {
    Object raw = robot.get("position");
    if (raw instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    Object telemetry = robot.get("telemetry");
    if (!(telemetry instanceof Map<?, ?> telemetryMap)) {
      return null;
    }
    Object pose = telemetryMap.get("pose");
    if (!(pose instanceof Map<?, ?> poseMap)) {
      return null;
    }
    double x = toDouble(poseMap.get("x"));
    double y = toDouble(poseMap.get("y"));
    double yaw = toDouble(poseMap.get("yaw"));
    return Map.of("lat", y, "lng", x, "x", x, "y", y, "yaw", yaw);
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

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> pathFromRoute(Map<String, Object> route) {
    Object raw = route.get("path");
    if (raw instanceof List<?> list) {
      return (List<Map<String, Object>>) list;
    }
    return List.of();
  }

  private int number(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    try {
      return (int) Math.round(Double.parseDouble(value.toString()));
    } catch (NumberFormatException ex) {
      return 0;
    }
  }
}
