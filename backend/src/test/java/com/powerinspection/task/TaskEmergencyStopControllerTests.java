package com.powerinspection.task;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@TestPropertySource(
    properties = {"app.robot.mode=simulation", "app.robot.allow-registration=false"})
@SpringBootTest
@AutoConfigureMockMvc
class TaskEmergencyStopControllerTests {
  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @Autowired DataStoreService dataStore;

  @Test
  void adminCanEmergencyStopSimulationTaskWithReason() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String routeId = "route_estop_" + suffix;
    String robotId = "robot_estop_" + suffix;
    String taskId = "task_estop_" + suffix;
    String dispatcherToken = login("dispatcher", "Disp@123");
    String adminToken = login("admin", "Admin@123");
    ensureFixtures(routeId, robotId);

    try {
      mockMvc
          .perform(
              post("/api/v1/tasks")
                  .header("Authorization", "Bearer " + dispatcherToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
            {
              "id":"%s",
              "name":"急停接口测试",
              "routeId":"%s",
              "robotId":"%s"
            }
            """
                          .formatted(taskId, routeId, robotId)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("CREATED"));

      mockMvc
          .perform(
              post("/api/v1/tasks/" + taskId + "/dispatch")
                  .header("Authorization", "Bearer " + dispatcherToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("DISPATCHED"));

      mockMvc
          .perform(
              post("/api/v1/tasks/" + taskId + "/emergency-stop")
                  .header("Authorization", "Bearer " + adminToken)
                  .header("Idempotency-Key", "estop-" + suffix)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"reason\":\"现场人员闯入，立即停机\"}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.status").value("ESTOPPED"))
          .andExpect(jsonPath("$.data.emergencyStopReason").value("现场人员闯入，立即停机"));

      mockMvc
          .perform(
              get("/api/v1/tasks/" + taskId + "/events")
                  .header("Authorization", "Bearer " + adminToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.items[*].type", hasItem("ESTOP")));

      mockMvc
          .perform(
              post("/api/v1/tasks/" + taskId + "/cancel")
                  .header("Authorization", "Bearer " + adminToken)
                  .header("Idempotency-Key", "cancel-" + suffix)
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    } finally {
      cleanup(taskId, robotId, routeId);
    }
  }

  @Test
  void emergencyStopRequiresReasonAndEstopPermission() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    String routeId = "route_estop_perm_" + suffix;
    String robotId = "robot_estop_perm_" + suffix;
    String taskId = "task_estop_perm_" + suffix;
    String adminToken = login("admin", "Admin@123");
    String dispatcherToken = login("dispatcher", "Disp@123");
    ensureFixtures(routeId, robotId);

    try {
      mockMvc
          .perform(
              post("/api/v1/tasks")
                  .header("Authorization", "Bearer " + dispatcherToken)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
            {
              "id":"%s",
              "name":"急停权限测试",
              "routeId":"%s",
              "robotId":"%s"
            }
            """
                          .formatted(taskId, routeId, robotId)))
          .andExpect(status().isOk());

      mockMvc
          .perform(
              post("/api/v1/tasks/" + taskId + "/emergency-stop")
                  .header("Authorization", "Bearer " + adminToken)
                  .header("Idempotency-Key", "estop-blank-" + suffix)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"reason\":\"   \"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message", containsString("原因")));

      mockMvc
          .perform(
              post("/api/v1/tasks/" + taskId + "/emergency-stop")
                  .header("Authorization", "Bearer " + dispatcherToken)
                  .header("Idempotency-Key", "estop-disp-" + suffix)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"reason\":\"调度员不应有急停权限\"}"))
          .andExpect(status().isForbidden());
    } finally {
      cleanup(taskId, robotId, routeId);
    }
  }

  private void ensureFixtures(String routeId, String robotId) {
    if (dataStore.find(DataCategory.ROUTE, routeId) == null) {
      dataStore.upsert(
          DataCategory.ROUTE,
          map(
              "id",
              routeId,
              "siteId",
              "site_001",
              "name",
              "急停测试路线",
              "path",
              List.of(map("lat", 30.2741, "lng", 120.1551), map("lat", 30.2744, "lng", 120.1554)),
              "checkpoints",
              List.of()));
    }
    if (dataStore.find(DataCategory.ROBOT, robotId) == null) {
      dataStore.upsert(
          DataCategory.ROBOT,
          map(
              "id", robotId,
              "siteId", "site_001",
              "name", "急停测试机器人",
              "status", "ONLINE"));
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
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"username\":\""
                            + username
                            + "\",\"password\":\""
                            + password
                            + "\",\"remember\":true}"))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
    return root.path("data").path("token").asText();
  }
}
