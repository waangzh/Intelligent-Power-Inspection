package com.powerinspection.robot;

import com.powerinspection.common.ApiException;
import com.powerinspection.route.RouteExecutorSupport;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "simulation", matchIfMissing = true)
public class SimulationRobotGateway implements RobotGateway {
  @Override
  public void dispatchTask(Map<String, Object> robot, Map<String, Object> task, Map<String, Object> route) {
    // Simulation mode accepts commands immediately.
  }

  @Override
  public void pauseTask(Map<String, Object> robot, Map<String, Object> task) {
    // Simulation mode accepts commands immediately.
  }

  @Override
  public void resumeTask(Map<String, Object> robot, Map<String, Object> task) {
    // Simulation mode accepts commands immediately.
  }

  @Override
  public void takeoverTask(Map<String, Object> robot, Map<String, Object> task) {
    // Simulation mode accepts commands immediately.
  }

  @Override
  public void cancelTask(Map<String, Object> robot, Map<String, Object> task) {
    // Simulation mode accepts commands immediately.
  }

  @Override
  public void emergencyStopTask(Map<String, Object> robot, Map<String, Object> task) {
    // Simulation mode accepts emergency stop immediately (status handled by TaskService).
  }

  @Override
  @SuppressWarnings("unchecked")
  public RobotProgressSnapshot advanceTask(Map<String, Object> robot, Map<String, Object> task, Map<String, Object> route) {
    List<Map<String, Object>> path = RouteExecutorSupport.compatiblePath(route);
    if (path.isEmpty()) {
      throw ApiException.badRequest("路线缺少路径点，无法执行任务");
    }
    int nextProgress = Math.min(100, number(task.get("progress")) + 4);
    int pathIndex = Math.min(path.size() - 1, (int) Math.floor((nextProgress / 100.0) * (path.size() - 1)));
    Map<String, Object> position = path.get(pathIndex);

    List<Map<String, Object>> checkpoints = RouteExecutorSupport.compatibleCheckpoints(route);
    int checkpointSeq = checkpoints.isEmpty() ? 0 : Math.min(checkpoints.size(), (int) Math.ceil((nextProgress / 100.0) * checkpoints.size()));
    return new RobotProgressSnapshot(nextProgress, checkpointSeq, position, null);
  }

  private int number(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException ex) {
      throw ApiException.badRequest("数字格式错误");
    }
  }
}
