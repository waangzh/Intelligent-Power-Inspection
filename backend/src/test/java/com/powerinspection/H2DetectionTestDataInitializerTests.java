package com.powerinspection.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class H2DetectionTestDataInitializerTests {
  @Test
  void seedsOneRouteCheckpointAndTaskForH2() {
    DataStoreService dataStore = mock(DataStoreService.class);
    when(dataStore.exists(DataCategory.ROBOT, "robot_001")).thenReturn(true);

    new H2DetectionTestDataInitializer(dataStore, "jdbc:h2:mem:power_inspection").run(null);

    ArgumentCaptor<Map<String, Object>> routeCaptor = mapCaptor();
    ArgumentCaptor<Map<String, Object>> taskCaptor = mapCaptor();
    verify(dataStore).upsert(eq(DataCategory.ROUTE), routeCaptor.capture());
    verify(dataStore).upsert(eq(DataCategory.TASK), taskCaptor.capture());

    Map<String, Object> route = routeCaptor.getValue();
    assertThat(route.get("id")).isEqualTo("route_h2_robot_image_test");
    assertThat(route.get("name")).isEqualTo("机器人图片检测测试路线");
    assertThat((List<?>) route.get("checkpoints")).singleElement().satisfies(rawCheckpoint -> {
      Map<?, ?> checkpoint = (Map<?, ?>) rawCheckpoint;
      assertThat(checkpoint.get("id")).isEqualTo("checkpoint_h2_robot_image_test");
      assertThat(checkpoint.get("name")).isEqualTo("测试检查点");
    });

    assertThat(taskCaptor.getValue()).containsEntry("id", "task_h2_robot_image_test")
      .containsEntry("name", "机器人图片检测测试任务")
      .containsEntry("routeId", "route_h2_robot_image_test")
      .containsEntry("robotId", "robot_001");
  }

  @Test
  void skipsTestDataOutsideH2() {
    DataStoreService dataStore = mock(DataStoreService.class);

    new H2DetectionTestDataInitializer(dataStore, "jdbc:mysql://localhost/ipi").run(null);

    verify(dataStore, never()).upsert(eq(DataCategory.ROUTE), anyMap());
    verify(dataStore, never()).upsert(eq(DataCategory.TASK), anyMap());
  }

  @Test
  void doesNotDuplicateExistingTestData() {
    DataStoreService dataStore = mock(DataStoreService.class);
    when(dataStore.exists(DataCategory.ROBOT, "robot_001")).thenReturn(true);
    when(dataStore.exists(DataCategory.ROUTE, "route_h2_robot_image_test")).thenReturn(true);
    when(dataStore.exists(DataCategory.TASK, "task_h2_robot_image_test")).thenReturn(true);

    new H2DetectionTestDataInitializer(dataStore, "jdbc:h2:mem:power_inspection").run(null);

    verify(dataStore, never()).upsert(eq(DataCategory.ROUTE), anyMap());
    verify(dataStore, never()).upsert(eq(DataCategory.TASK), anyMap());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
    return ArgumentCaptor.forClass((Class) Map.class);
  }
}
