package com.powerinspection.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.record.InspectionRecordService;
import com.powerinspection.robot.RobotBridgeExecutionEvent;
import com.powerinspection.route.RouteRevisionEntity;
import com.powerinspection.route.RouteRevisionRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RobotEventIngestionServiceTests {
  @Mock private TaskExecutionRepository executions;
  @Mock private RobotExecutionEventRepository events;
  @Mock private TaskExecutionControlCommandRepository controls;
  @Mock private RouteRevisionRepository revisions;
  @Mock private DataStoreService dataStore;
  @Mock private InspectionRecordService inspectionRecordService;
  @Mock private NotificationService notificationService;
  private RobotEventIngestionService service;
  private TaskExecutionEntity execution;

  @BeforeEach
  void setUp() {
    service = new RobotEventIngestionService(
        executions, events, controls, revisions, new ObjectMapper(), dataStore, inspectionRecordService,
        notificationService);
    execution = execution(TaskExecutionStatus.STARTING.name());
    when(executions.findByExecutionId("exec-1")).thenReturn(Optional.of(execution));
    when(events.findByRobotIdAndSequence(anyString(), anyLong())).thenReturn(Optional.empty());
    when(events.findByEventId(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void routeStartedTargetReachedAndFinishedFollowTheMinimalStateMachine() {
    RouteRevisionEntity revision = new RouteRevisionEntity();
    revision.setExecutorJson("{\"targets\":[{},{}]}");
    when(revisions.findById("rev-1")).thenReturn(Optional.of(revision));
    Map<String, Object> task = new LinkedHashMap<>(Map.of(
        "id", "task-1", "name", "巡检任务", "routeId", "route-1", "robotId", "robot-1"));
    Map<String, Object> route = Map.of("id", "route-1", "siteId", "site-1", "name", "巡检路线");
    when(dataStore.get(DataCategory.TASK, "task-1")).thenReturn(task);
    when(dataStore.get(DataCategory.ROUTE, "route-1")).thenReturn(route);
    when(dataStore.upsert(DataCategory.TASK, task)).thenReturn(task);

    service.ingest("exec-1", event(1, "route_started", Map.of()));
    service.ingest("exec-1", event(2, "target_reached", Map.of("target_id", "target-1", "target_index", 0)));
    service.ingest("exec-1", event(3, "route_finished", Map.of()));

    assertEquals(TaskExecutionStatus.COMPLETED.name(), execution.getStatus());
    assertEquals("target-1", execution.getCurrentTargetId());
    assertEquals(100, execution.getProgress());
    assertEquals(3, execution.getLastRobotSequence());
    assertEquals(TaskExecutionStatus.COMPLETED.name(), task.get("status"));
    verify(inspectionRecordService).createForCompletedTask(
        task, route, 2, execution.getStartedAt(), "2026-07-14T00:00:00Z", "exec-1");
  }

  @Test
  void ackDoesNotEnterRunningAndRouteFailedBeforeStartBecomesStartFailed() {
    service.ingest("exec-1", event(1, "command_accepted", Map.of()));
    assertEquals(TaskExecutionStatus.STARTING.name(), execution.getStatus());

    service.ingest("exec-1", failedEvent(2));
    assertEquals(TaskExecutionStatus.START_FAILED.name(), execution.getStatus());
    assertEquals("MAP_HASH_MISMATCH", execution.getLastErrorCode());
  }

  @Test
  void localConfirmationWaitsUntilRouteStarted() {
    execution.setStartMode(TaskStartMode.LOCAL_CONFIRM.name());

    service.ingest("exec-1", event(1, "start_waiting_local_confirmation", Map.of()));
    assertEquals(TaskExecutionStatus.WAITING_LOCAL_CONFIRM.name(), execution.getStatus());
    assertEquals("2026-07-14T00:00:00Z", execution.getRobotReadyAt());

    service.ingest("exec-1", event(2, "local_start_confirmed", Map.of()));
    assertEquals(TaskExecutionStatus.WAITING_LOCAL_CONFIRM.name(), execution.getStatus());
    assertEquals("2026-07-14T00:00:00Z", execution.getLocalConfirmedAt());

    service.ingest("exec-1", event(3, "route_started", Map.of()));
    assertEquals(TaskExecutionStatus.RUNNING.name(), execution.getStatus());
    assertEquals("2026-07-14T00:00:00Z", execution.getStartedAt());
  }

  @Test
  void routeStartedCanMoveWaitingLocalExecutionToRunningWithoutOptionalConfirmEvent() {
    execution.setStartMode(TaskStartMode.LOCAL_CONFIRM.name());
    execution.setStatus(TaskExecutionStatus.WAITING_LOCAL_CONFIRM.name());
    execution.setRobotReadyAt("2026-07-14T00:00:00Z");

    service.ingest("exec-1", event(1, "route_started", Map.of()));

    assertEquals(TaskExecutionStatus.RUNNING.name(), execution.getStatus());
    assertEquals(execution.getStartedAt(), execution.getLocalConfirmedAt());
  }

  @Test
  void lateOrTerminalEventsDoNotRollBackExecution() {
    execution.setStatus(TaskExecutionStatus.COMPLETED.name());
    execution.setLastRobotSequence(5);

    service.ingest("exec-1", event(4, "route_started", Map.of()));
    service.ingest("exec-1", event(6, "route_started", Map.of()));

    assertEquals(TaskExecutionStatus.COMPLETED.name(), execution.getStatus());
    assertEquals(6, execution.getLastRobotSequence());
  }

  @Test
  void ownershipMismatchPreservesUnknownRemoteStateForManualReconciliation() {
    RobotBridgeExecutionEvent wrongRobot = event(1, "route_started", Map.of());
    Map<String, Object> fields = new LinkedHashMap<>(wrongRobot.fields());
    fields.put("robot_id", "robot-other");

    service.ingest("exec-1", new RobotBridgeExecutionEvent(fields));

    assertEquals(TaskExecutionStatus.DISCONNECTED.name(), execution.getStatus());
    assertEquals(TaskExecutionStatus.STARTING.name(), execution.getRecoveryStatus());
    assertTrue(execution.isManualReconciliationRequired());
    assertEquals("EVENT_OWNERSHIP_CONFLICT", execution.getLastErrorCode());
  }

  @Test
  void ownershipMismatchDoesNotTurnAReportedRunningExecutionIntoFailed() {
    execution.setStatus(TaskExecutionStatus.RUNNING.name());
    RobotBridgeExecutionEvent wrongRequest = event(1, "target_reached", Map.of());
    Map<String, Object> fields = new LinkedHashMap<>(wrongRequest.fields());
    fields.put("request_id", "request-other");

    service.ingest("exec-1", new RobotBridgeExecutionEvent(fields));

    assertEquals(TaskExecutionStatus.DISCONNECTED.name(), execution.getStatus());
    assertEquals(TaskExecutionStatus.RUNNING.name(), execution.getRecoveryStatus());
    assertTrue(execution.isManualReconciliationRequired());
  }

  @Test
  void controlAckDoesNotChangeExecutionUntilTheMatchingRealEventArrives() {
    execution.setStatus(TaskExecutionStatus.PAUSING.name());
    TaskExecutionControlCommandEntity control = control("PAUSE", TaskExecutionStatus.RUNNING.name());
    when(controls.findByExecutionIdAndRequestId("exec-1", "control-1")).thenReturn(Optional.of(control));

    service.ingest("exec-1", controlEvent(1, "command_accepted"));
    assertEquals(TaskExecutionStatus.PAUSING.name(), execution.getStatus());
    assertEquals(TaskExecutionControlCommandStatus.ACKED.name(), control.getStatus());

    service.ingest("exec-1", controlEvent(2, "route_paused"));
    assertEquals(TaskExecutionStatus.PAUSED.name(), execution.getStatus());
    assertEquals(TaskExecutionControlCommandStatus.CONFIRMED.name(), control.getStatus());
  }

  @Test
  void canceledExecutionCannotBeRolledBackByLateControlEvent() {
    execution.setStatus(TaskExecutionStatus.CANCELLED.name());
    execution.setLastRobotSequence(2);
    TaskExecutionControlCommandEntity control = control("CANCEL", TaskExecutionStatus.RUNNING.name());
    when(controls.findByExecutionIdAndRequestId("exec-1", "control-1")).thenReturn(Optional.of(control));

    service.ingest("exec-1", controlEvent(3, "route_resumed"));

    assertEquals(TaskExecutionStatus.CANCELLED.name(), execution.getStatus());
  }

  private static TaskExecutionEntity execution(String status) {
    TaskExecutionEntity item = new TaskExecutionEntity();
    item.setTaskId("task-1"); item.setExecutionId("exec-1"); item.setRobotId("robot-1"); item.setDeploymentId("dep-1");
    item.setRouteRevisionId("rev-1"); item.setRouteContentSha256("a".repeat(64)); item.setMapImageSha256("b".repeat(64));
    item.setStartRequestId("request-1"); item.setStartCommandId("command-1"); item.setStatus(status); item.setCreatedAt("2026-07-14T00:00:00Z"); item.setUpdatedAt("2026-07-14T00:00:00Z");
    return item;
  }

  private static RobotBridgeExecutionEvent event(long sequence, String event, Map<String, Object> payload) {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("schema_version", "1.0"); fields.put("robot_id", "robot-1"); fields.put("boot_id", "boot-1"); fields.put("sequence", sequence);
    fields.put("event", event); fields.put("execution_id", "exec-1"); fields.put("deployment_id", "dep-1"); fields.put("request_id", "request-1");
    fields.put("command_id", "command-1"); fields.put("occurred_at", "2026-07-14T00:00:00Z"); fields.put("payload", payload);
    return new RobotBridgeExecutionEvent(fields);
  }

  private static RobotBridgeExecutionEvent failedEvent(long sequence) {
    Map<String, Object> fields = new LinkedHashMap<>(event(sequence, "route_failed", Map.of()).fields());
    fields.put("error_code", "MAP_HASH_MISMATCH"); fields.put("error_message", "地图哈希不一致");
    return new RobotBridgeExecutionEvent(fields);
  }

  private static TaskExecutionControlCommandEntity control(String action, String priorStatus) {
    TaskExecutionControlCommandEntity item = new TaskExecutionControlCommandEntity();
    item.setId("control-1"); item.setExecutionId("exec-1"); item.setRobotId("robot-1"); item.setDeploymentId("dep-1");
    item.setAction(action); item.setRequestId("control-1"); item.setCommandId("control-command-1"); item.setPriorExecutionStatus(priorStatus);
    item.setStatus(TaskExecutionControlCommandStatus.QUEUED.name()); item.setRequestedAt("2026-07-14T00:00:00Z"); item.setUpdatedAt("2026-07-14T00:00:00Z");
    return item;
  }

  private static RobotBridgeExecutionEvent controlEvent(long sequence, String event) {
    Map<String, Object> fields = new LinkedHashMap<>(event(sequence, event, Map.of()).fields());
    fields.put("request_id", "control-1"); fields.put("command_id", "control-command-1");
    return new RobotBridgeExecutionEvent(fields);
  }
}
