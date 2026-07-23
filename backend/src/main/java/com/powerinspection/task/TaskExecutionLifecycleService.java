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
import com.powerinspection.notification.NotificationService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 真实路线执行的本地意图、资格核验和保守断联恢复。远端结果状态只能由事件摄取服务改变。 */
@Service
public class TaskExecutionLifecycleService {
  private static final Logger log = LoggerFactory.getLogger(TaskExecutionLifecycleService.class);
  private final TaskExecutionRepository executions;
  private final TaskExecutionControlCommandRepository controlCommands;
  private final RouteRevisionRepository revisions;
  private final RouteDeploymentRepository deployments;
  private final RobotHeartbeatService heartbeatService;
  private final DataStoreService dataStore;
  private final ObjectMapper objectMapper;
  private final RobotProperties robotProperties;
  private final NotificationService notificationService;

  @org.springframework.beans.factory.annotation.Autowired
  public TaskExecutionLifecycleService(TaskExecutionRepository executions, TaskExecutionControlCommandRepository controlCommands, RouteRevisionRepository revisions,
      RouteDeploymentRepository deployments, RobotHeartbeatService heartbeatService, DataStoreService dataStore,
      ObjectMapper objectMapper, RobotProperties robotProperties, NotificationService notificationService) {
    this.executions = executions;
    this.controlCommands = controlCommands;
    this.revisions = revisions;
    this.deployments = deployments;
    this.heartbeatService = heartbeatService;
    this.dataStore = dataStore;
    this.objectMapper = objectMapper;
    this.robotProperties = robotProperties;
    this.notificationService = notificationService;
  }

  public TaskExecutionLifecycleService(TaskExecutionRepository executions, TaskExecutionControlCommandRepository controlCommands, RouteRevisionRepository revisions,
      RouteDeploymentRepository deployments, RobotHeartbeatService heartbeatService, DataStoreService dataStore,
      ObjectMapper objectMapper, RobotProperties robotProperties) {
    this(executions, controlCommands, revisions, deployments, heartbeatService, dataStore, objectMapper, robotProperties, null);
  }

  @Transactional
  public Map<String, Object> start(String taskId, String idempotencyKey, TaskStartMode requestedMode, String operatorId) {
    TaskStartMode startMode = TaskStartMode.defaulted(requestedMode);
    requireKey(idempotencyKey);
    TaskExecutionEntity execution = requireExecutionForStart(taskId);
    log.info("event=task_start_requested taskId={} robotId={} executionId={} startMode={} operatorId={}",
      taskId, execution.getRobotId(), execution.getExecutionId(), startMode, operatorId);
    String fingerprint = fingerprint(taskId, execution, startMode);
    TaskExecutionEntity sameKey = executions.findByStartRequestId(idempotencyKey).orElse(null);
    if (sameKey != null) {
      if (!sameKey.getTaskId().equals(taskId) || !fingerprint.equals(sameKey.getStartRequestFingerprint())) {
        throw ApiException.conflict("Idempotency-Key 已用于不同的启动请求");
      }
      return detail(sameKey);
    }
    if (execution.isManualReconciliationRequired()) {
      throw ApiException.conflict("当前执行存在未核对的机器人事件，禁止再次启动；请保留审计记录并创建新的执行任务");
    }
    if (!startable(execution)) {
      throw ApiException.conflict("当前执行不允许再次启动");
    }
    StartContext context = requireStartContext(taskId, execution, startMode);
    String now = Instant.now().toString();
    execution.setDeploymentId(context.deployment().getId());
    execution.setExecutorRouteId(context.executorRouteId());
    execution.setStartRequestId(idempotencyKey);
    execution.setStartRequestFingerprint(fingerprint);
    execution.setStartCommandId(null);
    execution.setStartMode(startMode.name());
    execution.setOperatorId(operatorId);
    execution.setStartRequestedAt(now);
    execution.setRobotReadyAt(null);
    execution.setLocalConfirmedAt(null);
    execution.setStartedAt(null);
    execution.setLastStartAttemptAt(null);
    execution.setStatus(TaskExecutionStatus.STARTING.name());
    execution.setLastErrorCode(null);
    execution.setLastErrorMessage(null);
    execution.setManualReconciliationRequired(false);
    execution.setUpdatedAt(now);
    executions.save(execution);
    notifyTransition(execution, "TASK_EXECUTION_STARTING", "任务启动请求已受理", "任务 " + taskId + " 已进入启动中状态。", execution.getStartRequestId());
    log.info("event=task_start_accepted taskId={} robotId={} executionId={} deploymentId={} requestId={} startMode={} operatorId={} executionStatus={}",
      taskId, execution.getRobotId(), execution.getExecutionId(), execution.getDeploymentId(), idempotencyKey,
      startMode, operatorId, execution.getStatus());
    return detail(execution);
  }

  public Map<String, Object> eligibility(String taskId) {
    TaskExecutionEntity execution = requireExecution(taskId);
    Map<String, Object> result = detail(execution);
    Map<String, Object> robot = dataStore.get(DataCategory.ROBOT, execution.getRobotId());
    RobotHeartbeatStatusView heartbeat = heartbeatService.detail(execution.getRobotId());
    boolean reportedRemote = heartbeat.reportedSupportsRemoteImmediateStart();
    boolean reportedLocal = heartbeat.reportedSupportsLocalConfirmStart();
    boolean localEnabled = supports(robot, "localConfirmStartEnabled", false);
    boolean supportsLocal = reportedLocal && localEnabled && heartbeat.localConfirmProtocolCompatible();
    boolean supportsRemote = reportedRemote;
    result.put("reportedSupportsRemoteImmediateStart", reportedRemote);
    result.put("reportedSupportsLocalConfirmStart", reportedLocal);
    result.put("localConfirmStartEnabled", localEnabled);
    result.put("localConfirmProtocolVersion", heartbeat.localConfirmProtocolVersion());
    result.put("localConfirmProtocolCompatible", heartbeat.localConfirmProtocolCompatible());
    result.put("localConfirmStartReady", heartbeat.localConfirmStartReady());
    result.put("localConfirmStartError", heartbeat.localConfirmStartError());
    result.put("capabilityReportedAt", heartbeat.capabilityReportedAt());
    result.put("supportsRemoteImmediateStart", supportsRemote);
    result.put("supportsLocalConfirmStart", supportsLocal);
    if (execution.isManualReconciliationRequired()) {
      result.put("eligible", false);
      result.put("ineligibleReason", "存在未核对的机器人事件，禁止再次启动；请保留审计记录并创建新的执行任务");
      result.put("remoteImmediateStartEligible", false);
      result.put("remoteImmediateStartIneligibleReason", result.get("ineligibleReason"));
      result.put("localConfirmStartEligible", false);
      result.put("localConfirmStartIneligibleReason", result.get("ineligibleReason"));
      return result;
    }
    ModeEligibility remote = modeEligibility(taskId, execution, TaskStartMode.REMOTE_IMMEDIATE);
    ModeEligibility local = modeEligibility(taskId, execution, TaskStartMode.LOCAL_CONFIRM);
    result.put("remoteImmediateStartEligible", remote.eligible());
    result.put("remoteImmediateStartIneligibleReason", remote.reason());
    result.put("localConfirmStartEligible", local.eligible());
    result.put("localConfirmStartIneligibleReason", local.reason());
    result.put("eligible", remote.eligible() || local.eligible());
    result.put("ineligibleReason", remote.eligible() || local.eligible() ? null : remote.reason());
    StartContext context = remote.context() != null ? remote.context() : local.context();
    if (context != null) {
      result.put("deploymentId", context.deployment().getId());
      result.put("executorRouteId", context.executorRouteId());
    }
    return result;
  }

  public Map<String, Object> detail(String taskId) { return detail(requireExecution(taskId)); }

  public boolean hasExecution(String taskId) { return executions.existsById(taskId); }

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
    if (execution == null || !startDeliveryRequired(execution) || nonBlank(execution.getStartCommandId())) return null;
    if (recent(execution.getLastStartAttemptAt(), now)) return null;
    TaskStartMode startMode = TaskStartMode.defaulted(parseStartMode(execution.getStartMode()));
    if (TaskExecutionStatus.STARTING.name().equals(execution.getStatus())) {
      try {
        requirePersistedStartContext(execution, startMode);
      } catch (ApiException ex) {
        String code = eligibilityErrorCode(ex.getMessage());
        startFailed(execution, code, ex.getMessage(), now, false);
        executions.save(execution);
        log.warn("event=task_start_recheck_failed taskId={} robotId={} executionId={} deploymentId={} requestId={} startMode={} errorCode={}",
          execution.getTaskId(), execution.getRobotId(), executionId, execution.getDeploymentId(),
          execution.getStartRequestId(), startMode, code);
        return null;
      }
    }
    execution.setStartAttemptNo(execution.getStartAttemptNo() + 1);
    execution.setLastStartAttemptAt(now.toString());
    execution.setUpdatedAt(now.toString());
    executions.save(execution);
    return new StartCommand(execution.getTaskId(), execution.getExecutionId(), execution.getRobotId(), execution.getDeploymentId(),
      execution.getExecutorRouteId(), execution.getStartRequestId(), startMode);
  }

  @Transactional
  public void accepted(String executionId, String commandId, Instant now) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (!startDeliveryRequired(execution)) return;
    if (!nonBlank(commandId)) {
      startFailed(execution, "INVALID_BRIDGE_PAYLOAD", "Bridge 启动回执缺少 commandId", now, false);
      executions.save(execution);
      return;
    }
    execution.setStartCommandId(commandId);
    execution.setUpdatedAt(now.toString());
    executions.save(execution);
    notifyTransition(execution, "TASK_EXECUTION_DISPATCHED", "任务启动命令已下发", "任务 " + execution.getTaskId() + " 的启动命令已下发至机器人。", commandId);
    log.info("event=robot_start_command_dispatched taskId={} robotId={} executionId={} deploymentId={} requestId={} commandId={} startMode={} executionStatus={}",
      execution.getTaskId(), execution.getRobotId(), executionId, execution.getDeploymentId(),
      execution.getStartRequestId(), commandId, execution.getStartMode(), execution.getStatus());
  }

  @Transactional
  public boolean timeoutIfNeeded(String executionId, Instant now, long startCommandTimeoutSeconds,
      long routeStartTimeoutSeconds, long localConfirmTimeoutSeconds) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    String code;
    String message;
    if (TaskExecutionStatus.STARTING.name().equals(execution.getStatus())) {
      if (!expired(execution.getStartRequestedAt(), now,
          nonBlank(execution.getStartCommandId()) ? routeStartTimeoutSeconds : startCommandTimeoutSeconds)) return false;
      code = nonBlank(execution.getStartCommandId()) ? "ROUTE_START_TIMEOUT" : "START_COMMAND_TIMEOUT";
      message = nonBlank(execution.getStartCommandId()) ? "等待机器人 route_started 事件超时" : "启动命令下发超时";
      execution.setStatus(TaskExecutionStatus.START_FAILED.name());
    } else if (TaskExecutionStatus.WAITING_LOCAL_CONFIRM.name().equals(execution.getStatus())) {
      if (!expired(execution.getRobotReadyAt(), now, localConfirmTimeoutSeconds)) return false;
      code = "LOCAL_CONFIRM_TIMEOUT";
      message = "等待机器人本地确认启动超时";
      execution.setStatus(TaskExecutionStatus.FAILED.name());
    } else {
      return false;
    }
    execution.setLastErrorCode(code);
    execution.setLastErrorMessage(message);
    execution.setUpdatedAt(now.toString());
    executions.save(execution);
    log.warn("event=task_execution_timeout taskId={} robotId={} executionId={} deploymentId={} requestId={} startMode={} executionStatus={} errorCode={}",
      execution.getTaskId(), execution.getRobotId(), executionId, execution.getDeploymentId(),
      execution.getStartRequestId(), execution.getStartMode(), execution.getStatus(), code);
    notifyTransition(execution, "TASK_EXECUTION_" + execution.getStatus(), "任务执行超时", "任务 " + execution.getTaskId() + " 执行超时：" + message, execution.getStartRequestId());
    return true;
  }

  @Transactional
  public void startFailed(String executionId, String code, String message, Instant now) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (startDeliveryRequired(execution)) startFailed(execution, code, message, now, false);
  }

  @Transactional
  public void disconnected(String executionId, String code, String message, Instant now) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (TaskExecutionStatus.TERMINAL.contains(execution.getStatus())) return;
    String previousStatus = execution.getStatus();
    if (!TaskExecutionStatus.DISCONNECTED.name().equals(execution.getStatus())) {
      execution.setRecoveryStatus(TaskExecutionStatus.RECOVERING.name().equals(execution.getStatus()) ? execution.getRecoveryStatus() : execution.getStatus());
      execution.setStatus(TaskExecutionStatus.DISCONNECTED.name());
    }
    execution.setLastErrorCode(safeCode(code));
    execution.setLastErrorMessage(safeMessage(message));
    execution.setUpdatedAt(now.toString());
    executions.save(execution);
    if (!TaskExecutionStatus.DISCONNECTED.name().equals(previousStatus)) {
      notifyTransition(execution, "TASK_EXECUTION_DISCONNECTED", "任务执行连接中断", "任务 " + execution.getTaskId() + " 与机器人连接中断。", execution.getUpdatedAt());
    }
    controlCommands.findFirstByExecutionIdAndStatusInOrderByRequestedAtDesc(executionId, List.of(
      TaskExecutionControlCommandStatus.PENDING_SEND.name(), TaskExecutionControlCommandStatus.SENDING.name(),
      TaskExecutionControlCommandStatus.QUEUED.name(), TaskExecutionControlCommandStatus.ACKED.name(),
      TaskExecutionControlCommandStatus.RECONCILING.name())).ifPresent(command -> {
        command.setStatus(TaskExecutionControlCommandStatus.RECONCILING.name());
        command.setRecoveryAction("RECONCILE_BEFORE_RETRYING_SAME_REQUEST_ID");
        command.setResultCode(safeCode(code));
        command.setResultMessage(safeMessage(message));
        command.setUpdatedAt(now.toString());
        controlCommands.save(command);
      });
  }

  @Transactional
  public TaskExecutionEntity beginRecovery(String executionId, Instant now) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (!TaskExecutionStatus.DISCONNECTED.name().equals(execution.getStatus())) return execution;
    execution.setStatus(TaskExecutionStatus.RECOVERING.name());
    execution.setUpdatedAt(now.toString());
    executions.save(execution);
    notifyTransition(execution, "TASK_EXECUTION_RECOVERING", "任务执行正在恢复", "任务 " + execution.getTaskId() + " 正在尝试恢复执行。", execution.getUpdatedAt());
    return execution;
  }

  @Transactional
  public void completeRecovery(String executionId, Instant now) {
    TaskExecutionEntity execution = requireByExecutionId(executionId);
    if (!TaskExecutionStatus.RECOVERING.name().equals(execution.getStatus())) return;
    String previous = execution.getRecoveryStatus();
    if (!List.of(TaskExecutionStatus.STARTING.name(), TaskExecutionStatus.WAITING_LOCAL_CONFIRM.name(), TaskExecutionStatus.RUNNING.name(), TaskExecutionStatus.PAUSING.name(),
        TaskExecutionStatus.PAUSED.name(), TaskExecutionStatus.RESUMING.name(), TaskExecutionStatus.CANCELLING.name(),
        TaskExecutionStatus.ESTOPPING.name(), TaskExecutionStatus.TAKEOVER_PENDING.name(),
        TaskExecutionStatus.MANUAL_TAKEOVER.name()).contains(previous)) {
      previous = TaskExecutionStatus.STARTING.name();
    }
    execution.setStatus(previous);
    execution.setRecoveryStatus(null);
    execution.setUpdatedAt(now.toString());
    executions.save(execution);
    notifyTransition(execution, "TASK_EXECUTION_RECOVERED", "任务执行已恢复", "任务 " + execution.getTaskId() + " 已恢复到 " + previous + " 状态。", execution.getUpdatedAt());
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
    result.put("startMode", TaskStartMode.defaulted(parseStartMode(item.getStartMode())).name());
    result.put("operatorId", item.getOperatorId());
    result.put("startRequestId", item.getStartRequestId());
    result.put("startCommandId", item.getStartCommandId());
    result.put("startRequestedAt", item.getStartRequestedAt());
    result.put("robotReadyAt", item.getRobotReadyAt());
    result.put("localConfirmedAt", item.getLocalConfirmedAt());
    result.put("startedAt", item.getStartedAt());
    result.put("currentTargetId", item.getCurrentTargetId());
    result.put("progress", item.getProgress());
    result.put("lastRobotSequence", item.getLastRobotSequence());
    result.put("lastEventAt", item.getLastEventAt());
    result.put("lastErrorCode", item.getLastErrorCode());
    result.put("lastErrorMessage", item.getLastErrorMessage());
    result.put("manualReconciliationRequired", item.isManualReconciliationRequired());
    controlCommands.findFirstByExecutionIdOrderByRequestedAtDesc(item.getExecutionId()).ifPresent(command -> result.put("latestControl", controlDetail(command)));
    result.put("createdAt", item.getCreatedAt());
    result.put("updatedAt", item.getUpdatedAt());
    return result;
  }

  private StartContext requireStartContext(String taskId, TaskExecutionEntity execution, TaskStartMode startMode) {
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
    Map<String, Object> robot = dataStore.get(DataCategory.ROBOT, execution.getRobotId());
    if (heartbeat.lastHeartbeatAt() == null) {
      throw ApiException.conflict("机器人尚未完成首次有效 heartbeat");
    }
    if (startMode == TaskStartMode.LOCAL_CONFIRM) {
      if (!heartbeat.reportedSupportsLocalConfirmStart()) {
        throw ApiException.conflict("ROBOT_START_MODE_UNSUPPORTED：设备未上报支持本地确认启动");
      }
      if (!supports(robot, "localConfirmStartEnabled", false)) {
        throw ApiException.conflict("LOCAL_CONFIRM_POLICY_DISABLED：管理员尚未审批启用本地确认启动");
      }
      if (!heartbeat.localConfirmProtocolCompatible()) {
        throw ApiException.conflict("LOCAL_CONFIRM_PROTOCOL_INCOMPATIBLE：本地确认协议版本不兼容");
      }
    } else if (!heartbeat.reportedSupportsRemoteImmediateStart()) {
      throw ApiException.conflict("ROBOT_START_MODE_UNSUPPORTED：设备未上报支持远程立即启动");
    }
    if (!heartbeat.source().bridgeConfigured()
        || RobotConnectionStatus.BRIDGE_UNCONFIGURED.name().equals(heartbeat.connectionStatus())) {
      throw ApiException.conflict("机器人未在 Bridge 配置");
    }
    if (RobotConnectionStatus.BRIDGE_UNREACHABLE.name().equals(heartbeat.connectionStatus())) {
      throw ApiException.conflict("Bridge 当前不可达");
    }
    if (!heartbeat.online() || !RobotConnectionStatus.CONNECTED.name().equals(heartbeat.connectionStatus())) {
      throw ApiException.conflict("机器人当前离线");
    }
    if (startMode == TaskStartMode.LOCAL_CONFIRM && !heartbeat.localConfirmStartReady()) {
      String suffix = nonBlank(heartbeat.localConfirmStartError())
        ? "（" + heartbeat.localConfirmStartError() + "）" : "";
      throw ApiException.conflict("LOCAL_CONFIRM_NOT_READY：机器人本地确认当前未就绪" + suffix);
    }
    boolean occupied = executions.findByRobotIdAndStatusIn(execution.getRobotId(), TaskExecutionStatus.ACTIVE).stream()
      .anyMatch(item -> !Objects.equals(item.getExecutionId(), execution.getExecutionId()));
    if (occupied) throw ApiException.conflict("机器人已有冲突的活动执行");
    RouteDeploymentEntity deployment = matchingReadyDeployment(execution, revision);
    return new StartContext(deployment, executorRouteId(revision));
  }

  private RouteDeploymentEntity matchingReadyDeployment(TaskExecutionEntity execution, RouteRevisionEntity revision) {
    List<RouteDeploymentEntity> candidates = deployments.findByRobotIdAndRouteRevisionIdAndStateOrderByCreatedAtDesc(
      execution.getRobotId(), execution.getRouteRevisionId(), RouteDeploymentState.READY_FOR_ROBOT.name());
    for (RouteDeploymentEntity deployment : candidates) {
      if (summaryMatches(deployment, execution, revision)) return deployment;
    }
    throw ApiException.conflict("不存在身份与路线/地图哈希均一致的「"
      + RouteDeploymentState.READY_FOR_ROBOT.displayLabel() + "」部署");
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

  private boolean startDeliveryRequired(TaskExecutionEntity execution) {
    if (TaskExecutionStatus.STARTING.name().equals(execution.getStatus())) return true;
    if (!TaskExecutionStatus.CANCELLING.name().equals(execution.getStatus())
        && !TaskExecutionStatus.ESTOPPING.name().equals(execution.getStatus())) {
      return false;
    }
    return controlCommands.findFirstByExecutionIdAndStatusInOrderByRequestedAtDesc(execution.getExecutionId(), List.of(
      TaskExecutionControlCommandStatus.PENDING_SEND.name(), TaskExecutionControlCommandStatus.SENDING.name(),
      TaskExecutionControlCommandStatus.RECONCILING.name())).map(command ->
        (TaskExecutionControlAction.CANCEL.name().equals(command.getAction())
          || TaskExecutionControlAction.ESTOP.name().equals(command.getAction()))
          && TaskExecutionStatus.STARTING.name().equals(command.getPriorExecutionStatus())).orElse(false);
  }

  private static Map<String, Object> controlDetail(TaskExecutionControlCommandEntity command) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("action", command.getAction());
    result.put("requestId", command.getRequestId());
    result.put("status", command.getStatus());
    result.put("commandId", command.getCommandId());
    result.put("takeoverReason", command.getTakeoverReason());
    result.put("requestedBy", command.getRequestedByName());
    result.put("requestedAt", command.getRequestedAt());
    result.put("ackedAt", command.getAckedAt());
    result.put("confirmedAt", command.getConfirmedAt());
    result.put("recoveryAction", command.getRecoveryAction());
    result.put("resultCode", command.getResultCode());
    result.put("resultMessage", command.getResultMessage());
    return result;
  }

  private String fingerprint(String taskId, TaskExecutionEntity execution, TaskStartMode startMode) {
    String content = String.join("|", taskId, execution.getExecutionId(), execution.getRobotId(), execution.getRouteRevisionId(),
      execution.getRouteContentSha256(), execution.getMapImageSha256(), startMode.name());
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

  private void requirePersistedStartContext(TaskExecutionEntity execution, TaskStartMode startMode) {
    requireStartContext(execution.getTaskId(), execution, startMode);
    RouteRevisionEntity revision = revisions.findById(execution.getRouteRevisionId())
      .orElseThrow(() -> ApiException.conflict("任务绑定的路线修订不存在"));
    RouteDeploymentEntity deployment = deployments.findById(execution.getDeploymentId())
      .orElseThrow(() -> ApiException.conflict("启动请求绑定的部署不存在"));
    if (!summaryMatches(deployment, execution, revision)) {
      throw ApiException.conflict("启动请求绑定的部署状态、身份或哈希已变化");
    }
  }
  private static boolean expired(String value, Instant now, long timeoutSeconds) {
    try { return Instant.parse(value).plusSeconds(Math.max(1, timeoutSeconds)).isBefore(now); }
    catch (Exception ignored) { return false; }
  }
  private void startFailed(TaskExecutionEntity item, String code, String message, Instant now, boolean manual) {
    item.setStatus(TaskExecutionStatus.START_FAILED.name());
    item.setLastErrorCode(safeCode(code));
    item.setLastErrorMessage(safeMessage(message));
    item.setManualReconciliationRequired(manual);
    item.setUpdatedAt(now.toString());
    notifyTransition(item, "TASK_EXECUTION_START_FAILED", "任务启动失败", "任务 " + item.getTaskId() + " 启动失败：" + safeMessage(message), item.getStartRequestId());
  }

  private void notifyTransition(TaskExecutionEntity execution, String eventCode, String title, String content, String discriminator) {
    if (notificationService == null) return;
    String key = "task-execution:" + execution.getExecutionId() + ":" + eventCode + ":" + (discriminator == null ? "" : discriminator);
    notificationService.pushEvent("*", "TASK", eventCode, "TASK", execution.getTaskId(), title, content,
        "/tasks/" + execution.getTaskId(), key);
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
  private static boolean startable(TaskExecutionEntity execution) {
    return TaskExecutionStatus.CREATED.name().equals(execution.getStatus())
      || TaskExecutionStatus.START_FAILED.name().equals(execution.getStatus());
  }
  private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
  private static boolean equals(Object actual, String expected) { return Objects.equals(text(actual), expected); }
  private static boolean supports(Map<String, Object> robot, String field, boolean defaultValue) {
    return robot.containsKey(field) ? Boolean.TRUE.equals(robot.get(field)) : defaultValue;
  }
  private static String eligibilityErrorCode(String message) {
    if (message != null) {
      int separator = message.indexOf('：');
      String candidate = separator > 0 ? message.substring(0, separator) : "";
      if (candidate.matches("[A-Z][A-Z0-9_]{2,63}")) return candidate;
    }
    return "START_ELIGIBILITY_CHANGED";
  }

  private ModeEligibility modeEligibility(
      String taskId, TaskExecutionEntity execution, TaskStartMode startMode) {
    if (!startable(execution)) {
      return new ModeEligibility(false, "执行已不处于 CREATED 或 START_FAILED 状态", null);
    }
    try {
      return new ModeEligibility(true, null, requireStartContext(taskId, execution, startMode));
    } catch (ApiException ex) {
      return new ModeEligibility(false, ex.getMessage(), null);
    }
  }
  private static TaskStartMode parseStartMode(String value) {
    try { return TaskStartMode.valueOf(value); } catch (Exception ignored) { return TaskStartMode.REMOTE_IMMEDIATE; }
  }

  private record StartContext(RouteDeploymentEntity deployment, String executorRouteId) {}
  private record ModeEligibility(boolean eligible, String reason, StartContext context) {}
  public record StartCommand(String taskId, String executionId, String robotId, String deploymentId, String executorRouteId,
      String requestId, TaskStartMode startMode) {
    public Map<String, Object> bridgePayload() {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("taskId", taskId);
      payload.put("robotId", robotId);
      payload.put("deploymentId", deploymentId);
      payload.put("executionId", executionId);
      payload.put("requestId", requestId);
      payload.put("executorRouteId", executorRouteId);
      payload.put("startMode", startMode.name());
      return payload;
    }
  }
}
