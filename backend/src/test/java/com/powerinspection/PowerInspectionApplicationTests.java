package com.powerinspection;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.task.TaskService;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PowerInspectionApplicationTests {
  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  TaskService taskService;

  @Test
  void defaultUsersCanLoginAndRolePermissionsAreEnforced() throws Exception {
    String adminToken = login("admin", "Admin@123");
    String dispatcherToken = login("dispatcher", "Disp@123");
    String viewerToken = login("viewer", "View@123");

    mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.username").value("admin"))
      .andExpect(jsonPath("$.data.role").value("ADMIN"));

    mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.username").value("dispatcher"))
      .andExpect(jsonPath("$.data.role").value("DISPATCHER"));

    mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(viewerToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.username").value("viewer"))
      .andExpect(jsonPath("$.data.role").value("VIEWER"));

    mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(3)));

    mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(viewerToken)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.message").value("无权限访问"));

    mockMvc.perform(get("/api/v1/users").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.message").value("无权限访问"));

    mockMvc.perform(post("/api/v1/tasks")
        .header("Authorization", bearer(viewerToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", "task_viewer_denied", "name", "viewer denied")))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.message").value("无权限访问"));
  }

  @Test
  void dispatcherCanCreateAndDispatchTask() throws Exception {
    String token = login("dispatcher", "Disp@123");

    mockMvc.perform(get("/api/v1/sites").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").exists());

    String body = """
      {
        "id":"task_test_001",
        "name":"接口测试巡检",
        "routeId":"route_demo_001",
        "robotId":"robot_001",
        "status":"CREATED",
        "progress":0,
        "currentCheckpointSeq":0,
        "createdAt":"2026-06-18T00:00:00Z"
      }
      """;

    mockMvc.perform(post("/api/v1/tasks")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("CREATED"));

    mockMvc.perform(post("/api/v1/tasks/task_test_001/dispatch").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("DISPATCHED"));

    mockMvc.perform(post("/api/v1/tasks/task_test_001/cancel").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("CANCELLED"));

    mockMvc.perform(delete("/api/v1/tasks/task_test_001").header("Authorization", bearer(token)))
      .andExpect(status().isOk());
  }

  @Test
  void authProfilePasswordPreferencesAndActivitiesFlowWorks() throws Exception {
    String username = "tester_api";
    String password = "Tester123";
    String newPassword = "Tester456";

    mockMvc.perform(post("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(
          "username", username,
          "password", password,
          "confirmPassword", password,
          "displayName", "测试用户",
          "phone", "13800009999",
          "agreed", true
        )))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.username").value(username))
      .andExpect(jsonPath("$.data.role").value("VIEWER"));

    String token = login(username, password);

    mockMvc.perform(patch("/api/v1/users/me")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("displayName", "测试用户改名", "bio", "集成测试资料")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.displayName").value("测试用户改名"));

    mockMvc.perform(get("/api/v1/users/me/activities").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));

    mockMvc.perform(get("/api/v1/users/me/preferences").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.notifyAlarm").isBoolean());

    mockMvc.perform(put("/api/v1/users/me/preferences")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("notifyAlarm", false, "notifyTask", true, "notifySystem", true, "defaultSiteId", "site_001", "sidebarCollapsed", true)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.notifyAlarm").value(false))
      .andExpect(jsonPath("$.data.defaultSiteId").value("site_001"));

    mockMvc.perform(put("/api/v1/auth/password")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("oldPassword", password, "newPassword", newPassword, "confirmPassword", newPassword)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0));

    login(username, newPassword);
  }

  @Test
  void apiSectionCrudResourcesAreCovered() throws Exception {
    String adminToken = login("admin", "Admin@123");
    String dispatcherToken = login("dispatcher", "Disp@123");

    mockMvc.perform(post("/api/v1/sites")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(
          "id", "site_test_api",
          "name", "接口测试站点",
          "address", "测试地址",
          "description", "测试站点描述",
          "lat", 30.1,
          "lng", 120.1
        )))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value("site_test_api"));

    mockMvc.perform(get("/api/v1/sites/site_test_api").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("接口测试站点"));

    mockMvc.perform(patch("/api/v1/sites/site_test_api")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("description", "已更新站点描述")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.description").value("已更新站点描述"));

    mockMvc.perform(post("/api/v1/sites/site_test_api/areas")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", "area_test_api", "name", "接口测试区域", "points", java.util.List.of())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.siteId").value("site_test_api"));

    mockMvc.perform(get("/api/v1/sites/site_test_api/areas").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].siteId").value("site_test_api"));

    mockMvc.perform(patch("/api/v1/sites/site_test_api/areas/area_test_api")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("name", "接口测试区域-更新")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("接口测试区域-更新"));

    mockMvc.perform(post("/api/v1/routes")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", "route_test_api", "name", "接口测试路线", "siteId", "site_test_api")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value("route_test_api"));

    mockMvc.perform(get("/api/v1/routes/route_test_api").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("接口测试路线"));

    mockMvc.perform(post("/api/v1/routes/route_test_api/checkpoints")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", "cp_test_api", "name", "接口测试检查点", "pan", 0, "tilt", -10)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value("cp_test_api"));

    mockMvc.perform(get("/api/v1/routes/route_test_api/checkpoints").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").value("cp_test_api"));

    mockMvc.perform(patch("/api/v1/routes/route_test_api/checkpoints/cp_test_api")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("name", "接口测试检查点-更新")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("接口测试检查点-更新"));

    mockMvc.perform(post("/api/v1/robots")
        .header("Authorization", bearer(adminToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", "robot_test_api", "name", "接口测试机器人", "model", "TestBot", "serialNo", "TB-001", "siteId", "site_test_api", "status", "ONLINE", "battery", 88)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value("robot_test_api"));

    mockMvc.perform(get("/api/v1/robots/robot_test_api").header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("接口测试机器人"));

    mockMvc.perform(get("/api/v1/robots/robot_test_api/telemetry").header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("ONLINE"));

    mockMvc.perform(patch("/api/v1/robots/robot_test_api")
        .header("Authorization", bearer(adminToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("battery", 77)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.battery").value(77));

    mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(adminToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", "tpl_test_api", "name", "接口测试模板", "scope", "ROUTE", "types", java.util.List.of("PERSON"), "prompt", "测试提示词")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value("tpl_test_api"));

    mockMvc.perform(get("/api/v1/detection-templates/tpl_test_api").header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("接口测试模板"));

    mockMvc.perform(patch("/api/v1/detection-templates/tpl_test_api")
        .header("Authorization", bearer(adminToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("name", "接口测试模板-更新")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("接口测试模板-更新"));

    mockMvc.perform(delete("/api/v1/detection-templates/tpl_test_api").header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk());
    mockMvc.perform(delete("/api/v1/robots/robot_test_api").header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk());
    mockMvc.perform(delete("/api/v1/routes/route_test_api/checkpoints/cp_test_api").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk());
    mockMvc.perform(delete("/api/v1/routes/route_test_api").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk());
    mockMvc.perform(delete("/api/v1/sites/site_test_api/areas/area_test_api").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk());
    mockMvc.perform(delete("/api/v1/sites/site_test_api").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk());
  }

  @Test
  void taskAlarmWorkOrderRecordNotificationApisWork() throws Exception {
    String dispatcherToken = login("dispatcher", "Disp@123");
    String adminToken = login("admin", "Admin@123");

    mockMvc.perform(post("/api/v1/tasks")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(
          "id", "task_flow_api",
          "name", "完整流程巡检",
          "routeId", "route_demo_001",
          "robotId", "robot_001",
          "status", "CREATED",
          "progress", 96,
          "currentCheckpointSeq", 0,
          "createdAt", "2026-06-18T00:00:00Z"
        )))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value("task_flow_api"));

    mockMvc.perform(get("/api/v1/tasks/task_flow_api").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.progress").value(96));

    mockMvc.perform(patch("/api/v1/tasks/task_flow_api")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("name", "完整流程巡检-更新")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("完整流程巡检-更新"));

    mockMvc.perform(post("/api/v1/tasks/task_flow_api/dispatch").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("DISPATCHED"));

    taskService.tick();
    taskService.tick();

    mockMvc.perform(get("/api/v1/tasks/task_flow_api/events").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));

    mockMvc.perform(get("/api/v1/records").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[?(@.taskId == 'task_flow_api')]").exists());

    mockMvc.perform(post("/api/v1/records/export").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(header().string("Content-Disposition", containsString("inspection-records.csv")))
      .andExpect(content().string(containsString("taskName,routeName,robotName")));

    mockMvc.perform(get("/api/v1/alarms").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));

    mockMvc.perform(post("/api/v1/alarms/alarm_seed_001/ack").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.acknowledged").value(true));

    mockMvc.perform(post("/api/v1/alarms/ack-all").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk());

    String workOrderId = postAndReadId("/api/v1/work-orders/from-alarm/alarm_seed_003", dispatcherToken, json("assigneeName", "张调度"));

    mockMvc.perform(get("/api/v1/work-orders/" + workOrderId).header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.alarmId").value("alarm_seed_003"));

    mockMvc.perform(patch("/api/v1/work-orders/" + workOrderId)
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("priority", "LOW")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.priority").value("LOW"));

    mockMvc.perform(patch("/api/v1/work-orders/" + workOrderId + "/assign")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("assigneeName", "李观察")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.assigneeName").value("李观察"));

    mockMvc.perform(patch("/api/v1/work-orders/" + workOrderId + "/status")
        .header("Authorization", bearer(dispatcherToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("status", "CLOSED", "resolution", "已复核")))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.closedAt", not(blankOrNullString())));

    mockMvc.perform(get("/api/v1/notifications").header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));

    mockMvc.perform(patch("/api/v1/notifications/ntf_seed_admin/read").header("Authorization", bearer(adminToken)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.read").value(true));

    mockMvc.perform(delete("/api/v1/work-orders/" + workOrderId).header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk());
    mockMvc.perform(delete("/api/v1/tasks/task_flow_api").header("Authorization", bearer(dispatcherToken)))
      .andExpect(status().isOk());
  }

  private String login(String username, String password) throws Exception {
    String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"remember\":true}";
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value(0))
      .andReturn()
      .getResponse()
      .getContentAsString(StandardCharsets.UTF_8);
    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("token").asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private String postAndReadId(String path, String token, String body) throws Exception {
    String response = mockMvc.perform(post(path)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").exists())
      .andReturn()
      .getResponse()
      .getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("id").asText();
  }

  private String json(Object... values) throws Exception {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      map.put(String.valueOf(values[i]), values[i + 1]);
    }
    return objectMapper.writeValueAsString(map);
  }
}
