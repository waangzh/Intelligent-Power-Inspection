package com.powerinspection.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.model.LocateAnythingResult;
import com.powerinspection.model.ModelProperties;
import com.powerinspection.robot.RobotInspectionImage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class DetectionRunServiceTests {
  @Mock DetectionRunRepository repository;
  @Mock RobotInspectionImageService imageService;
  @Mock LocateAnythingGateway gateway;
  @Mock DetectionAlarmService detectionAlarmService;

  @Test
  void recordsFormalTaskDetectionAndDelegatesTheSameFindingSnapshotToAlarmService() {
    ModelProperties properties = new ModelProperties();
    properties.getLocateAnything().setBaseUrl("http://localhost:18080");
    DetectionRunService service = new DetectionRunService(
        repository, imageService, gateway, new ObjectMapper(), properties, detectionAlarmService);
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    LocateAnythingFinding finding = new LocateAnythingFinding(
        "fire-risk", "FIRE", "火焰", 0.0, List.of(1, 2, 3, 4), "明火", "/annotated.jpg", Map.of());
    LocateAnythingResult result = new LocateAnythingResult(List.of(finding), List.of(), null);
    List<Map<String, Object>> detections = List.of(Map.of(
        "itemId", "fire-risk", "type", "FIRE", "prompt", "火焰",
        "enabled", true, "alarmMode", "ON_FINDING",
        "alarmSeverity", "CRITICAL"));

    DetectionRunEntity run = service.recordTaskResult(
        Map.of("id", "task-1"),
        Map.of("id", "route-1", "name", "路线 A"),
        Map.of("id", "cp-1", "name", "检查点 1"),
        new RobotInspectionImage("image-1", "http://image", 640, 480),
        detections,
        result);

    assertThat(run.getSourceType()).isEqualTo("TASK_CHECKPOINT");
    assertThat(run.getTaskId()).isEqualTo("task-1");
    assertThat(run.getCheckpointId()).isEqualTo("cp-1");
    assertThat(run.getImageId()).isEqualTo("image-1");
    assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
    verify(detectionAlarmService).createAlarms(
        any(), org.mockito.ArgumentMatchers.eq("DETECTION_RUN"), any(),
        org.mockito.ArgumentMatchers.eq(detections), org.mockito.ArgumentMatchers.eq(List.of(finding)));
  }

  @Test
  void robotImageDetectionStoresExplicitSafeRiskDefaults() throws Exception {
    ModelProperties properties = new ModelProperties();
    properties.getLocateAnything().setBaseUrl("http://localhost:18080");
    ObjectMapper objectMapper = new ObjectMapper();
    DetectionRunService service = new DetectionRunService(
        repository, imageService, gateway, objectMapper, properties, detectionAlarmService);
    RobotInspectionImageEntity image = new RobotInspectionImageEntity();
    image.setId("image-1");
    image.setTaskId("task-1");
    image.setCheckpointId("cp-1");
    when(imageService.requireAvailable("image-1")).thenReturn(image);
    when(imageService.publicImageUrl(image, "http://localhost/model-files/"))
        .thenReturn("http://localhost/model-files/image.jpg");
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    try {
      DetectionRunEntity run = service.submitRobotImage(
          "image-1",
          List.of(Map.of("type", "FIRE", "enabled", true, "prompt", "定位明火")),
          "user-1",
          "http://localhost/model-files/");

      var item = objectMapper.readTree(run.getDetectionsJson()).get(0);
      assertThat(item.path("itemId").asText()).isEqualTo("FIRE");
      assertThat(item.path("name").asText()).isEqualTo("FIRE");
      assertThat(item.path("alarmMode").asText()).isEqualTo("OFF");
      assertThat(item.path("alarmSeverity").asText()).isEqualTo("MEDIUM");
      assertThat(item.path("alarmMessage").asText()).isEmpty();
    } finally {
      service.shutdown();
    }
  }

  @Test
  void robotImageDetectionRejectsNonCanonicalAlarmSeverity() {
    ModelProperties properties = new ModelProperties();
    properties.getLocateAnything().setBaseUrl("http://localhost:18080");
    DetectionRunService service = new DetectionRunService(
        repository, imageService, gateway, new ObjectMapper(), properties, detectionAlarmService);
    RobotInspectionImageEntity image = new RobotInspectionImageEntity();
    image.setId("image-1");
    when(imageService.requireAvailable("image-1")).thenReturn(image);

    try {
      assertThatThrownBy(() -> service.submitRobotImage(
          "image-1",
          List.of(Map.of(
              "type", "FIRE", "enabled", true, "prompt", "定位明火",
              "alarmMode", "ON_FINDING", "alarmSeverity", "critical")),
          "user-1",
          "http://localhost/model-files/"))
          .isInstanceOf(ApiException.class)
          .hasMessage("告警级别必须是 LOW、MEDIUM、HIGH 或 CRITICAL");
    } finally {
      service.shutdown();
    }
  }
}
