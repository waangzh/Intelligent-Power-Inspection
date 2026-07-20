package com.powerinspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.task.TaskService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@TestPropertySource(
    properties = {"app.robot.mode=simulation", "app.robot.allow-registration=false"})
@SpringBootTest
@AutoConfigureMockMvc
class PowerInspectionApplicationTests {
  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @Autowired TaskService taskService;

  @Autowired DataStoreService dataStore;

  @Test
  void defaultUsersCanLoginAndRolePermissionsAreEnforced() throws Exception {
    String adminToken = login("admin", "Admin@123");
    String dispatcherToken = login("dispatcher", "Disp@123");
    String viewerToken = login("viewer", "View@123");

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.username").value("admin"))
        .andExpect(jsonPath("$.data.user.role").value("ADMIN"))
        .andExpect(jsonPath("$.data.permissions").isArray())
        .andExpect(jsonPath("$.data.permissions[?(@ == 'workorder:view')]").exists())
        .andExpect(jsonPath("$.data.permissions[?(@ == 'task:dispatch')]").doesNotExist());

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.username").value("dispatcher"))
        .andExpect(jsonPath("$.data.user.role").value("DISPATCHER"))
        .andExpect(jsonPath("$.data.permissions[?(@ == 'task:dispatch')]").exists());

    mockMvc
        .perform(get("/api/v1/auth/me").header("Authorization", bearer(viewerToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.user.username").value("viewer"))
        .andExpect(jsonPath("$.data.user.role").value("VIEWER"))
        .andExpect(jsonPath("$.data.permissions.length()").value(1));

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(3)));

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(viewerToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("无权限访问"));

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("无权限访问"));

    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", bearer(viewerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("id", "task_viewer_denied", "name", "viewer denied")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("无权限访问"));

    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("id", "task_admin_denied", "name", "admin denied")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("无权限访问"));

    mockMvc
        .perform(
            post("/api/v1/alarms/alarm_seed_003/ack").header("Authorization", bearer(adminToken)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("无权限访问"));
  }

  @Test
  void loginPermissionsMatchGeneratedManifest() throws Exception {
    Path manifestPath = Path.of("..", "shared", "generated", "permissions.json");
    JsonNode manifest =
        objectMapper.readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));
    for (String role : List.of("ADMIN", "DISPATCHER", "VIEWER")) {
      String password =
          "ADMIN".equals(role) ? "Admin@123" : "DISPATCHER".equals(role) ? "Disp@123" : "View@123";
      String username = role.toLowerCase();
      String token = login(username, password);
      String body =
          mockMvc
              .perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString(StandardCharsets.UTF_8);
      JsonNode permissions = objectMapper.readTree(body).path("data").path("permissions");
      JsonNode expected = manifest.path("byRole").path(role);
      if (permissions.size() != expected.size()) {
        throw new AssertionError(
            role
                + " permissions size mismatch: api="
                + permissions.size()
                + " manifest="
                + expected.size());
      }
      for (JsonNode perm : expected) {
        boolean found = false;
        for (JsonNode actual : permissions) {
          if (perm.asText().equals(actual.asText())) {
            found = true;
            break;
          }
        }
        if (!found) {
          throw new AssertionError(role + " missing permission from manifest: " + perm.asText());
        }
      }
    }
  }

  @Test
  void dispatcherCanCreateAndDispatchTask() throws Exception {
    String token = login("dispatcher", "Disp@123");
    String routeId = "route_task_dispatch_test";
    ensureTestRoute(routeId);

    mockMvc
        .perform(get("/api/v1/sites").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].id").exists())
        .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)));

    String body =
        """
      {
        "id":"task_test_001",
        "name":"接口测试巡检",
        "routeId":"route_task_dispatch_test",
        "robotId":"robot_001"
      }
      """;

    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CREATED"));

    mockMvc
        .perform(
            post("/api/v1/tasks/task_test_001/dispatch").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DISPATCHED"));

    mockMvc
        .perform(
            post("/api/v1/tasks/task_test_001/cancel")
                .header("Authorization", bearer(token))
                .header("Idempotency-Key", "cancel-task-test-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"));

    mockMvc
        .perform(delete("/api/v1/tasks/task_test_001").header("Authorization", bearer(token)))
        .andExpect(status().isOk());
    dataStore.delete(DataCategory.ROUTE, routeId);
  }

  @Test
  void authProfilePasswordPreferencesAndActivitiesFlowWorks() throws Exception {
    String username = "tester_api";
    String password = "Tester123";
    String newPassword = "Tester456";

    String phone = "13800009999";
    String smsCode = sendRegisterSmsCode(phone);
    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "username", username,
                        "password", password,
                        "confirmPassword", password,
                        "displayName", "测试用户",
                        "phone", phone,
                        "smsCode", smsCode,
                        "agreed", true)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value(username))
        .andExpect(jsonPath("$.data.role").value("VIEWER"));

    String token = login(username, password);

    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("displayName", "测试用户改名", "bio", "集成测试资料")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.displayName").value("测试用户改名"));

    mockMvc
        .perform(get("/api/v1/users/me/activities").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));

    mockMvc
        .perform(get("/api/v1/users/me/preferences").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notifyAlarm").isBoolean());

    mockMvc
        .perform(
            put("/api/v1/users/me/preferences")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "notifyAlarm",
                        false,
                        "notifyTask",
                        true,
                        "notifySystem",
                        true,
                        "defaultSiteId",
                        "site_001",
                        "sidebarCollapsed",
                        true)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notifyAlarm").value(false))
        .andExpect(jsonPath("$.data.defaultSiteId").value("site_001"));

    mockMvc
        .perform(
            put("/api/v1/auth/password")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "oldPassword",
                        password,
                        "newPassword",
                        newPassword,
                        "confirmPassword",
                        newPassword)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    login(username, newPassword);
  }

  @Test
  void apiSectionCrudResourcesAreCovered() throws Exception {
    String adminToken = login("admin", "Admin@123");
    String dispatcherToken = login("dispatcher", "Disp@123");

    mockMvc
        .perform(
            post("/api/v1/sites")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "id", "site_test_api",
                        "name", "接口测试站点",
                        "address", "测试地址",
                        "description", "测试站点描述",
                        "lat", 30.1,
                        "lng", 120.1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("site_test_api"));

    mockMvc
        .perform(
            get("/api/v1/sites/site_test_api").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("接口测试站点"));

    mockMvc
        .perform(
            patch("/api/v1/sites/site_test_api")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("description", "已更新站点描述")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.description").value("已更新站点描述"));

    mockMvc
        .perform(
            post("/api/v1/sites/site_test_api/areas")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json("id", "area_test_api", "name", "接口测试区域", "points", java.util.List.of())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.siteId").value("site_test_api"));

    mockMvc
        .perform(
            get("/api/v1/sites/site_test_api/areas")
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].siteId").value("site_test_api"))
        .andExpect(jsonPath("$.data.total").value(1));

    mockMvc
        .perform(
            patch("/api/v1/sites/site_test_api/areas/area_test_api")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "接口测试区域-更新")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("接口测试区域-更新"));

    mockMvc
        .perform(
            post("/api/v1/routes")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("id", "route_test_api", "name", "接口测试路线", "siteId", "site_test_api")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("route_test_api"));

    mockMvc
        .perform(
            get("/api/v1/routes/route_test_api").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("接口测试路线"));

    mockMvc
        .perform(
            post("/api/v1/routes/route_test_api/checkpoints")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("id", "cp_test_api", "name", "接口测试检查点", "pan", 0, "tilt", -10)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("cp_test_api"));

    mockMvc
        .perform(
            get("/api/v1/routes/route_test_api/checkpoints")
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value("cp_test_api"));

    mockMvc
        .perform(
            patch("/api/v1/routes/route_test_api/checkpoints/cp_test_api")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "接口测试检查点-更新")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("接口测试检查点-更新"));

    mockMvc
        .perform(
            post("/api/v1/robots")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "id",
                        "robot_test_api",
                        "name",
                        "接口测试机器人",
                        "model",
                        "TestBot",
                        "serialNo",
                        "TB-001",
                        "siteId",
                        "site_test_api",
                        "status",
                        "ONLINE")))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get("/api/v1/robots/robot_001").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("robot_001"));

    mockMvc
        .perform(
            get("/api/v1/robots/robot_001/telemetry").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("robot_001"));

    mockMvc
        .perform(
            patch("/api/v1/robots/robot_001")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("firmware", "ROS2-test")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.firmware").value("ROS2-test"));

    mockMvc
        .perform(
            post("/api/v1/detection-templates")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "id", "tpl_test_api",
                        "name", "接口测试模板",
                        "scope", "ROUTE",
                        "items",
                            java.util.List.of(
                                map("type", "PERSON", "enabled", true, "prompt", "测试提示词")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("tpl_test_api"));

    mockMvc
        .perform(
            get("/api/v1/detection-templates/tpl_test_api")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("接口测试模板"));

    mockMvc
        .perform(
            patch("/api/v1/detection-templates/tpl_test_api")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "接口测试模板-更新")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("接口测试模板-更新"));

    mockMvc
        .perform(
            delete("/api/v1/detection-templates/tpl_test_api")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(delete("/api/v1/robots/robot_001").header("Authorization", bearer(adminToken)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            delete("/api/v1/routes/route_test_api/checkpoints/cp_test_api")
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/api/v1/routes/route_test_api")
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/api/v1/sites/site_test_api/areas/area_test_api")
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/api/v1/sites/site_test_api").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk());
  }

  @Test
  void taskAlarmWorkOrderRecordNotificationApisWork() throws Exception {
    String dispatcherToken = login("dispatcher", "Disp@123");
    String adminToken = login("admin", "Admin@123");
    String routeId = "route_task_flow_test";
    ensureTestRoute(routeId);
    dataStore.upsert(
        DataCategory.NOTIFICATION,
        map(
            "id",
            "ntf_flow_admin",
            "userId",
            "user_admin",
            "type",
            "SYSTEM",
            "title",
            "测试通知",
            "content",
            "用于验证通知读取状态",
            "read",
            false,
            "link",
            "/dashboard"));

    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "id", "task_flow_api",
                        "name", "完整流程巡检",
                        "routeId", routeId,
                        "robotId", "robot_001")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value("task_flow_api"));

    // Client create cannot set progress; seed near-complete so two ticks finish and write a record.
    Map<String, Object> seededTask = dataStore.find(DataCategory.TASK, "task_flow_api");
    seededTask.put("progress", 96);
    dataStore.upsert(DataCategory.TASK, seededTask);

    seedFlowAlarm("alarm_flow_001", "HIGH");
    seedFlowAlarm("alarm_flow_002", "HIGH");
    seedFlowAlarm("alarm_flow_003", "HIGH");

    mockMvc
        .perform(
            get("/api/v1/tasks/task_flow_api").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.progress").value(96));

    mockMvc
        .perform(
            patch("/api/v1/tasks/task_flow_api")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("name", "完整流程巡检-更新")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("完整流程巡检-更新"));

    mockMvc
        .perform(
            post("/api/v1/tasks/task_flow_api/dispatch")
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("DISPATCHED"));

    taskService.tick();
    taskService.tick();

    mockMvc
        .perform(
            get("/api/v1/tasks/task_flow_api/events")
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()", greaterThanOrEqualTo(1)));

    mockMvc
        .perform(get("/api/v1/records").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[?(@.taskId == 'task_flow_api')]").exists())
        .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(1)));

    mockMvc
        .perform(post("/api/v1/records/export").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", containsString("inspection-records.csv")))
        .andExpect(content().string(containsString("taskName,routeName,robotName")));

    mockMvc
        .perform(get("/api/v1/alarms").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()", greaterThanOrEqualTo(1)));

    mockMvc
        .perform(
            post("/api/v1/alarms/alarm_flow_001/ack")
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.acknowledged").value(true));

    mockMvc
        .perform(post("/api/v1/alarms/ack-all").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk());

    String workOrderId =
        postAndReadId("/api/v1/work-orders/from-alarm/alarm_flow_003", adminToken, "{}");

    mockMvc
        .perform(
            get("/api/v1/work-orders/" + workOrderId)
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.alarmId").value("alarm_flow_003"))
        .andExpect(jsonPath("$.data.status").value("PENDING"))
        .andExpect(jsonPath("$.data.locationDescription").value("电容器组巡检"));

    mockMvc
        .perform(
            post("/api/v1/work-orders/" + workOrderId + "/claim")
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.assigneeName").value("张调度"))
        .andExpect(jsonPath("$.data.status").value("PROCESSING"));

    mockMvc
        .perform(
            post("/api/v1/work-orders/" + workOrderId + "/claim")
                .header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            patch("/api/v1/work-orders/" + workOrderId)
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("priority", "LOW")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.priority").value("LOW"));

    String photoUploadResponse =
        mockMvc
            .perform(
                multipart("/api/v1/work-orders/" + workOrderId + "/photos")
                    .file(new MockMultipartFile(
                        "photo", "onsite.jpg", MediaType.IMAGE_JPEG_VALUE, testImage()))
                    .header("Authorization", bearer(dispatcherToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.url", containsString("/model-files/work-order-photos/" + workOrderId)))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    String photoUrl = objectMapper.readTree(photoUploadResponse).path("data").path("url").asText();

    mockMvc
        .perform(
            delete("/api/v1/work-orders/" + workOrderId + "/photos")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("url", photoUrl)))
        .andExpect(status().isOk());
    assertThat(Files.notExists(
        ModelFileWebConfig.MODEL_FILE_ROOT.resolve(photoUrl.substring("/model-files/".length()))))
        .isTrue();
    mockMvc
        .perform(get("/api/v1/work-orders/" + workOrderId).header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.pendingPhotos").isEmpty());

    photoUploadResponse =
        mockMvc
            .perform(
                multipart("/api/v1/work-orders/" + workOrderId + "/photos")
                    .file(new MockMultipartFile(
                        "photo", "onsite.jpg", MediaType.IMAGE_JPEG_VALUE, testImage()))
                    .header("Authorization", bearer(dispatcherToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    photoUrl = objectMapper.readTree(photoUploadResponse).path("data").path("url").asText();

    mockMvc
        .perform(
            patch("/api/v1/work-orders/" + workOrderId + "/status")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "status",
                        "REVIEW",
                        "review",
                        Map.of(
                            "conclusion", "PARTIALLY_RESOLVED",
                            "onsiteFinding", "现场检查主变底部和密封件，未见新增渗油痕迹。",
                            "handlingMeasures", "已完成油迹清洁和油位复测，读数保持正常。"))))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            patch("/api/v1/work-orders/" + workOrderId + "/status")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json(
                        "status",
                        "REVIEW",
                        "review",
                        Map.of(
                            "conclusion", "RESOLVED",
                            "onsiteFinding", "现场检查主变底部和密封件，未见新增渗油痕迹。",
                            "handlingMeasures", "已完成油迹清洁和油位复测，读数保持正常。"),
                        "resolutionForm",
                        Map.of(
                            "faultType", "设备渗漏油",
                            "handlingMethod", "现场清理",
                            "testResult", "复测正常，油位恢复",
                            "conclusion", "RESOLVED",
                            "submittedBy", "张调度",
                            "photos", List.of(photoUrl)))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.review.conclusion").value("RESOLVED"))
        .andExpect(jsonPath("$.data.review.submittedByName").value("张调度"))
        .andExpect(jsonPath("$.data.resolutionForm.photos[0]").value(photoUrl));

    mockMvc
        .perform(
            patch("/api/v1/work-orders/" + workOrderId + "/status")
                .header("Authorization", bearer(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("status", "CLOSED")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.closedAt", not(blankOrNullString())));

    mockMvc
        .perform(
            post("/api/v1/work-orders/from-alarm/alarm_flow_002")
                .header("Authorization", bearer(dispatcherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/work-orders/" + workOrderId + "/claim")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()", greaterThanOrEqualTo(1)));

    mockMvc
        .perform(
            patch("/api/v1/notifications/ntf_flow_admin/read")
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.read").value(true));

    mockMvc
        .perform(
            delete("/api/v1/work-orders/" + workOrderId)
                .header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            delete("/api/v1/tasks/task_flow_api").header("Authorization", bearer(dispatcherToken)))
        .andExpect(status().isOk());
    dataStore.delete(DataCategory.ROUTE, routeId);
  }

  private void ensureTestRoute(String routeId) {
    if (dataStore.find(DataCategory.ROUTE, routeId) != null) return;
    dataStore.upsert(
        DataCategory.ROUTE,
        map(
            "id",
            routeId,
            "siteId",
            "site_001",
            "name",
            "任务流程测试路线",
            "path",
            List.of(map("lat", 30.2741, "lng", 120.1551), map("lat", 30.2744, "lng", 120.1554)),
            "checkpoints",
            List.of()));
  }

  private void seedFlowAlarm(String id, String severity) {
    dataStore.upsert(
        DataCategory.ALARM,
        map(
            "id",
            id,
            "taskId",
            "task_flow_api",
            "routeName",
            "电容器组巡检",
            "type",
            "FIRE",
            "severity",
            severity,
            "message",
            "流程测试告警 " + id,
            "acknowledged",
            false));
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> item = new LinkedHashMap<>();
    for (int index = 0; index + 1 < values.length; index += 2) {
      item.put(String.valueOf(values[index]), values[index + 1]);
    }
    return item;
  }

  private String login(String username, String password) throws Exception {
    String body =
        "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"remember\":true}";
    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("token").asText();
  }

  private String sendRegisterSmsCode(String phone) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/sms/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json("phone", phone, "purpose", "REGISTER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.debugCode").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("debugCode").asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private String postAndReadId(String path, String token, String body) throws Exception {
    String response =
        mockMvc
            .perform(
                post(path)
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

  private byte[] testImage() throws Exception {
    Path image = Path.of("src/test/resources/fixtures/inspection.jpg");
    if (Files.exists(image)) {
      return Files.readAllBytes(image);
    }
    return new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0, 0x00, 0x10, 'J', 'F', 'I', 'F', 0x00,
      0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, (byte) 0xff, (byte) 0xd9 };
  }
}
