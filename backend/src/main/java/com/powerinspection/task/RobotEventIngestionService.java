package com.powerinspection.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.record.InspectionRecordService;
import com.powerinspection.common.Ids;
import com.powerinspection.robot.RobotBridgeExecutionEvent;
import com.powerinspection.route.RouteRevisionEntity;
import com.powerinspection.route.RouteRevisionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 唯一允许把机器人事件映射为 RUNNING、COMPLETED、FAILED 等真实执行结果的服务。 */
@Service
public class RobotEventIngestionService {
  private static final Logger log = LoggerFactory.getLogger(RobotEventIngestionService.class);
  private final TaskExecutionRepository executions;
  private final RobotExecutionEventRepository eventRepository;
  private final TaskExecutionControlCommandRepository controlCommands;
  private final RouteRevisionRepository revisions;
  private final ObjectMapper objectMapper;
  private final DataStoreService dataStore;
  private final InspectionRecordService inspectionRecordService;

  public RobotEventIngestionService(TaskExecutionRepository executions, RobotExecutionEventRepository eventRepository,
      TaskExecutionControlCommandRepository controlCommands,
      RouteRevisionRepository revisions, ObjectMapper objectMapper,
      DataStoreService dataStore, InspectionRecordService inspectionRecordService) {
    this.executions = executions;
    this.eventRepository = eventRepository;
    this.controlCommands = controlCommands;
    this.revisions = revisions;
    this.objectMapper = objectMapper;
    this.dataStore = dataStore;
    this.inspectionRecordService = inspectionRecordService;
  }

  @Transactional
  public void ingest(String expectedExecutionId, RobotBridgeExecutionEvent input) {
    String receivedAt = Instant.now().toString();
    String robotId = input.text("robot_id");
    String executionId = input.text("execution_id");
    String deploymentId = input.text("deployment_id");
    String eventType = input.text("event");
    long sequence = input.sequence();
    log.debug("event=robot_event_received robotId={} executionId={} deploymentId={} requestId={} eventType={} sequence={}",
      robotId, executionId, deploymentId, input.text("request_id"), eventType, sequence);
    String eventId = nonBlank(input.text("event_id")) ? input.text("event_id") : robotId + ":" + sequence;
    RobotExecutionEventEntity duplicate = eventRepository.findByRobotIdAndSequence(robotId, sequence)
      .or(() -> eventRepository.findByEventId(eventId)).orElse(null);
    if (duplicate != null) {
      if (!same(duplicate, executionId, deploymentId, eventId, eventType)) {
        duplicate.setProcessingResult("CONFLICT");
        duplicate.setConflictCode("EVENT_SEQUENCE_CONFLICT");
        eventRepository.save(duplicate);
        conflict(expectedExecutionId, "EVENT_SEQUENCE_CONFLICT", "机器人同一事件序列或 eventId 的内容发生冲突");
      } else {
        log.debug("event=duplicate_robot_event_ignored robotId={} executionId={} eventType={} sequence={}",
          robotId, executionId, eventType, sequence);
      }
      return;
    }

    RobotExecutionEventEntity audit = audit(input, robotId, executionId, deploymentId, eventId, eventType, sequence, receivedAt);
    eventRepository.save(audit);
    TaskExecutionEntity execution = executions.findByExecutionId(expectedExecutionId).orElse(null);
    if (execution == null) {
      reject(audit, "EXECUTION_NOT_FOUND", "平台不存在该执行实例");
      return;
    }
    if (!validOwnership(execution, input, robotId, executionId, deploymentId, eventType, sequence)) {
      reject(audit, "EVENT_OWNERSHIP_CONFLICT", "事件缺少协议必填字段，或 robot、execution、deployment、request、command 归属不匹配");
      conflict(execution.getExecutionId(), "EVENT_OWNERSHIP_CONFLICT", "Bridge 事件归属校验失败，平台无法确认机器人实际状态，等待人工对账");
      return;
    }
    if (sequence <= execution.getLastRobotSequence()) {
      audit.setProcessingResult("STALE_SEQUENCE");
      eventRepository.save(audit);
      return;
    }

    apply(execution, input, audit);
    execution.setLastRobotSequence(sequence);
    execution.setLastEventAt(nonBlank(input.text("occurred_at")) ? input.text("occurred_at") : receivedAt);
    execution.setUpdatedAt(receivedAt);
    executions.save(execution);
  }

  @Transactional
  public void bridgeOwnershipConflict(String executionId, String code, String message) {
    conflict(executionId, code, message);
  }

  private void apply(TaskExecutionEntity execution, RobotBridgeExecutionEvent event, RobotExecutionEventEntity audit) {
    String type = event.text("event");
    String status = execution.getStatus();
    if (TaskExecutionStatus.TERMINAL.contains(status)) {
      if ((TaskExecutionStatus.COMPLETED.name().equals(status) && "route_failed".equals(type))
          || ((TaskExecutionStatus.FAILED.name().equals(status) || TaskExecutionStatus.START_FAILED.name().equals(status)) && "route_finished".equals(type))) {
        audit.setProcessingResult("CONFLICT");
        audit.setConflictCode("EVENT_TERMINAL_CONFLICT");
        execution.setManualReconciliationRequired(true);
        execution.setLastErrorCode("EVENT_TERMINAL_CONFLICT");
        execution.setLastErrorMessage("终态执行收到冲突的晚到机器人事件，需要人工对账");
      } else {
        audit.setProcessingResult("TERMINAL_IGNORED");
      }
      eventRepository.save(audit);
      return;
    }

    TaskExecutionControlCommandEntity control = controlFor(execution, event);
    if (control != null) {
      if ("command_accepted".equals(type)) {
        acknowledged(control, audit);
        return;
      }
      if ("command_rejected".equals(type) || "command_failed".equals(type)) {
        failedControl(execution, control, event, audit);
        return;
      }
      if (confirmedControl(execution, control, type, audit)) return;
    }

    boolean starting = TaskExecutionStatus.STARTING.name().equals(status)
      || (TaskExecutionStatus.RECOVERING.name().equals(status) && TaskExecutionStatus.STARTING.name().equals(execution.getRecoveryStatus()));
    boolean waitingLocal = TaskExecutionStatus.WAITING_LOCAL_CONFIRM.name().equals(status)
      || (TaskExecutionStatus.RECOVERING.name().equals(status)
        && TaskExecutionStatus.WAITING_LOCAL_CONFIRM.name().equals(execution.getRecoveryStatus()));
    boolean running = TaskExecutionStatus.RUNNING.name().equals(status)
      || (TaskExecutionStatus.RECOVERING.name().equals(status) && TaskExecutionStatus.RUNNING.name().equals(execution.getRecoveryStatus()));
    String occurredAt = event.text("occurred_at");
    if ("start_waiting_local_confirmation".equals(type) && starting
        && TaskStartMode.LOCAL_CONFIRM.name().equals(execution.getStartMode())) {
      execution.setStatus(TaskExecutionStatus.WAITING_LOCAL_CONFIRM.name());
      execution.setRecoveryStatus(null);
      execution.setRobotReadyAt(occurredAt);
      audit.setProcessingResult("APPLIED");
      log.info("event=robot_waiting_local_confirmation taskId={} robotId={} executionId={} deploymentId={} requestId={} startMode={} executionStatus={}",
        execution.getTaskId(), execution.getRobotId(), execution.getExecutionId(), execution.getDeploymentId(),
        execution.getStartRequestId(), execution.getStartMode(), execution.getStatus());
    } else if ("local_start_confirmed".equals(type) && waitingLocal) {
      execution.setLocalConfirmedAt(occurredAt);
      audit.setProcessingResult("APPLIED");
    } else if ("route_started".equals(type) && (starting || waitingLocal)) {
      execution.setStatus(TaskExecutionStatus.RUNNING.name());
      execution.setRecoveryStatus(null);
      execution.setStartedAt(occurredAt);
      if (TaskStartMode.LOCAL_CONFIRM.name().equals(execution.getStartMode())
          && !nonBlank(execution.getLocalConfirmedAt())) {
        execution.setLocalConfirmedAt(occurredAt);
      }
      audit.setProcessingResult("APPLIED");
      log.info("event=robot_route_started taskId={} robotId={} executionId={} deploymentId={} requestId={} startMode={} executionStatus={}",
        execution.getTaskId(), execution.getRobotId(), execution.getExecutionId(), execution.getDeploymentId(),
        execution.getStartRequestId(), execution.getStartMode(), execution.getStatus());
    } else if ("target_reached".equals(type) && running) {
      updateProgress(execution, event.payload());
      audit.setProcessingResult("APPLIED");
    } else if ("route_finished".equals(type) && running) {
      execution.setStatus(TaskExecutionStatus.COMPLETED.name());
      execution.setRecoveryStatus(null);
      execution.setProgress(100);
      completeTask(execution, occurredAt);
      audit.setProcessingResult("APPLIED");
    } else if ("route_failed".equals(type) || "command_rejected".equals(type) || "command_failed".equals(type)) {
      if (starting) execution.setStatus(TaskExecutionStatus.START_FAILED.name());
      else if (waitingLocal) execution.setStatus(TaskExecutionStatus.FAILED.name());
      else if (running) execution.setStatus(TaskExecutionStatus.FAILED.name());
      else {
        audit.setProcessingResult("INVALID_TRANSITION");
        audit.setConflictCode("EVENT_STATE_CONFLICT");
        execution.setManualReconciliationRequired(true);
        execution.setLastErrorCode("EVENT_STATE_CONFLICT");
        execution.setLastErrorMessage("机器人失败事件不符合本地执行状态机，需要人工对账");
        eventRepository.save(audit);
        return;
      }
      execution.setRecoveryStatus(null);
      execution.setLastErrorCode(safeCode(event.text("error_code"), "ROBOT_ROUTE_FAILED"));
      execution.setLastErrorMessage(safeMessage(firstNonBlank(event.text("error_message"), event.text("error"), event.text("reason")), "机器人报告路线执行失败"));
      audit.setProcessingResult("APPLIED");
    } else {
      audit.setProcessingResult("AUDITED");
    }
    eventRepository.save(audit);
  }

  private void updateProgress(TaskExecutionEntity execution, Map<String, Object> payload) {
    String targetId = text(payload.get("target_id"));
    if (nonBlank(targetId)) execution.setCurrentTargetId(targetId);
    int index = number(payload.get("target_index"));
    int progress = number(payload.get("progress"));
    if (progress <= 0) progress = percentageForTarget(execution.getRouteRevisionId(), index);
    execution.setProgress(Math.max(execution.getProgress(), Math.min(99, progress)));
  }

  private int percentageForTarget(String revisionId, int targetIndex) {
    if (targetIndex < 0) return 0;
    try {
      RouteRevisionEntity revision = revisions.findById(revisionId).orElse(null);
      if (revision == null) return 0;
      Map<String, Object> document = objectMapper.readValue(revision.getExecutorJson(), new TypeReference<Map<String, Object>>() {});
      Object targets = document.get("targets");
      if (!(targets instanceof List<?> values) || values.isEmpty()) return 0;
      return Math.min(99, ((targetIndex + 1) * 100) / values.size());
    } catch (Exception ignored) { return 0; }
  }

  private boolean validOwnership(TaskExecutionEntity execution, RobotBridgeExecutionEvent event, String robotId,
      String executionId, String deploymentId, String type, long sequence) {
    if (!"1.0".equals(event.text("schema_version")) || sequence <= 0 || !nonBlank(type)
        || !validOccurredAt(event.text("occurred_at"))
        || !Objects.equals(execution.getRobotId(), robotId) || !Objects.equals(execution.getExecutionId(), executionId)
        || !Objects.equals(execution.getDeploymentId(), deploymentId) || !nonBlank(event.text("command_id"))) return false;
    boolean startCommand = Objects.equals(execution.getStartRequestId(), event.text("request_id"))
      && (!nonBlank(execution.getStartCommandId()) || Objects.equals(execution.getStartCommandId(), event.text("command_id")));
    return startCommand || controlFor(execution, event) != null;
  }

  private TaskExecutionControlCommandEntity controlFor(TaskExecutionEntity execution, RobotBridgeExecutionEvent event) {
    TaskExecutionControlCommandEntity command = controlCommands.findByExecutionIdAndRequestId(execution.getExecutionId(), event.text("request_id")).orElse(null);
    if (command == null || !Objects.equals(command.getRobotId(), execution.getRobotId()) || !Objects.equals(command.getDeploymentId(), execution.getDeploymentId())) return null;
    return !nonBlank(command.getCommandId()) || Objects.equals(command.getCommandId(), event.text("command_id")) ? command : null;
  }

  private void acknowledged(TaskExecutionControlCommandEntity control, RobotExecutionEventEntity audit) {
    if (!TaskExecutionControlCommandStatus.CONFIRMED.name().equals(control.getStatus()) && !TaskExecutionControlCommandStatus.FAILED.name().equals(control.getStatus())) {
      control.setStatus(TaskExecutionControlCommandStatus.ACKED.name());
      control.setAckedAt(Instant.now().toString());
      control.setResultCode("ROBOT_ACKED");
      control.setResultMessage("机器人已持久化命令，等待真实 ROS 事件确认");
      control.setUpdatedAt(Instant.now().toString());
      controlCommands.save(control);
    }
    audit.setProcessingResult("ACKED");
    eventRepository.save(audit);
  }

  private boolean confirmedControl(TaskExecutionEntity execution, TaskExecutionControlCommandEntity control, String eventType,
      RobotExecutionEventEntity audit) {
    TaskExecutionStatus confirmed = switch (control.getAction()) {
      case "PAUSE" -> "route_paused".equals(eventType) ? TaskExecutionStatus.PAUSED : null;
      case "RESUME" -> "route_resumed".equals(eventType) ? TaskExecutionStatus.RUNNING : null;
      case "TAKEOVER" -> "manual_takeover".equals(eventType) ? TaskExecutionStatus.MANUAL_TAKEOVER : null;
      case "CANCEL" -> "route_canceled".equals(eventType) ? TaskExecutionStatus.CANCELLED : null;
      case "ESTOP" -> "emergency_stopped".equals(eventType) ? TaskExecutionStatus.ESTOPPED : null;
      default -> null;
    };
    if (confirmed == null) return false;
    if (!waitingStatus(control.getAction()).equals(execution.getStatus())) {
      audit.setProcessingResult("INVALID_TRANSITION");
      audit.setConflictCode("EVENT_STATE_CONFLICT");
      eventRepository.save(audit);
      return true;
    }
    String now = Instant.now().toString();
    execution.setStatus(confirmed.name());
    execution.setRecoveryStatus(null);
    control.setStatus(TaskExecutionControlCommandStatus.CONFIRMED.name());
    control.setConfirmedAt(now);
    control.setRecoveryAction("REAL_EVENT_CONFIRMED");
    control.setResultCode("CONTROL_APPLIED");
    control.setResultMessage("已由机器人真实事件确认");
    control.setUpdatedAt(now);
    controlCommands.save(control);
    audit.setProcessingResult("APPLIED");
    eventRepository.save(audit);
    return true;
  }

  private void failedControl(TaskExecutionEntity execution, TaskExecutionControlCommandEntity control, RobotBridgeExecutionEvent event,
      RobotExecutionEventEntity audit) {
    String now = Instant.now().toString();
    control.setStatus(TaskExecutionControlCommandStatus.FAILED.name());
    control.setResultCode(safeCode(firstNonBlank(event.text("error_code"), "ROBOT_COMMAND_REJECTED"), "ROBOT_COMMAND_REJECTED"));
    control.setResultMessage(safeMessage(firstNonBlank(event.text("error_message"), event.text("error"), event.text("reason")), "机器人拒绝控制命令"));
    control.setRecoveryAction("RESTORE_PREVIOUS_STATUS");
    control.setUpdatedAt(now);
    controlCommands.save(control);
    if (waitingStatus(control.getAction()).equals(execution.getStatus())) {
      execution.setStatus(control.getPriorExecutionStatus());
      execution.setRecoveryStatus(null);
    }
    execution.setLastErrorCode(control.getResultCode());
    execution.setLastErrorMessage(control.getResultMessage());
    audit.setProcessingResult("CONTROL_FAILED");
    eventRepository.save(audit);
  }

  private static String waitingStatus(String action) {
    return switch (action) {
      case "PAUSE" -> TaskExecutionStatus.PAUSING.name();
      case "RESUME" -> TaskExecutionStatus.RESUMING.name();
      case "TAKEOVER" -> TaskExecutionStatus.TAKEOVER_PENDING.name();
      case "CANCEL" -> TaskExecutionStatus.CANCELLING.name();
      case "ESTOP" -> TaskExecutionStatus.ESTOPPING.name();
      default -> "";
    };
  }

  private RobotExecutionEventEntity audit(RobotBridgeExecutionEvent input, String robotId, String executionId, String deploymentId,
      String eventId, String eventType, long sequence, String receivedAt) {
    RobotExecutionEventEntity item = new RobotExecutionEventEntity();
    item.setId(Ids.next("revt"));
    item.setRobotId(robotId);
    item.setExecutionId(executionId);
    item.setDeploymentId(deploymentId);
    item.setEventId(eventId);
    item.setSequence(sequence);
    item.setEventType(eventType);
    item.setOccurredAt(nonBlank(input.text("occurred_at")) ? input.text("occurred_at") : receivedAt);
    item.setReceivedAt(receivedAt);
    item.setPayloadSummary(payloadSummary(input.payload()));
    item.setErrorCode(safeCode(input.text("error_code"), null));
    item.setErrorMessage(safeMessage(input.text("error_message"), null));
    item.setProcessingResult("RECEIVED");
    return item;
  }

  private void reject(RobotExecutionEventEntity event, String code, String message) {
    event.setProcessingResult("REJECTED");
    event.setConflictCode(code);
    event.setErrorCode(code);
    event.setErrorMessage(safeMessage(message, "事件校验失败"));
    eventRepository.save(event);
    log.warn("event=robot_event_rejected robotId={} executionId={} deploymentId={} eventType={} sequence={} errorCode={}",
      event.getRobotId(), event.getExecutionId(), event.getDeploymentId(), event.getEventType(), event.getSequence(), code);
  }

  private void conflict(String executionId, String code, String message) {
    TaskExecutionEntity execution = executions.findByExecutionId(executionId).orElse(null);
    if (execution == null) return;
    execution.setManualReconciliationRequired(true);
    execution.setLastErrorCode(safeCode(code, "EVENT_CONFLICT"));
    execution.setLastErrorMessage(safeMessage(message, "机器人事件冲突"));
    if (!TaskExecutionStatus.TERMINAL.contains(execution.getStatus())
        && !TaskExecutionStatus.CREATED.name().equals(execution.getStatus())) {
      String previous = TaskExecutionStatus.RECOVERING.name().equals(execution.getStatus())
        ? execution.getRecoveryStatus() : execution.getStatus();
      execution.setRecoveryStatus(previous);
      execution.setStatus(TaskExecutionStatus.DISCONNECTED.name());
    }
    execution.setUpdatedAt(Instant.now().toString());
    executions.save(execution);
  }

  private static boolean same(RobotExecutionEventEntity stored, String executionId, String deploymentId, String eventId, String eventType) {
    return Objects.equals(stored.getExecutionId(), executionId) && Objects.equals(stored.getDeploymentId(), deploymentId)
      && Objects.equals(stored.getEventId(), eventId) && Objects.equals(stored.getEventType(), eventType);
  }
  private String payloadSummary(Map<String, Object> payload) {
    try {
      Map<String, Object> filtered = new LinkedHashMap<>();
      for (String field : List.of("target_id", "target_index", "progress", "reason", "code")) {
        if (payload.containsKey(field)) filtered.put(field, safePayloadValue(payload.get(field)));
      }
      return objectMapper.writeValueAsString(filtered);
    } catch (Exception ex) { return "{}"; }
  }
  private static Object safePayloadValue(Object value) {
    if (value instanceof Number || value instanceof Boolean) return value;
    return safeMessage(text(value), "");
  }
  private static String firstNonBlank(String... values) {
    for (String value : values) if (nonBlank(value)) return value;
    return "";
  }
  private static int number(Object value) {
    if (value instanceof Number number) return Math.max(0, number.intValue());
    try { return Math.max(0, Integer.parseInt(text(value))); } catch (Exception ignored) { return 0; }
  }
  private static String safeCode(String value, String fallback) {
    if (!nonBlank(value)) return fallback;
    String code = value.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase();
    return code.substring(0, Math.min(64, code.length()));
  }
  private static String safeMessage(String value, String fallback) {
    String message = nonBlank(value) ? value : fallback;
    if (message == null) return null;
    message = message.replaceAll("[\\r\\n]+", " ").trim();
    message = message.replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[REDACTED]");
    message = message.replaceAll("(?i)(authorization|token|password)=[^\\s,;]+", "$1=[REDACTED]");
    return message.substring(0, Math.min(500, message.length()));
  }
  private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
  private static boolean validOccurredAt(String value) {
    try { Instant.parse(value); return true; } catch (Exception ignored) { return false; }
  }

  private void completeTask(TaskExecutionEntity execution, String completedAt) {
    Map<String, Object> task = dataStore.get(DataCategory.TASK, execution.getTaskId());
    task.put("status", TaskExecutionStatus.COMPLETED.name());
    task.put("progress", 100);
    task.put("completedAt", completedAt);
    if (nonBlank(execution.getStartedAt())) task.put("startedAt", execution.getStartedAt());
    dataStore.upsert(DataCategory.TASK, task);

    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, text(task.get("routeId")));
    inspectionRecordService.createForCompletedTask(
        task,
        route,
        checkpointCount(execution.getRouteRevisionId()),
        execution.getStartedAt(),
        completedAt,
        execution.getExecutionId());
  }

  private int checkpointCount(String revisionId) {
    try {
      RouteRevisionEntity revision = revisions.findById(revisionId).orElse(null);
      if (revision == null) return 0;
      Map<String, Object> document = objectMapper.readValue(revision.getExecutorJson(), new TypeReference<Map<String, Object>>() {});
      Object targets = document.get("targets");
      return targets instanceof List<?> values ? values.size() : 0;
    } catch (Exception ignored) {
      return 0;
    }
  }
  private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
