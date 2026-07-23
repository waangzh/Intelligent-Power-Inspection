package com.powerinspection.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.detection.DetectionRunService;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.robot.RobotGateway;
import com.powerinspection.robot.RobotProperties;
import com.powerinspection.record.InspectionRecordService;
import com.powerinspection.route.RouteRevisionEntity;
import com.powerinspection.route.RouteRevisionService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class TaskServiceTests {
  @Mock private DataStoreService dataStore;
  @Mock private SimpMessagingTemplate messagingTemplate;
  @Mock private RobotGateway robotGateway;
  @Mock private LocateAnythingGateway locateAnythingGateway;
  @Mock private RouteRevisionService routeRevisionService;
  @Mock private TaskExecutionService taskExecutionService;
  @Mock private DetectionRunService detectionRunService;
  @Mock private InspectionRecordService inspectionRecordService;
  private TaskService service;

  @BeforeEach
  void setUp() {
    RobotProperties properties = new RobotProperties();
    properties.setMode("bridge");
    service =
        new TaskService(
            dataStore,
            messagingTemplate,
            robotGateway,
            locateAnythingGateway,
            routeRevisionService,
            taskExecutionService,
            properties,
            detectionRunService,
            inspectionRecordService);
  }

  @Test
  void bridgeModeRejectsTaskWithoutRouteRevision() {
    Map<String, Object> body =
        new HashMap<>(Map.of("name", "巡检", "routeId", "route-1", "robotId", "robot-1"));

    ApiException error = assertThrows(ApiException.class, () -> service.createTask(body));

    assertEquals("Bridge 模式创建任务必须提供 routeRevisionId", error.getMessage());
  }

  @Test
  void validRevisionCreatesTaskAndImmutableExecutionBinding() {
    RouteRevisionEntity revision = new RouteRevisionEntity();
    revision.setId("rev-1");
    revision.setRouteId("route-1");
    revision.setContentSha256("a".repeat(64));
    revision.setMapImageSha256("b".repeat(64));
    TaskExecutionEntity execution = new TaskExecutionEntity();
    execution.setExecutionId("exec-1");
    execution.setRouteContentSha256("a".repeat(64));
    execution.setMapImageSha256("b".repeat(64));
    when(routeRevisionService.require("rev-1")).thenReturn(revision);
    when(dataStore.find(DataCategory.ROUTE, "route-1"))
        .thenReturn(Map.of("id", "route-1", "siteId", "site-1"));
    when(dataStore.find(DataCategory.ROBOT, "robot-1"))
        .thenReturn(Map.of("id", "robot-1", "siteId", "site-1"));
    when(taskExecutionService.bind(any(), any())).thenReturn(execution);
    when(dataStore.upsert(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
    Map<String, Object> body =
        new HashMap<>(
            Map.of(
                "id",
                "task-1",
                "name",
                "巡检",
                "routeId",
                "route-1",
                "routeRevisionId",
                "rev-1",
                "robotId",
                "robot-1"));

    Map<String, Object> saved = service.createTask(body);

    assertEquals("exec-1", saved.get("executionId"));
    assertEquals(TaskExecutionStatus.CREATED.name(), saved.get("status"));
    verify(taskExecutionService).bind(any(), eq(revision));
  }
}
