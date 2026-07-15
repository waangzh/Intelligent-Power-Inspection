package com.powerinspection.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.robot.RobotConnectionStatus;
import com.powerinspection.robot.RobotHeartbeatService;
import com.powerinspection.robot.RobotHeartbeatStatusView;
import com.powerinspection.robot.RobotProperties;
import com.powerinspection.route.RouteDeploymentEntity;
import com.powerinspection.route.RouteDeploymentRepository;
import com.powerinspection.route.RouteDeploymentState;
import com.powerinspection.route.RouteRevisionEntity;
import com.powerinspection.route.RouteRevisionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 真实路线执行的本地意图、资格核验和保守断联恢复。远端结果状态只能由事件摄取服务改变。 */
@Service
public class TaskExecutionLifecycleService {
  private final TaskExecutionRepository executions;
  private final RouteRevisionRepository revisions;
  private final RouteDeploymentRepository deployments;
  private final RobotHeartbeatService heartbeatService;
  private final DataStoreService dataStore;
  private final ObjectMapper objectMapper;
  private final RobotProperties robotProperties;

  public TaskExecutionLifecycleService(TaskExecutionRepository executions, RouteRevisionRepository revisions,
      RouteDeploymentRepository deployments, RobotHeartbeatService heartbeatService, DataStoreService dataStore,
      ObjectMapper objectMapper, RobotProperties robotProperties) {
    this.executions = executions;
    this.revisions = revisions;
    this.deployments = deployments;
    this.heartbeatService = heartbeatService;
    this.dataStore = dataStore;
    this.objectMapper = objectMapper;
    this.robotProperties = robotProperties;
  }

  @Transactional
  public Map<String, Object> start(String taskId, String idempotencyKey) {
    requireKey(idempotencyKey);
    TaskExecutionEntity execution = requireExecutionForStart(taskId);
    String fingerprint = fingerprint(taskId, execution);
    TaskExecutionEntity sameKey = executions.findByStartRequestId(idempotencyKey).orElse(null);
    if (sameKey != null) {
      if (!sameKey.getTaskId().equals(taskId) || !fingerprint.equals(sameKey.getStartRequestFingerprint())) {
        throw ApiException.conflict("Idempotency-Key 已用于不同的启动请求");
      }
      return detail(sameKey);
    }
    if (!TaskExecutionStatus.CREATED.name().equals(execution.getStatus())) {
      throw ApiException.conflict("当前执行不允许再次启动");
    }
    StartContext context = requireStartContext(taskId, execution);
    String now = Instant.now().toString();
    execution.setDeploymentId(context.deployment().getId());
    execution.setExecutorRouteId(context.executorRouteId());
    execution.setStartRequestId(idempotencyKey);
    execution.setStartRequestFingerprint(fingerprint);
    execution.setStatus(TaskExecutionStatus.STARTING.name());
    execution.setLastErrorCode(null);
    execution.setLastErrorMessage(null);
    execution.setManualReconciliationRequired(false);
    execution.setUpdatedAt(now);
    executions.save(execution);
    return detail(execution);
  }

  public Map<String, Object> eligibility(String taskId) {
    TaskExecutionEntity execution = requireExecution(taskId);
    Map<String, Object> result = detail(execution);
    try {
      StartContext context = requireStartContext(taskId, execution);
      result.put("eligible", TaskExecutionStatus.CREATED.name().equals(execution.getStatus()));
      result.put("ineligibleReason", TaskExecutionStatus.CREATED.name().equals(execution.getStatus()) ? null : "执行已不处于 CREATED 状态");
      result.put("deploymentId", context.deployment().getId());
      result.put("executorRouteId", context.executorRouteId());
      return result;
    } catch (ApiException ex) {
      result.put("eligible", false);
      result.put("ineligibleReason", ex.getMessage());
      return result;
    }
  }

  public Map<String, Object> detail(String taskId) { return detail(requireExecution(taskId)); }

  public List<String> nonTerminalExecutionIds() {
    return executions.findByStatusIn(TaskExecutionStatus.NON_TERMINAL).stream().map(TaskExecutionEntity::getExecutionId).toList();
  }

  public TaskExecutionEntity findByExecutionId(String executionId) {
    return executions.findByExecutionId(executionId).orElse(null);
  }

  /** 事务内仅领取一次投递资格，网络调用由 worker 在事务外完成。 */
  @Transactional
  public StartCommand claimStartAttempt(String executionId, Instant now) {
    TaskExecutionEntity execution = findByExecutionId(executionId);
    if (execution == null || !TaskExecutionStatus.STARTING.name().equals(execution.getStatus()) || nonBlank(execution.getStartCommandId())) return null;
    if (recent(execution.getLastStartAttemptAt(), now)) return null;
    execution.setStartAttemptNo(execution.getStartAttemptNo() + 1);
    execution.setLastStartAttemptAt(now.toString());
    execution.setUpdatedAt(now.toString());
    executions.save(execution);
    return new StartCommand(execution.getTaskId(), execution.getExecutionId(), execution.getRobotId(), execution.getDeploymentId(),
      execution.getExecutorRouteId(), execution.getStartRequestId());
  }

  @Transactional
  public void accepted(String executionId, String commandId, Instant now) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (!TaskExecutionStatus.STARTING.name().equals(execution.getStatus())) return;
    if (!nonBlank(commandId)) {
      startFailed(execution, "INVALID_BRIDGE_PAYLOAD", "Bridge 启动回执缺少 commandId", now, false);
      executions.save(execution);
      return;
    }
    execution.setStartCommandId(commandId);
    execution.setUpdatedAt(now.toString());
    executions.save(execution);
  }

  @Transactional
  public void startFailed(String executionId, String code, String message, Instant now) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (TaskExecutionStatus.STARTING.name().equals(execution.getStatus())) startFailed(execution, code, message, now, false);
  }

  @Transactional
  public void disconnected(String executionId, String code, String message, Instant now) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (TaskExecutionStatus.TERMINAL.contains(execution.getStatus())) return;
    if (!TaskExecutionStatus.DISCONNECTED.name().equals(execution.getStatus())) {
      execution.setRecoveryStatus(TaskExecutionStatus.RECOVERING.name().equals(execution.getStatus()) ? execution.getRecoveryStatus() : execution.getStatus());
      execution.setStatus(TaskExecutionStatus.DISCONNECTED.name());
    }
    execution.setLastErrorCode(safeCode(code));
    execution.setLastErrorMessage(safeMessage(message));
    execution.setUpdatedAt(now.toString());
    executions.save(execution);
  }

  @Transactional
  public TaskExecutionEntity beginRecovery(String executionId, Instant now) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (!TaskExecutionStatus.DISCONNECTED.name().equals(execution.getStatus())) return execution;
    execution.setStatus(TaskExecutionStatus.RECOVERING.name());
    execution.setUpdatedAt(now.toString());
    return executions.save(execution);
  }

  @Transactional
  public void completeRecovery(String executionId, Instant now) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (!TaskExecutionStatus.RECOVERING.name().equals(execution.getStatus())) return;
    String previous = execution.getRecoveryStatus();
    if (!TaskExecutionStatus.STARTING.name().equals(previous) && !TaskExecutionStatus.RUNNING.name().equals(previous)) {
      previous = TaskExecutionStatus.STARTING.name();
    }
    execution.setStatus(previous);
    execution.setRecoveryStatus(null);
    execution.setUpdatedAt(now.toString());
    executions.save(execution);
  }

  @Transactional
  public void recordCommandFromVerifiedEvent(String executionId, String commandId) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (!nonBlank(execution.getStartCommandId()) && nonBlank(commandId)) {
      execution.setStartCommandId(commandId);
      execution.setUpdatedAt(Instant.now().toString());
      executions.save(execution);
    }
  }

  public Map<String, Object> detail(TaskExecutionEntity item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("taskId", item.getTaskId());
    result.put("executionId", item.getExecutionId());
    result.put("routeRevisionId", item.getRouteRevisionId());
    result.put("robotId", item.getRobotId());
    result.put("deploymentId", item.getDeploymentId());
    result.put("executorRouteId", item.getExecutorRouteId());
    result.put("routeContentSha256", item.getRouteContentSha256());
    result.put("mapImageSha256", item.getMapImageSha256());
    result.put("status", item.getStatus());
    result.put("currentTargetId", item.getCurrentTargetId());
    result.put("progress", item.getProgress());
    result.put("lastRobotSequence", item.getLastRobotSequence());
    result.put("lastEventAt", item.getLastEventAt());
    result.put("lastErrorCode", item.getLastErrorCode());
    result.put("lastErrorMessage", item.getLastErrorMessage());
    result.put("manualReconciliationRequired", item.isManualReconciliationRequired());
    result.put("createdAt", item.getCreatedAt());
    result.put("updatedAt", item.getUpdatedAt());
    return result;
  }

  private StartContext requireStartContext(String taskId, TaskExecutionEntity execution) {
    if (!robotProperties.isBridgeMode()) throw ApiException.conflict("当前环境未启用 Bridge 执行模式");
    Map<String, Object> task = dataStore.get(DataCategory.TASK, taskId);
    if (!equals(task.get("routeRevisionId"), execution.getRouteRevisionId()) || !equals(task.get("robotId"), execution.getRobotId())
        || !equals(task.get("executionId"), execution.getExecutionId()) || !equals(task.get("routeContentSha256"), execution.getRouteContentSha256())
        || !equals(task.get("mapImageSha256"), execution.getMapImageSha256())) {
      throw ApiException.conflict("任务绑定身份与不可变执行快照不一致");
    }
    RouteRevisionEntity revision = revisions.findById(execution.getRouteRevisionId())
      .orElseThrow(() -> ApiException.conflict("任务绑定的路线修订不存在"));
    if (!equals(revision.getContentSha256(), execution.getRouteContentSha256()) || !equals(revision.getMapImageSha256(), execution.getMapImageSha256())) {
      throw ApiException.conflict("路线修订哈希与执行快照不一致");
    }
    RobotHeartbeatStatusView heartbeat = heartbeatService.detail(execution.getRobotId());
    if (!heartbeat.source().bridgeConfigured()) throw ApiException.conflict("机器人未在 Bridge 配置");
    if (!heartbeat.online() || !RobotConnectionStatus.CONNECTED.name().equals(heartbeat.connectionStatus())) {
      throw ApiException.conflict("机器人离线或 Bridge 当前不可达");
    }
    RouteDeploymentEntity deployment = matchingReadyDeployment(execution, revision);
    return new StartContext(deployment, executorRouteId(revision));
  }

  private RouteDeploymentEntity matchingReadyDeployment(TaskExecutionEntity execution, RouteRevisionEntity revision) {
    List<RouteDeploymentEntity> candidates = deployments.findByRobotIdAndRouteRevisionIdAndStateOrderByCreatedAtDesc(
      execution.getRobotId(), execution.getRouteRevisionId(), RouteDeploymentState.READY_FOR_ROBOT.name());
    for (RouteDeploymentEntity deployment : candidates) {
      if (summaryMatches(deployment, execution, revision)) return deployment;
    }
    throw ApiException.conflict("不存在身份与路线/地图哈希均一致的 READY_FOR_ROBOT 部署");
  }

  private boolean summaryMatches(RouteDeploymentEntity deployment, TaskExecutionEntity execution, RouteRevisionEntity revision) {
    try {
      if (!equals(deployment.getRobotId(), execution.getRobotId()) || !equals(deployment.getRouteRevisionId(), execution.getRouteRevisionId())
          || !RouteDeploymentState.READY_FOR_ROBOT.name().equals(deployment.getState()) || !nonBlank(deployment.getRemoteSummaryJson())) return false;
      Map<String, Object> summary = objectMapper.readValue(deployment.getRemoteSummaryJson(), new TypeReference<Map<String, Object>>() {});
      return equals(summary.get("state"), RouteDeploymentState.READY_FOR_ROBOT.name())
        && equals(summary.get("deploymentId"), deployment.getId())
        && equals(summary.get("robotId"), execution.getRobotId())
        && equals(summary.get("routeRevisionId"), revision.getId())
        && equals(summary.get("routeRevisionContentSha256"), execution.getRouteContentSha256())
        && equals(summary.get("routePayloadSha256"), execution.getRouteContentSha256())
        && equals(summary.get("routeContentSha256"), execution.getRouteContentSha256())
        && equals(summary.get("mapAssetId"), revision.getMapAssetId())
        && equals(summary.get("mapImageSha256"), execution.getMapImageSha256());
    } catch (Exception ignored) { return false; }
  }

  private String executorRouteId(RouteRevisionEntity revision) {
    try {
      Map<String, Object> document = objectMapper.readValue(revision.getExecutorJson(), new TypeReference<Map<String, Object>>() {});
      String activeRouteId = text(document.get("active_route_id"));
      if (!nonBlank(activeRouteId)) throw ApiException.conflict("路线修订缺少 active_route_id，不能启动");
      Object routes = document.get("routes");
      if (!(routes instanceof List<?> values) || values.stream().noneMatch(value -> value instanceof Map<?, ?> route && activeRouteId.equals(text(route.get("id"))))) {
        throw ApiException.conflict("路线修订的 active_route_id 未指向有效路线");
      }
      return activeRouteId;
    } catch (ApiException ex) { throw ex; }
    catch (Exception ex) { throw ApiException.conflict("路线修订执行内容无效，不能启动"); }
  }

  private TaskExecutionEntity requireExecution(String taskId) {
    return executions.findById(taskId).orElseThrow(() -> ApiException.conflict("任务未绑定不可变路线修订执行快照"));
  }

  private TaskExecutionEntity requireExecutionForStart(String taskId) {
    return executions.findByTaskIdForStart(taskId)
      .orElseThrow(() -> ApiException.conflict("任务未绑定不可变路线修订执行快照"));
  }

  private TaskExecutionEntity requireByExecutionId(String executionId) {
    return executions.findByExecutionId(executionId).orElseThrow(() -> ApiException.notFound("执行实例不存在"));
  }

  private String fingerprint(String taskId, TaskExecutionEntity execution) {
    String content = String.join("|", taskId, execution.getExecutionId(), execution.getRobotId(), execution.getRouteRevisionId(),
      execution.getRouteContentSha256(), execution.getMapImageSha256());
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
      StringBuilder out = new StringBuilder();
      for (byte value : bytes) out.append(String.format("%02x", value));
      return out.toString();
    } catch (Exception ex) { throw new IllegalStateException("无法计算启动请求指纹", ex); }
  }

  private static void requireKey(String key) {
    if (!nonBlank(key) || key.length() > 160) throw ApiException.badRequest("请提供长度不超过 160 的 Idempotency-Key");
  }
  private static boolean recent(String value, Instant now) {
    try { return Instant.parse(value).plusSeconds(2).isAfter(now); } catch (Exception ignored) { return false; }
  }
  private static void startFailed(TaskExecutionEntity item, String code, String message, Instant now, boolean manual) {
    item.setStatus(TaskExecutionStatus.START_FAILED.name());
    item.setLastErrorCode(safeCode(code));
    item.setLastErrorMessage(safeMessage(message));
    item.setManualReconciliationRequired(manual);
    item.setUpdatedAt(now.toString());
  }
  private static String safeCode(String value) {
    String result = value == null ? "EXECUTION_ERROR" : value.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase();
    return result.substring(0, Math.min(64, result.length()));
  }
  private static String safeMessage(String value) {
    String result = value == null ? "执行状态未知" : value.replaceAll("[\\r\\n]+", " ").trim();
    result = result.replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[REDACTED]");
    result = result.replaceAll("(?i)(authorization|token|password)=[^\\s,;]+", "$1=[REDACTED]");
    return result.substring(0, Math.min(500, result.length()));
  }
  private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
  private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
  private static boolean equals(Object actual, String expected) { return Objects.equals(text(actual), expected); }

  private record StartContext(RouteDeploymentEntity deployment, String executorRouteId) {}
  public record StartCommand(String taskId, String executionId, String robotId, String deploymentId, String executorRouteId, String requestId) {
    public Map<String, Object> bridgePayload() {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("taskId", taskId);
      payload.put("robotId", robotId);
      payload.put("deploymentId", deploymentId);
      payload.put("executionId", executionId);
      payload.put("requestId", requestId);
      payload.put("executorRouteId", executorRouteId);
      return payload;
    }
  }
}
