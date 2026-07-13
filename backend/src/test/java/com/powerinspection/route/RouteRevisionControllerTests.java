package com.powerinspection.route;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
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

  @Test
  void v2DraftWithMapAssetCreatesStableV3Revision() throws Exception {
    String token = login("dispatcher", "Disp@123");
    String siteId = "site_route_revision";
    String routeId = "route_route_revision";
    mockMvc.perform(post("/api/v1/sites")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", siteId, "name", "路线修订测试站点")))
      .andExpect(status().isOk());
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

    String adminToken = login("admin", "Admin@123");
    String robotId = "robot_route_revision";
    mockMvc.perform(post("/api/v1/robots")
        .header("Authorization", bearer(adminToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", robotId, "name", "路线修订测试机器人", "siteId", siteId)))
      .andExpect(status().isOk());

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
        .header("Authorization", bearer(token)))
      .andExpect(status().isConflict());

    mockMvc.perform(delete("/api/v1/routes/{id}", routeId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk());

    mockMvc.perform(delete("/api/v1/map-assets/{id}", mapId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isConflict());
  }

  private String createMapAsset(String siteId) {
    String mapId = "map_route_revision";
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
