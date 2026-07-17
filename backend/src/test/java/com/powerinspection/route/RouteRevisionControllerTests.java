package com.powerinspection.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.route.RouteRevisionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.robot.BridgeRobotSnapshot;
import com.powerinspection.robot.RobotHeartbeatService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class RouteRevisionControllerTests {
  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  DataStoreService dataStore;

  @Autowired
  RouteRevisionRepository routeRevisionRepository;

  @Autowired
  RobotHeartbeatService heartbeatService;

  @Test
  void draftValidationOverwritesMapIdentityReportsAllIssuesAndDoesNotPersist() throws Exception {
    String token = login("dispatcher", "Disp@123");
    String siteId = "site_draft_validate";
    String routeId = "route_draft_validate";
    mockMvc.perform(post("/api/v1/sites")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", siteId, "name", "草稿校验测试站点")))
      .andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/routes")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", routeId, "siteId", siteId, "name", "草稿校验测试路线")))
      .andExpect(status().isOk());
    String mapId = createMapAsset(siteId, "map_draft_validate");
    mockMvc.perform(patch("/api/v1/routes/{id}", routeId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("mapId", mapId, "executorJson", validV3Executor())))
      .andExpect(status().isOk());

    String routeBefore = objectMapper.writeValueAsString(dataStore.get(DataCategory.ROUTE, routeId));
    long revisionsBefore = routeRevisionRepository.count();
    Map<String, Object> forgedMap = validV3Executor();
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) forgedMap.get("map");
    map.put("yaml", "forged.yaml");
    map.put("image_sha256", "c".repeat(64));
    map.put("vendor_extension", "preserve");
    String normalized = mockMvc.perform(post("/api/v1/routes/{id}/draft:validate", routeId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("executorJson", forgedMap)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.valid").value(true))
      .andExpect(jsonPath("$.data.normalizedExecutorJson.map.yaml").value("floor.yaml"))
      .andExpect(jsonPath("$.data.normalizedExecutorJson.map.image_sha256").value("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
      .andExpect(jsonPath("$.data.normalizedExecutorJson.map.vendor_extension").value("preserve"))
      .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertEquals(mapId, objectMapper.readTree(normalized).path("data").path("mapAssetId").asText());
    assertEquals(revisionsBefore, routeRevisionRepository.count());
    assertEquals(routeBefore, objectMapper.writeValueAsString(dataStore.get(DataCategory.ROUTE, routeId)));

    Map<String, Object> invalid = validV3Executor();
    @SuppressWarnings("unchecked")
    Map<String, Object> startLocation = (Map<String, Object>) ((Map<String, Object>) invalid.get("start_pose")).get("location");
    startLocation.put("x", 99.0);
    @SuppressWarnings("unchecked")
    Map<String, Object> onlyRoute = (Map<String, Object>) ((List<?>) invalid.get("routes")).get(0);
    onlyRoute.put("goal_timeout_sec", 0);
    invalid.put("schedules", List.of(map("id", "schedule_1")));
    String invalidResponse = mockMvc.perform(post("/api/v1/routes/{id}/draft:validate", routeId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("executorJson", invalid)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.valid").value(false))
      .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    List<String> pointers = new java.util.ArrayList<>();
    for (var issue : objectMapper.readTree(invalidResponse).path("data").path("issues")) pointers.add(issue.path("jsonPointer").asText());
    org.junit.jupiter.api.Assertions.assertTrue(pointers.contains("/start_pose/location/x"));
    org.junit.jupiter.api.Assertions.assertTrue(pointers.contains("/routes/0/goal_timeout_sec"));
    org.junit.jupiter.api.Assertions.assertTrue(pointers.contains("/schedules"));
    assertEquals(revisionsBefore, routeRevisionRepository.count());
    assertEquals(routeBefore, objectMapper.writeValueAsString(dataStore.get(DataCategory.ROUTE, routeId)));

    mockMvc.perform(post("/api/v1/routes/{id}/draft:validate", routeId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("executorJson", validV2Executor())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.valid").value(false))
      .andExpect(jsonPath("$.data.issues[0].code").value("INVALID_VERSION"));
  }

  @Test
  void v2DraftWithMapAssetCreatesStableV3Revision() throws Exception {
    String token = login("dispatcher", "Disp@123");
    String siteId = "site_001";
    String routeId = "route_route_revision";
    mockMvc.perform(post("/api/v1/routes")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", routeId, "siteId", siteId, "name", "路线修订测试路线")))
      .andExpect(status().isOk());

    String mapId = createMapAsset(siteId);
    mockMvc.perform(patch("/api/v1/routes/{id}", routeId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("mapId", mapId, "executorJson", validV2Executor())))
      .andExpect(status().isOk());

    String first = mockMvc.perform(post("/api/v1/routes/{id}/revisions", routeId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.revisionNo").value(1))
      .andExpect(jsonPath("$.data.executorJson.version").value(3))
      .andExpect(jsonPath("$.data.executorJson.map.yaml").value("floor.yaml"))
      .andExpect(jsonPath("$.data.executorJson.map.vendor_extension").value("preserve"))
      .andExpect(jsonPath("$.data.executorJson.start_pose.frame_id").value("map"))
      .andExpect(jsonPath("$.data.executorJson.targets[0].location.type").value("map_pose"))
      .andExpect(jsonPath("$.data.executorJson.keepout_zones").isArray())
      .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    String revisionId = objectMapper.readTree(first).path("data").path("id").asText();

    mockMvc.perform(post("/api/v1/routes/{id}/revisions", routeId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(revisionId))
      .andExpect(jsonPath("$.data.revisionNo").value(1));

    String robotId = "robot_001";
    Instant heartbeatAt = Instant.now();
    heartbeatService.applyBridgeSnapshot(new BridgeRobotSnapshot(robotId, heartbeatAt, "1.0", "test-boot", "idle", "test", 0, Map.of()), heartbeatAt);

    mockMvc.perform(post("/api/v1/route-revisions/{id}/deployments", revisionId)
        .header("Authorization", bearer(token))
        .header("Idempotency-Key", "deploy:route-revision-test:1")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("robotId", robotId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.state").value("PENDING"));

    mockMvc.perform(post("/api/v1/tasks")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", "task_route_revision", "name", "修订绑定任务", "routeRevisionId", revisionId, "robotId", robotId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.routeId").value(routeId))
      .andExpect(jsonPath("$.data.executionId").isNotEmpty())
      .andExpect(jsonPath("$.data.routeContentSha256").isNotEmpty());

    mockMvc.perform(post("/api/v1/tasks/task_route_revision/dispatch")
        .header("Authorization", bearer(token)))
      .andExpect(status().isConflict());

    mockMvc.perform(patch("/api/v1/tasks/task_route_revision")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("status", "DISPATCHED")))
      .andExpect(status().isBadRequest());

    mockMvc.perform(post("/api/v1/tasks/task_route_revision/pause")
        .header("Authorization", bearer(token))
        .header("Idempotency-Key", "pause-route-revision"))
      .andExpect(status().isConflict());

    mockMvc.perform(delete("/api/v1/tasks/task_route_revision")
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk());

    mockMvc.perform(delete("/api/v1/routes/{id}", routeId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk());

    mockMvc.perform(delete("/api/v1/map-assets/{id}", mapId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isConflict());
  }

  @Test
  void persistedDraftNormalizesRetainsLastPublishableReportAndChecksVersion() throws Exception {
    String token = login("dispatcher", "Disp@123");
    String siteId = "site_draft_persistence";
    String routeId = "route_draft_persistence";
    mockMvc.perform(post("/api/v1/sites")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", siteId, "name", "草稿持久化测试站点")))
      .andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/routes")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", routeId, "siteId", siteId, "name", "草稿持久化测试路线")))
      .andExpect(status().isOk());
    String mapId = createMapAsset(siteId, "map_draft_persistence");

    Map<String, Object> valid = validV3Executor();
    @SuppressWarnings("unchecked")
    Map<String, Object> forgedMap = (Map<String, Object>) valid.get("map");
    forgedMap.put("yaml", "forged.yaml");
    long revisionsBefore = routeRevisionRepository.count();
    mockMvc.perform(put("/api/v1/routes/{id}/draft", routeId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("executorJson", valid, "mapAssetId", mapId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.valid").value(true))
      .andExpect(jsonPath("$.data.publishable").value(true))
      .andExpect(jsonPath("$.data.normalizedExecutorJson.map.yaml").value("floor.yaml"))
      .andExpect(jsonPath("$.data.draft.version").value(0));
    assertEquals(revisionsBefore, routeRevisionRepository.count());

    mockMvc.perform(get("/api/v1/routes/{id}/draft", routeId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.normalizedExecutorJson.map.yaml").value("floor.yaml"))
      .andExpect(jsonPath("$.data.checkedAt").isNotEmpty())
      .andExpect(jsonPath("$.data.publishable").value(true));

    Map<String, Object> invalid = validV3Executor();
    @SuppressWarnings("unchecked")
    Map<String, Object> startLocation = (Map<String, Object>) ((Map<String, Object>) invalid.get("start_pose")).get("location");
    startLocation.put("x", 99.0);
    mockMvc.perform(put("/api/v1/routes/{id}/draft", routeId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("executorJson", invalid, "mapAssetId", mapId, "expectedVersion", 0)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.valid").value(false))
      .andExpect(jsonPath("$.data.publishable").value(false))
      .andExpect(jsonPath("$.data.draft.lastPublishable.checkedAt").isNotEmpty());

    mockMvc.perform(get("/api/v1/routes/{id}/draft:check", routeId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.valid").value(false))
      .andExpect(jsonPath("$.data.publishable").value(false));

    mockMvc.perform(put("/api/v1/routes/{id}/draft", routeId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("executorJson", validV3Executor(), "mapAssetId", mapId, "expectedVersion", 0)))
      .andExpect(status().isConflict());

    Map<String, Object> warning = validV3Executor();
    warning.put("targets", List.of());
    @SuppressWarnings("unchecked")
    Map<String, Object> onlyRoute = (Map<String, Object>) ((List<?>) warning.get("routes")).get(0);
    onlyRoute.put("target_ids", List.of());
    mockMvc.perform(put("/api/v1/routes/{id}/draft", routeId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("executorJson", warning, "mapAssetId", mapId, "expectedVersion", 1)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.valid").value(true))
      .andExpect(jsonPath("$.data.publishable").value(true))
      .andExpect(jsonPath("$.data.issues[0].severity").value("WARNING"));
  }

  private String createMapAsset(String siteId) {
    return createMapAsset(siteId, "map_route_revision");
  }

  private String createMapAsset(String siteId, String mapId) {
    dataStore.upsert(DataCategory.MAP_ASSET, map(
      "id", mapId,
      "siteId", siteId,
      "status", "AVAILABLE",
      "yamlName", "floor.yaml",
      "pgmName", "floor.pgm",
      "image", "floor.pgm",
      "resolution", 0.05,
      "origin", List.of(0.0, 0.0, 0.0),
      "width", 2,
      "height", 1,
      "yamlSha256", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
      "pgmSha256", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    ));
    return mapId;
  }

  private Map<String, Object> validV2Executor() {
    return map(
      "version", 2,
      "frame_id", "map",
      "map", map("vendor_extension", "preserve"),
      "active_route_id", "route_patrol_001",
      "start_pose", map(
        "name", "起点",
        "pose", map("x", 0.2, "y", 0.2, "yaw", 0.0),
        "publish_initial_pose", true,
        "covariance", map("x", 0.25, "y", 0.25, "yaw", 0.0685)
      ),
      "targets", List.of(map(
        "id", "target_001",
        "name", "巡检点",
        "pose", map("x", 0.4, "y", 0.4, "yaw", 0.0),
        "task_duration_sec", 5
      )),
      "routes", List.of(map(
        "id", "route_patrol_001",
        "name", "测试路线",
        "target_ids", List.of("target_001"),
        "return_to_start", true,
        "loop", map("enabled", false, "wait_sec", 0, "max_cycles", 0),
        "goal_timeout_sec", 120,
        "max_retries_per_checkpoint", 0,
        "failure_policy", "abort"
      )),
      "schedules", List.of()
    );
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> validV3Executor() {
    Map<String, Object> executor = objectMapper.convertValue(validV2Executor(), Map.class);
    executor.put("version", 3);
    executor.put("map", map(
      "yaml", "client.yaml", "image", "client.pgm", "resolution", 1.0,
      "origin", List.of(1.0, 2.0, 3.0), "width", 9, "height", 9,
      "image_sha256", "a".repeat(64)
    ));
    Map<String, Object> start = (Map<String, Object>) executor.get("start_pose");
    start.put("frame_id", "map");
    start.put("location", mapPose((Map<String, Object>) start.get("pose")));
    Map<String, Object> target = (Map<String, Object>) ((List<?>) executor.get("targets")).get(0);
    target.put("location", mapPose((Map<String, Object>) target.get("pose")));
    executor.put("keepout_zones", List.of());
    return executor;
  }

  private Map<String, Object> mapPose(Map<String, Object> pose) {
    return map("type", "map_pose", "frame_id", "map", "x", pose.get("x"), "y", pose.get("y"), "yaw", pose.get("yaw"));
  }

  private String login(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("username", username, "password", password, "remember", true)))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("token").asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private String json(Object... values) throws Exception {
    return objectMapper.writeValueAsString(map(values));
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> item = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) item.put(String.valueOf(values[i]), values[i + 1]);
    return item;
  }
}
