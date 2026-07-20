package com.powerinspection.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.powerinspection.common.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RouteExecutorSupportTests {
  @Test
  void normalizeRouteAcceptsRouteJsonV2AndBuildsCompatibleCheckpoints() {
    Map<String, Object> route = map(
      "id", "route_test",
      "executorJson", validExecutor()
    );

    RouteExecutorSupport.normalizeRoute(route);

    assertEquals("ros2d", route.get("mapMode"));
    assertEquals(route.get("executorJson"), route.get("rosRoute"));
    List<Map<String, Object>> checkpoints = RouteExecutorSupport.compatibleCheckpoints(route);
    assertEquals(2, checkpoints.size());
    assertEquals("target_002", checkpoints.get(0).get("id"));
    assertEquals(1, checkpoints.get(0).get("seq"));
    assertEquals(4.2, ((Map<?, ?>) checkpoints.get(0).get("position")).get("x"));
    assertEquals(-0.6, ((Map<?, ?>) checkpoints.get(0).get("position")).get("y"));
    assertEquals(30, checkpoints.get(0).get("dwellSeconds"));
  }

  @Test
  void attachRosAliasFillsLegacyRouteDefaults() {
    Map<String, Object> route = map("id", "route_legacy", "siteId", "site_001");

    Map<String, Object> normalized = RouteExecutorSupport.attachRosAlias(route);

    assertEquals(List.of(), normalized.get("path"));
    assertEquals(List.of(), normalized.get("routeDetections"));
    assertEquals(List.of(), normalized.get("checkpoints"));
    assertEquals("2d", normalized.get("mapMode"));
  }

  @Test
  void validateRejectsMissingTargetReferences() {
    Map<String, Object> executor = validExecutor();
    Map<String, Object> routeDef = routeDefs(executor).get(0);
    routeDef.put("target_ids", List.of("target_404"));

    ApiException ex = assertThrows(ApiException.class, () -> RouteExecutorSupport.validate(executor));

    assertEquals("executorJson target_ids references missing target: target_404", ex.getMessage());
  }

  @Test
  void validateRejectsTargetWithoutPose() {
    Map<String, Object> executor = validExecutor();
    targetDefs(executor).get(0).remove("pose");

    ApiException ex = assertThrows(ApiException.class, () -> RouteExecutorSupport.validate(executor));

    assertEquals("executorJson.targets[0].pose must be an object", ex.getMessage());
  }

  @Test
  void validateAcceptsV3RouteWithMapIdentityAndKeepoutZone() {
    Map<String, Object> executor = validExecutorV3();

    RouteExecutorSupport.validate(executor);

    assertEquals(3, executor.get("version"));
  }

  @Test
  void validateRejectsV3LocationMismatch() {
    Map<String, Object> executor = validExecutorV3();
    @SuppressWarnings("unchecked")
    Map<String, Object> location = (Map<String, Object>) targetDefs(executor).get(0).get("location");
    location.put("yaw", 0.4);

    ApiException ex = assertThrows(ApiException.class, () -> RouteExecutorSupport.validate(executor));

    assertEquals("executorJson.targets[0].pose and location disagree on yaw", ex.getMessage());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> routeDefs(Map<String, Object> executor) {
    return (List<Map<String, Object>>) executor.get("routes");
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> targetDefs(Map<String, Object> executor) {
    return (List<Map<String, Object>>) executor.get("targets");
  }

  private Map<String, Object> validExecutor() {
    return map(
      "version", 2,
      "frame_id", "map",
      "active_route_id", "route_patrol_001",
      "start_pose", map(
        "name", "Initial pose",
        "pose", pose(0.5, -0.5, 0.2),
        "publish_initial_pose", true,
        "covariance", map("x", 0.25, "y", 0.25, "yaw", 0.0685)
      ),
      "targets", List.of(
        map("id", "target_001", "name", "Target 1", "pose", pose(2.5, 0.8, 1.2), "task_duration_sec", 25),
        map("id", "target_002", "name", "Target 2", "pose", pose(4.2, -0.6, 0.5), "task_duration_sec", 30)
      ),
      "routes", List.of(map(
        "id", "route_patrol_001",
        "name", "Main patrol route",
        "target_ids", List.of("target_002", "target_001"),
        "return_to_start", true,
        "loop", map("enabled", false, "wait_sec", 600, "max_cycles", 0),
        "goal_timeout_sec", 120,
        "max_retries_per_checkpoint", 1,
        "failure_policy", "abort_and_return_home"
      )),
      "schedules", List.of()
    );
  }

  private Map<String, Object> validExecutorV3() {
    Map<String, Object> executor = validExecutor();
    executor.put("version", 3);
    executor.put("map", map(
      "yaml", "my_map.yaml",
      "image", "my_map.pgm",
      "resolution", 0.025,
      "origin", List.of(-2.89, -6.37, 0.0),
      "width", 1024,
      "height", 1024,
      "image_sha256", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    ));
    Map<String, Object> start = (Map<String, Object>) executor.get("start_pose");
    start.put("frame_id", "map");
    start.put("location", location((Map<String, Object>) start.get("pose")));
    for (Map<String, Object> target : targetDefs(executor)) {
      target.put("location", location((Map<String, Object>) target.get("pose")));
    }
    executor.put("keepout_zones", List.of(map(
      "id", "keepout_001",
      "type", "hard_keepout",
      "enabled", true,
      "mask_padding_m", 0.025,
      "polygon", List.of(map("x", 5.0, "y", 5.0), map("x", 6.0, "y", 5.0), map("x", 5.0, "y", 6.0))
    )));
    return executor;
  }

  private Map<String, Object> location(Map<String, Object> pose) {
    return map("type", "map_pose", "frame_id", "map", "x", pose.get("x"), "y", pose.get("y"), "yaw", pose.get("yaw"));
  }

  private Map<String, Object> pose(double x, double y, double yaw) {
    return map("x", x, "y", y, "yaw", yaw);
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> item = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      item.put(values[i].toString(), values[i + 1]);
    }
    return item;
  }
}
