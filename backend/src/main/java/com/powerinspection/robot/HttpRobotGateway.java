package com.powerinspection.robot;

import com.powerinspection.common.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "http")
public class HttpRobotGateway implements RobotGateway {
  private final MobileBridgeClient bridgeClient;

  public HttpRobotGateway(MobileBridgeClient bridgeClient) { this.bridgeClient = bridgeClient; }
  @Override public void dispatchTask(Map<String, Object> robot, Map<String, Object> task, Map<String, Object> route) { command("start", "巡逻启动失败，请确认机器人 patrol executor 已运行"); }
  @Override public void pauseTask(Map<String, Object> robot, Map<String, Object> task) { command("pause", "巡逻暂停失败"); }
  @Override public void resumeTask(Map<String, Object> robot, Map<String, Object> task) { command("resume", "巡逻恢复失败"); }
  @Override public void takeoverTask(Map<String, Object> robot, Map<String, Object> task) { command("pause", "人工接管失败：无法暂停巡逻"); }
  @Override public void cancelTask(Map<String, Object> robot, Map<String, Object> task) { bridgeClient.emergencyStop(); command("cancel", "巡逻取消失败"); }
  @Override public RobotProgressSnapshot advanceTask(Map<String, Object> robot, Map<String, Object> task, Map<String, Object> route) {
    Map<String, Object> position = robot.get("position") instanceof Map<?, ?> pose ? toMap(pose) : Map.of("lat", 0, "lng", 0, "x", 0, "y", 0, "yaw", 0);
    return new RobotProgressSnapshot(number(task.get("progress")), number(task.get("currentCheckpointSeq")), position);
  }
  private void command(String command, String message) { if (!bridgeClient.sendPatrolCommand(command)) throw ApiException.badRequest(message); }
  private int number(Object value) { try { return value == null ? 0 : (int) Math.round(Double.parseDouble(String.valueOf(value))); } catch (NumberFormatException ex) { return 0; } }
  private Map<String, Object> toMap(Map<?, ?> raw) { return Map.of("lat", value(raw, "y"), "lng", value(raw, "x"), "x", value(raw, "x"), "y", value(raw, "y"), "yaw", value(raw, "yaw")); }
  private Object value(Map<?, ?> raw, String key) { Object value = raw.get(key); return value == null ? 0 : value; }
}
