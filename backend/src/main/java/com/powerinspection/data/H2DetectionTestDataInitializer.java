package com.powerinspection.data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class H2DetectionTestDataInitializer implements ApplicationRunner {
  static final String ROBOT_ID = "robot_001";
  static final String ROUTE_ID = "route_h2_robot_image_test";
  static final String CHECKPOINT_ID = "checkpoint_h2_robot_image_test";
  static final String TASK_ID = "task_h2_robot_image_test";

  private final DataStoreService dataStore;
  private final String datasourceUrl;

  public H2DetectionTestDataInitializer(
      DataStoreService dataStore,
      @Value("${spring.datasource.url:}") String datasourceUrl) {
    this.dataStore = dataStore;
    this.datasourceUrl = datasourceUrl;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (datasourceUrl == null || !datasourceUrl.startsWith("jdbc:h2:")) return;
    if (!dataStore.exists(DataCategory.ROBOT, ROBOT_ID)) return;

    if (!dataStore.exists(DataCategory.ROUTE, ROUTE_ID)) {
      Map<String, Object> position = map("lat", 30.2741, "lng", 120.1551);
      Map<String, Object> checkpoint = map(
        "id", CHECKPOINT_ID,
        "routeId", ROUTE_ID,
        "name", "测试检查点",
        "seq", 1,
        "position", position,
        "pan", 0,
        "tilt", 0,
        "dwellSeconds", 10,
        "detections", List.of()
      );
      dataStore.upsert(DataCategory.ROUTE, map(
        "id", ROUTE_ID,
        "siteId", "site_001",
        "name", "机器人图片检测测试路线",
        "description", "仅用于本地 H2 环境测试机器人图片检测",
        "mapMode", "2d",
        "path", List.of(position),
        "routeDetections", List.of(),
        "checkpoints", List.of(checkpoint),
        "createdAt", Instant.now().toString()
      ));
    }

    if (!dataStore.exists(DataCategory.TASK, TASK_ID)) {
      dataStore.upsert(DataCategory.TASK, map(
        "id", TASK_ID,
        "name", "机器人图片检测测试任务",
        "routeId", ROUTE_ID,
        "robotId", ROBOT_ID,
        "status", "CREATED",
        "progress", 0,
        "currentCheckpointSeq", 0,
        "createdAt", Instant.now().toString()
      ));
    }
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      result.put(values[i].toString(), values[i + 1]);
    }
    return result;
  }
}
