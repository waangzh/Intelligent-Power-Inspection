package com.powerinspection.task;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@TestPropertySource(properties = {
  "app.robot.mode=simulation",
  "app.robot.allow-registration=false"
})
@SpringBootTest
@AutoConfigureMockMvc
class TaskMutationControllerTests {
  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  DataStoreService dataStore;

  @Test
  void createRejectsServerManagedFields() throws Exception {
    String token = login("dispatcher", "Disp@123");
    String routeId = ensureRoute();

    try {
      mockMvc.perform(post("/api/v1/tasks")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "name":"绕过状态机",
              "routeId":"%s",
              "robotId":"robot_001",
              "status":"RUNNING",
              "progress":100,
              "completedAt":"2026-01-01T00:00:00Z"
            }
            """.formatted(routeId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("服务端字段")));
    } finally {
      dataStore.delete(DataCategory.ROUTE, routeId);
    }
  }

  @Test
  void patchCannotBypassSimulationTaskStateMachine() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String routeId = "route_mut_" + suffix;
    String robotId = "robot_mut_" + suffix;
    String taskId = "task_mut_" + suffix;
    String token = login("dispatcher", "Disp@123");
    ensureFixtures(routeId, robotId);

    try {
      mockMvc.perform(post("/api/v1/tasks")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "id":"%s",
              "name":"状态机测试",
              "routeId":"%s",
              "robotId":"%s"
            }
            """.formatted(taskId, routeId, robotId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CREATED"));

      mockMvc.perform(patch("/api/v1/tasks/" + taskId)
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"status\":\"RUNNING\",\"progress\":100}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("服务端字段")));

      mockMvc.perform(post("/api/v1/tasks/" + taskId + "/dispatch")
          .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

      mockMvc.perform(delete("/api/v1/tasks/" + taskId)
          .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("删除")));
    } finally {
      cleanup(taskId, robotId, routeId);
    }
  }

  @Test
  void createdTaskAllowsMetadataPatchBeforeDispatch() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String routeId = "route_meta_" + suffix;
    String robotId = "robot_meta_" + suffix;
    String taskId = "task_meta_" + suffix;
    String token = login("dispatcher", "Disp@123");
    ensureFixtures(routeId, robotId);

    try {
      String created = mockMvc.perform(post("/api/v1/tasks")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "id":"%s",
              "name":"元数据测试",
              "routeId":"%s",
              "robotId":"%s",
              "note":"初始备注"
            }
            """.formatted(taskId, routeId, robotId)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
      long version = objectMapper.readTree(created).path("data").path("version").asLong();

      mockMvc.perform(patch("/api/v1/tasks/" + taskId)
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"name\":\"元数据测试-更新\",\"note\":\"更新备注\",\"version\":" + version + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("元数据测试-更新"))
        .andExpect(jsonPath("$.data.note").value("更新备注"))
        .andExpect(jsonPath("$.data.status").value("CREATED"));
    } finally {
      cleanup(taskId, robotId, routeId);
    }
  }

  @Test
  void createCannotOverwriteExistingTaskId() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String routeId = "route_idov_" + suffix;
    String robotId = "robot_idov_" + suffix;
    String taskId = "task_idov_" + suffix;
    String token = login("dispatcher", "Disp@123");
    ensureFixtures(routeId, robotId);

    try {
      mockMvc.perform(post("/api/v1/tasks")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "id":"%s",
              "name":"首次创建",
              "routeId":"%s",
              "robotId":"%s"
            }
            """.formatted(taskId, routeId, robotId)))
        .andExpect(status().isOk());

      mockMvc.perform(post("/api/v1/tasks/" + taskId + "/dispatch")
          .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DISPATCHED"));

      mockMvc.perform(post("/api/v1/tasks")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "id":"%s",
              "name":"试图覆盖",
              "routeId":"%s",
              "robotId":"%s"
            }
            """.formatted(taskId, routeId, robotId)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", containsString("不能通过创建接口覆盖")));

      mockMvc.perform(post("/api/v1/tasks/" + taskId + "/cancel")
          .header("Authorization", "Bearer " + token)
          .header("Idempotency-Key", "cancel-idov-" + suffix))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    } finally {
      cleanup(taskId, robotId, routeId);
    }
  }

  @Test
  void createRejectsClientSiteId() throws Exception {
    String token = login("dispatcher", "Disp@123");
    String routeId = ensureRoute();
    try {
      mockMvc.perform(post("/api/v1/tasks")
          .header("Authorization", "Bearer " + token)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "name":"站点伪造",
              "routeId":"%s",
              "robotId":"robot_001",
              "siteId":"site_spoof"
            }
            """.formatted(routeId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("服务端字段")));
    } finally {
      dataStore.delete(DataCategory.ROUTE, routeId);
    }
  }

  private String ensureRoute() {
    String routeId = "route_mut_guard_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    dataStore.upsert(DataCategory.ROUTE, map(
      "id", routeId,
      "siteId", "site_001",
      "name", "状态机测试路线",
      "path", List.of(map("lat", 30.2741, "lng", 120.1551)),
      "checkpoints", List.of()
    ));
    return routeId;
  }

  private void ensureFixtures(String routeId, String robotId) {
    if (dataStore.find(DataCategory.ROUTE, routeId) == null) {
      dataStore.upsert(DataCategory.ROUTE, map(
        "id", routeId,
        "siteId", "site_001",
        "name", "状态机测试路线",
        "path", List.of(map("lat", 30.2741, "lng", 120.1551)),
        "checkpoints", List.of()
      ));
    }
    if (dataStore.find(DataCategory.ROBOT, robotId) == null) {
      dataStore.upsert(DataCategory.ROBOT, map(
        "id", robotId,
        "siteId", "site_001",
        "name", "状态机测试机器人",
        "status", "ONLINE"
      ));
    }
  }

  private void cleanup(String taskId, String robotId, String routeId) {
    if (dataStore.find(DataCategory.TASK, taskId) != null) {
      dataStore.delete(DataCategory.TASK, taskId);
    }
    if (dataStore.find(DataCategory.ROBOT, robotId) != null) {
      dataStore.delete(DataCategory.ROBOT, robotId);
    }
    if (dataStore.find(DataCategory.ROUTE, routeId) != null) {
      dataStore.delete(DataCategory.ROUTE, routeId);
    }
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> item = new LinkedHashMap<>();
    for (int index = 0; index + 1 < values.length; index += 2) {
      item.put(String.valueOf(values[index]), values[index + 1]);
    }
    return item;
  }

  private String login(String username, String password) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"remember\":true}"))
      .andExpect(status().isOk())
      .andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    return root.path("data").path("token").asText();
  }
}
