package com.powerinspection.task;

import com.powerinspection.robot.RobotBridgeExecutionClient;
import com.powerinspection.robot.RobotBridgeExecutionEvent;
import com.powerinspection.robot.RobotBridgeExecutionException;
import com.powerinspection.robot.RobotBridgeExecutionSnapshot;
import com.powerinspection.robot.RobotBridgeExecutionStartResult;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 平台重启和断联后的执行对账入口；所有远程调用都发生在本地事务之外。 */
@Component
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "bridge")
public class TaskExecutionWorker {
  private final TaskExecutionLifecycleService lifecycle;
  private final RobotEventIngestionService ingestion;
  private final RobotBridgeExecutionClient bridge;

  public TaskExecutionWorker(TaskExecutionLifecycleService lifecycle, RobotEventIngestionService ingestion, RobotBridgeExecutionClient bridge) {
    this.lifecycle = lifecycle;
    this.ingestion = ingestion;
    this.bridge = bridge;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recoverAfterRestart() { runOnce(); }

  @Scheduled(fixedDelayString = "${app.task-execution.worker-delay-ms:2000}", initialDelayString = "${app.task-execution.worker-initial-delay-ms:3000}")
  public void scheduledRun() { runOnce(); }

  public void runOnce() {
    for (String executionId : lifecycle.nonTerminalExecutionIds()) {
      try { synchronize(executionId); }
      catch (RuntimeException ignored) {
        // 单个执行异常不得阻塞其他机器人；状态已由下方的受控路径保存或等待下次对账。
      }
    }
  }

  private void synchronize(String executionId) {
    TaskExecutionEntity execution = lifecycle.findByExecutionId(executionId);
    if (execution == null || TaskExecutionStatus.CREATED.name().equals(execution.getStatus())) return;
    if (TaskExecutionStatus.STARTING.name().equals(execution.getStatus())) deliverStart(execution);
    execution = lifecycle.findByExecutionId(executionId);
    if (execution == null || TaskExecutionStatus.TERMINAL.contains(execution.getStatus())) return;
    boolean recovering = TaskExecutionStatus.DISCONNECTED.name().equals(execution.getStatus());
    if (recovering) execution = lifecycle.beginRecovery(executionId, Instant.now());
    poll(execution, recovering);
  }

  private void deliverStart(TaskExecutionEntity execution) {
    TaskExecutionLifecycleService.StartCommand command = lifecycle.claimStartAttempt(execution.getExecutionId(), Instant.now());
    if (command == null) return;
    try {
      RobotBridgeExecutionStartResult result = bridge.start(command.executionId(), command.bridgePayload());
      if (!result.accepted() || !Objects.equals(command.executionId(), result.executionId()) || !"QUEUED".equals(result.state()) || result.commandId().isBlank()) {
        lifecycle.startFailed(command.executionId(), "INVALID_BRIDGE_PAYLOAD", "Bridge 启动回执与本地执行身份不一致", Instant.now());
        return;
      }
      lifecycle.accepted(command.executionId(), result.commandId(), Instant.now());
    } catch (RobotBridgeExecutionException ex) {
      if (ex.getDisposition() == RobotBridgeExecutionException.Disposition.EXPLICIT_FAILURE) {
        lifecycle.startFailed(command.executionId(), ex.getErrorCode(), ex.getMessage(), Instant.now());
      } else {
        lifecycle.disconnected(command.executionId(), ex.getErrorCode(), ex.getMessage(), Instant.now());
      }
    }
  }

  private void poll(TaskExecutionEntity execution, boolean recovering) {
    try {
      RobotBridgeExecutionSnapshot snapshot = bridge.execution(execution.getExecutionId());
      if (!Objects.equals(execution.getExecutionId(), snapshot.executionId()) || !Objects.equals(execution.getRobotId(), snapshot.robotId())
          || !Objects.equals(execution.getDeploymentId(), snapshot.deploymentId())) {
        ingestion.bridgeOwnershipConflict(execution.getExecutionId(), "BRIDGE_EXECUTION_OWNERSHIP_CONFLICT", "Bridge 执行查询返回的身份与本地执行快照不一致");
        return;
      }
      List<RobotBridgeExecutionEvent> events = bridge.events(execution.getExecutionId(), execution.getLastRobotSequence());
      events.stream().sorted(Comparator.comparingLong(RobotBridgeExecutionEvent::sequence))
        .forEach(event -> ingestion.ingest(execution.getExecutionId(), event));
      if (recovering) lifecycle.completeRecovery(execution.getExecutionId(), Instant.now());
    } catch (RobotBridgeExecutionException ex) {
      if (recovering && "EXECUTION_NOT_FOUND".equals(ex.getErrorCode())
          && TaskExecutionStatus.STARTING.name().equals(execution.getRecoveryStatus()) && (execution.getStartCommandId() == null || execution.getStartCommandId().isBlank())) {
        // 超时前 Bridge 可能尚未持久化；恢复为 STARTING 后以同一 requestId 保守重试。
        lifecycle.completeRecovery(execution.getExecutionId(), Instant.now());
      } else if (ex.getDisposition() == RobotBridgeExecutionException.Disposition.EXPLICIT_FAILURE && "EXECUTION_NOT_FOUND".equals(ex.getErrorCode())) {
        ingestion.bridgeOwnershipConflict(execution.getExecutionId(), "EXECUTION_NOT_FOUND", "Bridge 找不到本地应存在的执行实例");
      } else {
        lifecycle.disconnected(execution.getExecutionId(), ex.getErrorCode(), ex.getMessage(), Instant.now());
      }
    }
  }
}
