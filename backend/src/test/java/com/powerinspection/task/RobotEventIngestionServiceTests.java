package com.powerinspection.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
  @Mock private RouteRevisionRepository revisions;
  private RobotEventIngestionService service;
  private TaskExecutionEntity execution;

  @BeforeEach
  void setUp() {
    service = new RobotEventIngestionService(executions, events, revisions, new ObjectMapper());
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

    service.ingest("exec-1", event(1, "route_started", Map.of()));
    service.ingest("exec-1", event(2, "target_reached", Map.of("target_id", "target-1", "target_index", 0)));
    service.ingest("exec-1", event(3, "route_finished", Map.of()));

    assertEquals(TaskExecutionStatus.COMPLETED.name(), execution.getStatus());
    assertEquals("target-1", execution.getCurrentTargetId());
    assertEquals(100, execution.getProgress());
    assertEquals(3, execution.getLastRobotSequence());
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
  void lateOrTerminalEventsDoNotRollBackExecution() {
    execution.setStatus(TaskExecutionStatus.COMPLETED.name());
    execution.setLastRobotSequence(5);

    service.ingest("exec-1", event(4, "route_started", Map.of()));
    service.ingest("exec-1", event(6, "route_started", Map.of()));

    assertEquals(TaskExecutionStatus.COMPLETED.name(), execution.getStatus());
    assertEquals(6, execution.getLastRobotSequence());
  }

  @Test
  void ownershipMismatchEntersAuditableManualFailurePath() {
    RobotBridgeExecutionEvent wrongRobot = event(1, "route_started", Map.of());
    Map<String, Object> fields = new LinkedHashMap<>(wrongRobot.fields());
    fields.put("robot_id", "robot-other");

    service.ingest("exec-1", new RobotBridgeExecutionEvent(fields));

    assertEquals(TaskExecutionStatus.START_FAILED.name(), execution.getStatus());
    assertTrue(execution.isManualReconciliationRequired());
    assertEquals("EVENT_OWNERSHIP_CONFLICT", execution.getLastErrorCode());
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
}
