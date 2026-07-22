package com.powerinspection.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.powerinspection.alarm.AlarmRepository;
import com.powerinspection.alarm.AlarmService;
import com.powerinspection.common.ApiException;
import com.powerinspection.model.LocateAnythingFinding;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DetectionAlarmServiceTests {
  @Mock AlarmService alarmService;
  @Mock AlarmRepository alarmRepository;
  private DetectionAlarmService service;

  @BeforeEach
  void setUp() {
    service = new DetectionAlarmService(alarmService, alarmRepository);
  }

  @Test
  void onlyExplicitRiskItemsCreateSourceLinkedAlarmsAndDuplicatesAreIgnored() {
    LocateAnythingFinding finding = new LocateAnythingFinding(
        "fire-risk", "FIRE", "火焰", 0.0, List.of(1, 2, 30, 40), "明火", "/annotated.jpg", Map.of());
    Map<String, Object> context = Map.of(
        "taskId", "task-1", "routeName", "路线 A", "checkpointName", "检查点 1",
        "checkpointId", "cp-1", "imageId", "img-1");
    List<Map<String, Object>> detections = List.of(
        Map.of("itemId", "fire-risk", "type", "FIRE", "name", "火焰", "displayLabel", "明火",
            "prompt", "火焰", "enabled", true, "alarmEnabled", true,
            "alarmOnFinding", true, "alarmSeverity", "CRITICAL", "alarmMessage", "发现{label}"),
        Map.of("itemId", "meter", "type", "METER", "name", "压力表", "prompt", "压力表",
            "enabled", true, "alarmEnabled", false));
    when(alarmRepository.existsByFindingKey(any())).thenReturn(false, true);
    when(alarmService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

    List<Map<String, Object>> created = service.createAlarms(
        "run-1", "DETECTION_RUN", context, detections, List.of(finding));
    List<Map<String, Object>> repeated = service.createAlarms(
        "run-1", "DETECTION_RUN", context, detections, List.of(finding));

    assertThat(created).hasSize(1);
    assertThat(created.get(0)).containsEntry("sourceType", "DETECTION_RUN")
        .containsEntry("detectionRunId", "run-1")
        .containsEntry("imageId", "img-1")
        .containsEntry("checkpointId", "cp-1")
        .containsEntry("itemId", "fire-risk")
        .containsEntry("severity", "CRITICAL")
        .containsEntry("message", "发现明火");
    assertThat(created.get(0).get("finding")).isEqualTo(Map.of(
        "itemId", "fire-risk", "type", "FIRE", "prompt", "火焰", "score", 0.0,
        "bbox", List.of(1, 2, 30, 40), "label", "明火", "imageUrl", "/annotated.jpg"));
    assertThat(repeated).isEmpty();
  }

  @Test
  void skipsFindingAlreadyPersistedByDatabaseKey() {
    LocateAnythingFinding finding = new LocateAnythingFinding(
        "fire-risk", "FIRE", "fire", 0.0, List.of(1, 2, 30, 40), "flame", "/annotated.jpg", Map.of());
    List<Map<String, Object>> detections = List.of(
        Map.of("itemId", "fire-risk", "type", "FIRE", "prompt", "fire",
            "enabled", true, "alarmEnabled", true, "alarmOnFinding", true,
            "alarmSeverity", "MEDIUM"));
    when(alarmRepository.existsByFindingKey("run-1:fire-risk:[1, 2, 30, 40]")).thenReturn(true);

    List<Map<String, Object>> created = service.createAlarms(
        "run-1", "DETECTION_RUN", Map.of(), detections, List.of(finding));

    assertThat(created).isEmpty();
    verifyNoInteractions(alarmService);
  }

  @Test
  void treatsUniqueConstraintRaceAsAnIdempotentDuplicate() {
    LocateAnythingFinding finding = new LocateAnythingFinding(
        "fire-risk", "FIRE", "fire", 0.0, List.of(1, 2, 30, 40), "flame", "/annotated.jpg", Map.of());
    List<Map<String, Object>> detections = List.of(
        Map.of("itemId", "fire-risk", "type", "FIRE", "prompt", "fire",
            "enabled", true, "alarmEnabled", true, "alarmOnFinding", true,
            "alarmSeverity", "MEDIUM"));
    when(alarmRepository.existsByFindingKey("run-1:fire-risk:[1, 2, 30, 40]"))
        .thenReturn(false, true);
    when(alarmService.create(any())).thenThrow(ApiException.conflict("duplicate"));

    List<Map<String, Object>> created = service.createAlarms(
        "run-1", "DETECTION_RUN", Map.of(), detections, List.of(finding));

    assertThat(created).isEmpty();
  }

  @Test
  void matchesFindingByStableItemIdInsteadOfTypeAndPrompt() {
    LocateAnythingFinding finding = new LocateAnythingFinding(
        "person-risk", "CUSTOM", "模型返回的提示词", 0.0, List.of(5, 6, 20, 30), "人员", null,
        Map.of());
    List<Map<String, Object>> detections = List.of(
        Map.of("itemId", "person-risk", "type", "PERSON", "prompt", "配置中的提示词",
            "enabled", true, "alarmMode", "ON_FINDING", "alarmSeverity", "HIGH"));
    when(alarmRepository.existsByFindingKey("run-2:person-risk:[5, 6, 20, 30]")).thenReturn(false);
    when(alarmService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

    List<Map<String, Object>> created = service.createAlarms(
        "run-2", "DETECTION_RUN", Map.of(), detections, List.of(finding));

    assertThat(created).singleElement().satisfies(alarm ->
        assertThat(alarm).containsEntry("itemId", "person-risk").containsEntry("severity", "HIGH"));
  }

  @Test
  void fallsBackToTypeAndPromptForLegacyFindingWithoutItemId() {
    LocateAnythingFinding finding = new LocateAnythingFinding(
        "FIRE", "定位明火", 0.0, List.of(5, 6, 20, 30), "明火", null, Map.of());
    List<Map<String, Object>> detections = List.of(
        Map.of("itemId", "fire-risk", "type", "FIRE", "prompt", "定位明火",
            "enabled", true, "alarmMode", "ON_FINDING", "alarmSeverity", "HIGH"));
    when(alarmRepository.existsByFindingKey("run-3:fire-risk:[5, 6, 20, 30]")).thenReturn(false);
    when(alarmService.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

    List<Map<String, Object>> created = service.createAlarms(
        "run-3", "DETECTION_RUN", Map.of(), detections, List.of(finding));

    assertThat(created).singleElement().satisfies(alarm ->
        assertThat(alarm).containsEntry("itemId", "fire-risk").containsEntry("severity", "HIGH"));
  }
}
