package com.powerinspection.task;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.robot.RobotConnectionStatus;
import com.powerinspection.robot.RobotHeartbeatService;
import com.powerinspection.robot.RobotHeartbeatStatusView;
import com.powerinspection.robot.RobotProperties;
import com.powerinspection.user.UserEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 控制意图只进入等待态；Bridge 回执、ACK 和真实事件均保留为独立审计阶段。 */
@Service
public class TaskExecutionControlService {
  private static final List<String> RETRYABLE = List.of(TaskExecutionControlCommandStatus.PENDING_SEND.name(),
    TaskExecutionControlCommandStatus.SENDING.name(), TaskExecutionControlCommandStatus.RECONCILING.name());

  private final TaskExecutionRepository executions;
  private final TaskExecutionControlCommandRepository commands;
  private final TaskExecutionLifecycleService lifecycle;
  private final DataStoreService dataStore;
  private final RobotHeartbeatService heartbeats;
  private final RobotProperties robotProperties;

  public TaskExecutionControlService(TaskExecutionRepository executions, TaskExecutionControlCommandRepository commands,
      TaskExecutionLifecycleService lifecycle, DataStoreService dataStore, RobotHeartbeatService heartbeats, RobotProperties robotProperties) {
    this.executions = executions;
    this.commands = commands;
    this.lifecycle = lifecycle;
    this.dataStore = dataStore;
    this.heartbeats = heartbeats;
    this.robotProperties = robotProperties;
  }

  @Transactional(noRollbackFor = ApiException.class)
  public Map<String, Object> request(String taskId, TaskExecutionControlAction action, String idempotencyKey,
      String takeoverReason, UserEntity operator) {
    requireKey(idempotencyKey);
    String reason = normalizedReason(action, takeoverReason);
    TaskExecutionControlCommandEntity sameRequest = commands.findByRequestId(idempotencyKey).orElse(null);
    if (sameRequest != null) {
      String expected = fingerprint(taskId, sameRequest.getExecutionId(), sameRequest.getRobotId(), sameRequest.getDeploymentId(), action, reason);
      if (!sameRequest.getTaskId().equals(taskId) || !sameRequest.getAction().equals(action.name())
          || !Objects.equals(sameRequest.getRequestFingerprint(), expected)) {
        throw ApiException.conflict("Idempotency-Key 已用于不同的任务控制意图");
      }
      return lifecycle.detail(taskId);
    }
    if (!robotProperties.isBridgeMode()) throw ApiException.conflict("当前环境未启用 Bridge 任务控制模式");
    TaskExecutionEntity execution = executions.findByTaskIdForStart(taskId)
      .orElseThrow(() -> ApiException.conflict("任务未绑定不可变执行快照"));
    requireBoundTask(taskId, execution);
    requireLegalStatus(execution, action);
    RobotHeartbeatStatusView heartbeat = heartbeats.detail(execution.getRobotId());
    if (!heartbeat.source().bridgeConfigured() || !heartbeat.online()
        || !RobotConnectionStatus.CONNECTED.name().equals(heartbeat.connectionStatus())) {
      lifecycle.disconnected(execution.getExecutionId(), "ROBOT_OFFLINE", "机器人离线或 Bridge 当前不可达", Instant.now());
      throw ApiException.conflict("机器人离线或 Bridge 当前不可达，控制结果等待保守对账");
    }

    String now = Instant.now().toString();
    TaskExecutionControlCommandEntity command = new TaskExecutionControlCommandEntity();
    command.setId(Ids.next("tctl"));
    command.setTaskId(taskId);
    command.setExecutionId(execution.getExecutionId());
    command.setRobotId(execution.getRobotId());
    command.setDeploymentId(execution.getDeploymentId());
    command.setAction(action.name());
    command.setRequestId(idempotencyKey);
    command.setRequestFingerprint(fingerprint(taskId, execution.getExecutionId(), execution.getRobotId(), execution.getDeploymentId(), action, reason));
    command.setStatus(TaskExecutionControlCommandStatus.PENDING_SEND.name());
    command.setPriorExecutionStatus(execution.getStatus());
    command.setTakeoverReason(reason);
    command.setRequestedById(operator.getId());
    command.setRequestedByName(displayName(operator));
    command.setRequestedAt(now);
    command.setCreatedAt(now);
    command.setUpdatedAt(now);
    commands.save(command);
    execution.setStatus(waitingStatus(action).name());
    execution.setUpdatedAt(now);
    executions.save(execution);
    return lifecycle.detail(execution);
  }

  @Transactional
  public ControlCommand claimNext(String executionId, Instant now) {
    TaskExecutionEntity execution = executions.findByExecutionIdForUpdate(executionId).orElse(null);
    if (execution == null || TaskExecutionStatus.TERMINAL.contains(execution.getStatus())) return null;
    TaskExecutionControlCommandEntity command = commands.findFirstByExecutionIdAndStatusInOrderByRequestedAtDesc(executionId, RETRYABLE).orElse(null);
    if (command == null || recent(command.getLastAttemptAt(), now)) return null;
    command.setStatus(TaskExecutionControlCommandStatus.SENDING.name());
    command.setLastAttemptAt(now.toString());
    command.setUpdatedAt(now.toString());
    commands.save(command);
    return new ControlCommand(command.getId(), command.getExecutionId(), command.getAction(), command.getRobotId(), command.getRequestId());
  }

  @Transactional
  public void queued(ControlCommand claimed, String commandId, Instant now) {
    TaskExecutionControlCommandEntity command = commands.findById(claimed.id()).orElse(null);
    if (command == null || !TaskExecutionControlCommandStatus.SENDING.name().equals(command.getStatus())) return;
    if (!nonBlank(commandId)) {
      fail(claimed.executionId(), command, "INVALID_BRIDGE_PAYLOAD", "Bridge 控制回执缺少 commandId", now, "RESTORE_PREVIOUS_STATUS");
      return;
    }
    command.setCommandId(commandId);
    command.setStatus(TaskExecutionControlCommandStatus.QUEUED.name());
    command.setResultCode("BRIDGE_QUEUED");
    command.setResultMessage("命令已由 Bridge 持久化，等待机器人 ACK 与真实事件确认");
    command.setUpdatedAt(now.toString());
    commands.save(command);
  }

  @Transactional
  public void explicitFailure(ControlCommand claimed, String code, String message, Instant now) {
    TaskExecutionControlCommandEntity command = commands.findById(claimed.id()).orElse(null);
    if (command != null) fail(claimed.executionId(), command, code, message, now, "RESTORE_PREVIOUS_STATUS");
  }

  @Transactional
  public void reconcile(ControlCommand claimed, String code, String message, Instant now) {
    TaskExecutionControlCommandEntity command = commands.findById(claimed.id()).orElse(null);
    if (command == null || TaskExecutionControlCommandStatus.CONFIRMED.name().equals(command.getStatus())
        || TaskExecutionControlCommandStatus.FAILED.name().equals(command.getStatus())) return;
    command.setStatus(TaskExecutionControlCommandStatus.RECONCILING.name());
    command.setRecoveryAction("RETRY_SAME_REQUEST_ID_AFTER_RECONCILIATION");
    command.setResultCode(safeCode(code));
    command.setResultMessage(safeMessage(message));
    command.setUpdatedAt(now.toString());
    commands.save(command);
  }

  public boolean needsStartDelivery(String executionId) {
    TaskExecutionControlCommandEntity command = commands.findFirstByExecutionIdAndStatusInOrderByRequestedAtDesc(executionId, RETRYABLE).orElse(null);
    return command != null && TaskExecutionControlAction.CANCEL.name().equals(command.getAction())
      && TaskExecutionStatus.STARTING.name().equals(command.getPriorExecutionStatus());
  }

  private void fail(String executionId, TaskExecutionControlCommandEntity command, String code, String message, Instant now, String recoveryAction) {
    command.setStatus(TaskExecutionControlCommandStatus.FAILED.name());
    command.setResultCode(safeCode(code));
    command.setResultMessage(safeMessage(message));
    command.setRecoveryAction(recoveryAction);
    command.setUpdatedAt(now.toString());
    commands.save(command);
    TaskExecutionEntity execution = executions.findByExecutionIdForUpdate(executionId).orElse(null);
    if (execution != null && !TaskExecutionStatus.TERMINAL.contains(execution.getStatus())
        && waitingStatus(TaskExecutionControlAction.valueOf(command.getAction())).name().equals(execution.getStatus())) {
      execution.setStatus(command.getPriorExecutionStatus());
      execution.setUpdatedAt(now.toString());
      executions.save(execution);
    }
  }

  private void requireBoundTask(String taskId, TaskExecutionEntity execution) {
    Map<String, Object> task = dataStore.get(DataCategory.TASK, taskId);
    if (!same(task.get("executionId"), execution.getExecutionId()) || !same(task.get("robotId"), execution.getRobotId())
        || !same(task.get("routeRevisionId"), execution.getRouteRevisionId()) || !same(task.get("routeContentSha256"), execution.getRouteContentSha256())
        || !same(task.get("mapImageSha256"), execution.getMapImageSha256()) || !nonBlank(execution.getDeploymentId())) {
      throw ApiException.conflict("任务绑定的 robot、execution 或 deployment 身份不一致");
    }
  }

  private static void requireLegalStatus(TaskExecutionEntity execution, TaskExecutionControlAction action) {
    boolean valid = switch (action) {
      case PAUSE, TAKEOVER -> TaskExecutionStatus.RUNNING.name().equals(execution.getStatus());
      case RESUME -> TaskExecutionStatus.PAUSED.name().equals(execution.getStatus());
      case CANCEL -> List.of(TaskExecutionStatus.RUNNING.name(), TaskExecutionStatus.PAUSED.name(), TaskExecutionStatus.STARTING.name()).contains(execution.getStatus());
    };
    if (!valid) throw ApiException.conflict("当前执行状态不允许该控制操作");
  }

  private static TaskExecutionStatus waitingStatus(TaskExecutionControlAction action) {
    return switch (action) {
      case PAUSE -> TaskExecutionStatus.PAUSING;
      case RESUME -> TaskExecutionStatus.RESUMING;
      case TAKEOVER -> TaskExecutionStatus.TAKEOVER_PENDING;
      case CANCEL -> TaskExecutionStatus.CANCELLING;
    };
  }

  private static void requireKey(String key) {
    if (!nonBlank(key) || key.length() > 160) throw ApiException.badRequest("请提供长度不超过 160 的 Idempotency-Key");
  }

  private static String normalizedReason(TaskExecutionControlAction action, String value) {
    String result = value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
    if (action == TaskExecutionControlAction.TAKEOVER && result.isBlank()) throw ApiException.badRequest("人工接管必须提供原因");
    if (result.length() > 500) throw ApiException.badRequest("人工接管原因不能超过 500 个字符");
    return result.isBlank() ? null : result;
  }

  private static String fingerprint(String taskId, String executionId, String robotId, String deploymentId, TaskExecutionControlAction action, String reason) {
    String content = String.join("|", taskId, executionId, robotId, deploymentId, action.name(), reason == null ? "" : reason);
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte value : bytes) out.append(String.format("%02x", value));
      return out.toString();
    } catch (Exception ex) { throw new IllegalStateException("无法计算控制请求指纹", ex); }
  }

  private static String displayName(UserEntity user) { return nonBlank(user.getDisplayName()) ? user.getDisplayName() : user.getUsername(); }
  private static boolean recent(String value, Instant now) {
    try { return Instant.parse(value).plusSeconds(2).isAfter(now); } catch (Exception ignored) { return false; }
  }
  private static boolean same(Object actual, String expected) { return Objects.equals(actual == null ? "" : String.valueOf(actual).trim(), expected); }
  private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
  private static String safeCode(String value) {
    String result = nonBlank(value) ? value.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase() : "CONTROL_ERROR";
    return result.substring(0, Math.min(64, result.length()));
  }
  private static String safeMessage(String value) {
    String result = nonBlank(value) ? value.replaceAll("[\\r\\n]+", " ").trim() : "控制命令未被确认";
    result = result.replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[REDACTED]");
    result = result.replaceAll("(?i)(authorization|token|password)=[^\\s,;]+", "$1=[REDACTED]");
    return result.substring(0, Math.min(500, result.length()));
  }

  public record ControlCommand(String id, String executionId, String action, String robotId, String requestId) {
    public Map<String, Object> bridgePayload() {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("robotId", robotId);
      body.put("requestId", requestId);
      return body;
    }
  }
}
