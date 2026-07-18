package com.powerinspection.robot;

import com.powerinspection.common.ApiException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** P2a 只接入心跳；bridge 模式下明确阻止旧任务链路触发机器人运动。 */
@Service
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "bridge")
public class BridgeRobotGateway implements RobotGateway {
  @Override public void dispatchTask(Map<String, Object> robot, Map<String, Object> task, Map<String, Object> route) { throw unavailable(); }
  @Override public void pauseTask(Map<String, Object> robot, Map<String, Object> task) { throw unavailable(); }
  @Override public void resumeTask(Map<String, Object> robot, Map<String, Object> task) { throw unavailable(); }
  @Override public void takeoverTask(Map<String, Object> robot, Map<String, Object> task) { throw unavailable(); }
  @Override public void cancelTask(Map<String, Object> robot, Map<String, Object> task) { throw unavailable(); }
  @Override public void emergencyStopTask(Map<String, Object> robot, Map<String, Object> task) { throw unavailable(); }
  @Override public RobotProgressSnapshot advanceTask(Map<String, Object> robot, Map<String, Object> task, Map<String, Object> route) { throw unavailable(); }
  private ApiException unavailable() { return ApiException.badRequest("当前 bridge 模式仅启用机器人心跳，尚未开放路线下发或导航控制"); }
}
