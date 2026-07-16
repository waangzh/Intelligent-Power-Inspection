package com.powerinspection.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskExecutionLifecycleServiceTests {
  @Mock private TaskExecutionRepository executions;
  @Mock private TaskExecutionControlCommandRepository controlCommands;
  @Mock private RouteRevisionRepository revisions;
  @Mock private RouteDeploymentRepository deployments;
  @Mock private RobotHeartbeatService heartbeats;
  @Mock private DataStoreService dataStore;
  private TaskExecutionLifecycleService service;
  private TaskExecutionEntity execution;
  private RouteRevisionEntity revision;

  @BeforeEach
  void setUp() {
    RobotProperties properties = new RobotProperties();
    properties.setMode("bridge");
    service = new TaskExecutionLifecycleService(executions, controlCommands, revisions, deployments, heartbeats, dataStore, new ObjectMapper(), properties);
    execution = execution(TaskExecutionStatus.CREATED.name());
    revision = revision();
    when(executions.findById("task-1")).thenReturn(Optional.of(execution));
    when(executions.findByTaskIdForStart("task-1")).thenReturn(Optional.of(execution));
    when(revisions.findById("rev-1")).thenReturn(Optional.of(revision));
    when(dataStore.get(any(), anyString())).thenReturn(task());
    when(heartbeats.detail("robot-1")).thenReturn(online());
  }

  @Test
  void onlyMatchingReadyForRobotDeploymentCanStart() {
    when(executions.findByStartRequestId("start-1")).thenReturn(Optional.empty());
    when(deployments.findByRobotIdAndRouteRevisionIdAndStateOrderByCreatedAtDesc("robot-1", "rev-1", "READY_FOR_ROBOT"))
      .thenReturn(List.of(deployment("a".repeat(64))));

    Map<String, Object> result = service.start("task-1", "start-1");

    assertEquals(TaskExecutionStatus.STARTING.name(), execution.getStatus());
    assertEquals("dep-1", execution.getDeploymentId());
    assertEquals("route-1", execution.getExecutorRouteId());
    assertEquals("STARTING", result.get("status"));
  }

  @Test
  void hashMismatchRejectsStartBeforeAnyBridgeCommand() {
    when(executions.findByStartRequestId("start-1")).thenReturn(Optional.empty());
    when(deployments.findByRobotIdAndRouteRevisionIdAndStateOrderByCreatedAtDesc("robot-1", "rev-1", "READY_FOR_ROBOT"))
      .thenReturn(List.of(deployment("c".repeat(64))));

    assertThrows(ApiException.class, () -> service.start("task-1", "start-1"));
    assertEquals(TaskExecutionStatus.CREATED.name(), execution.getStatus());
  }

  @Test
  void conflictingActiveExecutionRejectsStart() {
    when(executions.findByStartRequestId("start-1")).thenReturn(Optional.empty());
    TaskExecutionEntity other = execution(TaskExecutionStatus.RUNNING.name());
    other.setTaskId("task-2"); other.setExecutionId("exec-2");
    when(executions.findByRobotIdAndStatusIn("robot-1", TaskExecutionStatus.ACTIVE)).thenReturn(List.of(other));

    ApiException error = assertThrows(ApiException.class, () -> service.start("task-1", "start-1"));

    assertTrue(error.getMessage().contains("活动执行"));
    assertEquals(TaskExecutionStatus.CREATED.name(), execution.getStatus());
  }

  @Test
  void startFailedExecutionCanRetryWithNewRequestId() {
    execution.setStatus(TaskExecutionStatus.START_FAILED.name());
    execution.setStartRequestId("failed-start");
    execution.setStartCommandId("failed-command");
    when(executions.findByStartRequestId("retry-start")).thenReturn(Optional.empty());
    when(deployments.findByRobotIdAndRouteRevisionIdAndStateOrderByCreatedAtDesc("robot-1", "rev-1", "READY_FOR_ROBOT"))
      .thenReturn(List.of(deployment("a".repeat(64))));

    Map<String, Object> result = service.start("task-1", "retry-start");

    assertEquals(TaskExecutionStatus.STARTING.name(), result.get("status"));
    assertEquals("retry-start", execution.getStartRequestId());
    assertNull(execution.getStartCommandId());
  }

  @Test
  void manualReconciliationFailureCannotBeRetried() {
    execution.setStatus(TaskExecutionStatus.START_FAILED.name());
    execution.setManualReconciliationRequired(true);
    when(executions.findByStartRequestId("retry-start")).thenReturn(Optional.empty());

    ApiException error = assertThrows(ApiException.class, () -> service.start("task-1", "retry-start"));

    assertTrue(error.getMessage().contains("未核对"));
    assertEquals(TaskExecutionStatus.START_FAILED.name(), execution.getStatus());
    assertTrue(execution.isManualReconciliationRequired());
  }

  @Test
  void identicalIdempotencyKeyReturnsThePersistedStartingIntent() {
    execution.setStatus(TaskExecutionStatus.STARTING.name());
    execution.setStartRequestId("start-1");
    execution.setStartRequestFingerprint("ignored");
    when(executions.findByStartRequestId("start-1")).thenReturn(Optional.of(execution));

    // The fingerprint is intentionally set by a real first request; emulate it with a preceding successful start.
    execution.setStatus(TaskExecutionStatus.CREATED.name());
    when(executions.findByStartRequestId("seed")).thenReturn(Optional.empty());
    when(deployments.findByRobotIdAndRouteRevisionIdAndStateOrderByCreatedAtDesc("robot-1", "rev-1", "READY_FOR_ROBOT"))
      .thenReturn(List.of(deployment("a".repeat(64))));
    service.start("task-1", "seed");
    when(executions.findByStartRequestId("seed")).thenReturn(Optional.of(execution));

    Map<String, Object> result = service.start("task-1", "seed");

    assertEquals(TaskExecutionStatus.STARTING.name(), result.get("status"));
    assertEquals("seed", execution.getStartRequestId());
  }

  @Test
  void timeoutMovesToDisconnectedThenConservativelyRestoresPreviousState() {
    execution.setStatus(TaskExecutionStatus.RUNNING.name());
    when(executions.findByExecutionId("exec-1")).thenReturn(Optional.of(execution));

    service.disconnected("exec-1", "BRIDGE_UNREACHABLE", "Bridge 超时", Instant.now());
    assertEquals(TaskExecutionStatus.DISCONNECTED.name(), execution.getStatus());
    assertEquals(TaskExecutionStatus.RUNNING.name(), execution.getRecoveryStatus());
    service.beginRecovery("exec-1", Instant.now());
    service.completeRecovery("exec-1", Instant.now());

    assertEquals(TaskExecutionStatus.RUNNING.name(), execution.getStatus());
  }

  private static TaskExecutionEntity execution(String status) {
    TaskExecutionEntity item = new TaskExecutionEntity();
    item.setTaskId("task-1"); item.setExecutionId("exec-1"); item.setRouteRevisionId("rev-1"); item.setRobotId("robot-1");
    item.setRouteContentSha256("a".repeat(64)); item.setMapImageSha256("b".repeat(64)); item.setStatus(status);
    item.setCreatedAt("2026-07-14T00:00:00Z"); item.setUpdatedAt("2026-07-14T00:00:00Z");
    return item;
  }

  private static RouteRevisionEntity revision() {
    RouteRevisionEntity item = new RouteRevisionEntity();
    item.setId("rev-1"); item.setMapAssetId("map-1"); item.setContentSha256("a".repeat(64)); item.setMapImageSha256("b".repeat(64));
    item.setExecutorJson("{\"active_route_id\":\"route-1\",\"routes\":[{\"id\":\"route-1\"}]}");
    return item;
  }

  private static RouteDeploymentEntity deployment(String routePayloadHash) {
    RouteDeploymentEntity item = new RouteDeploymentEntity();
    item.setId("dep-1"); item.setRobotId("robot-1"); item.setRouteRevisionId("rev-1"); item.setState(RouteDeploymentState.READY_FOR_ROBOT.name());
    item.setRemoteSummaryJson("{\"state\":\"READY_FOR_ROBOT\",\"deploymentId\":\"dep-1\",\"robotId\":\"robot-1\",\"routeRevisionId\":\"rev-1\",\"routeRevisionContentSha256\":\"" + "a".repeat(64) + "\",\"routePayloadSha256\":\"" + routePayloadHash + "\",\"routeContentSha256\":\"" + "a".repeat(64) + "\",\"mapAssetId\":\"map-1\",\"mapImageSha256\":\"" + "b".repeat(64) + "\"}");
    return item;
  }

  private static Map<String, Object> task() {
    return Map.of("routeRevisionId", "rev-1", "robotId", "robot-1", "executionId", "exec-1", "routeContentSha256", "a".repeat(64), "mapImageSha256", "b".repeat(64));
  }

  private static RobotHeartbeatStatusView online() {
    return new RobotHeartbeatStatusView("robot-1", null, null, RobotConnectionStatus.CONNECTED.name(), true, Instant.now(), Instant.now(), Instant.now(), null,
      new RobotHeartbeatStatusView.Source("robot-bridge", true), "1.0", "boot-1", "test", "idle", 0, null);
  }
}
