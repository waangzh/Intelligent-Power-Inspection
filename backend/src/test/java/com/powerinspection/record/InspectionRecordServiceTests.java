package com.powerinspection.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerinspection.common.PageResult;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InspectionRecordServiceTests {
  @Mock private DataStoreService dataStore;
  private InspectionRecordService service;

  @BeforeEach
  void setUp() {
    service = new InspectionRecordService(dataStore);
  }

  @Test
  void completedTaskCreatesSummaryRecord() {
    Map<String, Object> task = Map.of(
        "id", "task-1", "name", "主变巡检", "robotId", "robot-1");
    Map<String, Object> route = Map.of(
        "id", "route-1", "name", "巡检路线", "siteId", "site-1");
    when(dataStore.page(
        eq(DataCategory.RECORD), eq(0), eq(1), eq("createdAt"), eq("desc"),
        eq(null), eq(null), eq(Map.of("taskId", "task-1"))))
        .thenReturn(new PageResult<>(List.of(), 0, 0, 1, false, null));
    when(dataStore.find(DataCategory.ROBOT, "robot-1"))
        .thenReturn(Map.of("id", "robot-1", "name", "巡检机器人"));
    when(dataStore.find(DataCategory.SITE, "site-1"))
        .thenReturn(Map.of("id", "site-1", "name", "城东变电站"));
    when(dataStore.count(DataCategory.ALARM, null, null, Map.of("taskId", "task-1")))
        .thenReturn(2L);
    when(dataStore.upsert(eq(DataCategory.RECORD), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(1));

    service.createForCompletedTask(
        task, route, 3, "2026-07-14T00:00:00Z", "2026-07-14T00:31:00Z", "exec-1");

    ArgumentCaptor<Map<String, Object>> record = ArgumentCaptor.forClass(Map.class);
    verify(dataStore).upsert(eq(DataCategory.RECORD), record.capture());
    assertEquals("record_exec-1", record.getValue().get("id"));
    assertEquals("task-1", record.getValue().get("taskId"));
    assertEquals(3, record.getValue().get("checkpointCount"));
    assertEquals(2L, record.getValue().get("alarmCount"));
    assertEquals("31 分钟", record.getValue().get("duration"));
  }

  @Test
  void existingTaskRecordMakesCompletionIdempotent() {
    Map<String, Object> existing = new LinkedHashMap<>(Map.of("id", "record-old", "taskId", "task-1"));
    when(dataStore.page(
        eq(DataCategory.RECORD), eq(0), eq(1), eq("createdAt"), eq("desc"),
        eq(null), eq(null), eq(Map.of("taskId", "task-1"))))
        .thenReturn(new PageResult<>(List.of(existing), 1, 0, 1, false, null));

    Map<String, Object> result = service.createForCompletedTask(
        Map.of("id", "task-1"), Map.of(), 0, null, "2026-07-14T00:00:00Z", "exec-1");

    assertEquals(existing, result);
    verify(dataStore, never()).upsert(eq(DataCategory.RECORD), org.mockito.ArgumentMatchers.any());
  }
}
