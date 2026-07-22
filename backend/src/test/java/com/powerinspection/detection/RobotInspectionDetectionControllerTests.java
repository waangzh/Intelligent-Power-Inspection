package com.powerinspection.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.model.LocateAnythingRequest;
import com.powerinspection.model.LocateAnythingResult;
import com.powerinspection.route.RouteRevisionEntity;
import com.powerinspection.route.RouteRevisionRepository;
import com.powerinspection.task.TaskExecutionEntity;
import com.powerinspection.task.TaskExecutionRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
  "app.model.locate-anything.input-file-base-url=http://127.0.0.1:18080/model-files/",
  "app.robot.bridge-platform-token=test-bridge-token",
  "app.robot.bridge-robot-id-mappings.robot_demo_004=robot-device-004"
})
@AutoConfigureMockMvc
class RobotInspectionDetectionControllerTests {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired DataStoreService dataStore;
  @Autowired RobotInspectionImageRepository imageRepository;
  @Autowired DetectionRunRepository runRepository;
  @Autowired RouteRevisionRepository revisionRepository;
  @Autowired TaskExecutionRepository executionRepository;
  @MockBean LocateAnythingGateway locateAnythingGateway;

  @BeforeEach
  void setUpRobotTasks() {
    imageRepository.deleteAll();
    dataStore.upsert(DataCategory.ROBOT, mutable(Map.of(
      "id", "robot_demo_004", "name", "巡检机器人 D4", "siteId", "site_002", "status", "ONLINE")));
    dataStore.upsert(DataCategory.ROBOT, mutable(Map.of(
      "id", "robot_demo_005", "name", "巡检机器人 E5", "siteId", "site_003", "status", "ONLINE")));
    dataStore.upsert(DataCategory.ROUTE, mutable(Map.of(
      "id", "route_demo_002", "siteId", "site_002", "name", "城西开关室日常巡检",
      "checkpoints", List.of(Map.of("id", "cp_demo_101", "routeId", "route_demo_002", "name", "10kV 开关柜 A 段", "seq", 1)))));
    dataStore.upsert(DataCategory.ROUTE, mutable(Map.of(
      "id", "route_demo_003", "siteId", "site_003", "name", "城南 500kV 户外设备巡检",
      "checkpoints", List.of(Map.of("id", "cp_demo_201", "routeId", "route_demo_003", "name", "1# 主变 A 相", "seq", 1)))));
    dataStore.upsert(DataCategory.TASK, mutable(Map.of(
      "id", "task_demo_active", "name", "开关室异常复核巡检", "routeId", "route_demo_002",
      "robotId", "robot_demo_004", "status", "CREATED", "progress", 10, "currentCheckpointSeq", 0,
      "createdAt", "2026-07-18T00:00:00Z")));
    dataStore.upsert(DataCategory.TASK, mutable(Map.of(
      "id", "task_demo_paused", "name", "500kV 主变红外测温巡检", "routeId", "route_demo_003",
      "robotId", "robot_demo_005", "status", "CREATED", "progress", 20, "currentCheckpointSeq", 1,
      "createdAt", "2026-07-18T00:00:00Z")));
  }

  @Test
  void onlyAdministratorCanImportRobotInspectionImages() throws Exception {
    mockMvc.perform(importImage(login("dispatcher", "Disp@123"), "task_demo_active", "robot_demo_004", "cp_demo_101"))
      .andExpect(status().isForbidden());

    mockMvc.perform(importImage(login("admin", "Admin@123"), "task_demo_active", "robot_demo_004", "cp_demo_101"))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.data.id").isString())
      .andExpect(jsonPath("$.data.taskId").value("task_demo_active"))
      .andExpect(jsonPath("$.data.checkpointId").value("cp_demo_101"))
      .andExpect(jsonPath("$.data.imageUrl", containsString("/model-files/robot-inspection/")))
      .andExpect(jsonPath("$.data.width").value(6))
      .andExpect(jsonPath("$.data.height").value(4));
  }

  @Test
  void importedImagesCanBeFilteredByTaskAndCheckpoint() throws Exception {
    String token = login("admin", "Admin@123");
    String imageId = importedImageId(token, "task_demo_paused", "robot_demo_005", "cp_demo_201");

    mockMvc.perform(get("/api/v1/robot-inspection-images")
        .queryParam("taskId", "task_demo_paused")
        .queryParam("checkpointId", "cp_demo_201")
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.items[0].id").value(imageId))
      .andExpect(jsonPath("$.data.items[0].taskName").value("500kV 主变红外测温巡检"))
      .andExpect(jsonPath("$.data.items[0].checkpointName").value("1# 主变 A 相"));
  }

  @Test
  void imageIdDetectionUsesControlledModelUrlAndPersistsResult() throws Exception {
    given(locateAnythingGateway.detectCheckpoint(any())).willReturn(new LocateAnythingResult(List.of(
      new LocateAnythingFinding("CUSTOM_PERSON", "定位图像中所有清晰可见的人员", 0.0,
        List.of(1, 2, 3, 4), "人员", null,
        Map.of("rawAnswer", "<box><1><2><3><4></box>"))
    ), List.of(), null));
    String token = login("admin", "Admin@123");
    String imageId = importedImageId(token, "task_demo_active", "robot_demo_004", "cp_demo_101");

    String created = mockMvc.perform(post("/api/v1/detections/robot-image")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
           {"imageId":"%s","detections":[{"itemId":"person_custom","type":"CUSTOM_PERSON","name":"人员检测","enabled":true,"displayLabel":"人员","prompt":"定位图像中所有清晰可见的人员","alarmEnabled":true,"alarmOnFinding":true,"alarmSeverity":"HIGH","alarmMessage":"检查点发现{label}"}]}
          """.formatted(imageId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("RUNNING"))
      .andExpect(jsonPath("$.data.imageId").value(imageId))
      .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    String runId = objectMapper.readTree(created).path("data").path("requestId").asText();
    JsonNode result = awaitRun(runId, token);

    assertThat(result.path("status").asText()).isEqualTo("SUCCEEDED");
      assertThat(result.path("detections").get(0).path("itemId").asText()).isEqualTo("person_custom");
      assertThat(result.path("detections").get(0).path("name").asText()).isEqualTo("人员检测");
      assertThat(result.path("detections").get(0).path("displayLabel").asText()).isEqualTo("人员");
      assertThat(result.path("detections").get(0).path("prompt").asText()).isEqualTo("定位图像中所有清晰可见的人员");
    assertThat(result.path("findings").get(0).path("type").asText()).isEqualTo("CUSTOM_PERSON");
    assertThat(result.path("alarmCount").asInt()).isEqualTo(1);
    Map<String, Object> alarm = dataStore.list(DataCategory.ALARM).stream()
      .filter(item -> runId.equals(item.get("detectionRunId")))
      .findFirst().orElseThrow();
    assertThat(alarm).containsEntry("sourceType", "DETECTION_RUN")
      .containsEntry("imageId", imageId)
      .containsEntry("taskId", "task_demo_active")
      .containsEntry("checkpointId", "cp_demo_101")
      .containsEntry("itemId", "person_custom")
      .containsEntry("severity", "HIGH");
    ArgumentCaptor<LocateAnythingRequest> request = ArgumentCaptor.forClass(LocateAnythingRequest.class);
    verify(locateAnythingGateway).detectCheckpoint(request.capture());
    assertThat(request.getValue().imageUrl())
      .startsWith("http://127.0.0.1:18080/model-files/robot-inspection/");

    DetectionRunEntity storedRun = runRepository.findById(runId).orElseThrow();
    storedRun.setResultImageUrl("/model-files/detection-runs/test_annotated.jpg");
    runRepository.saveAndFlush(storedRun);
    mockMvc.perform(get("/api/v1/detections/runs/" + runId).header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.resultImageUrl")
        .value("http://localhost/model-files/detection-runs/test_annotated.jpg"));

    mockMvc.perform(get("/api/v1/detections/runs")
        .queryParam("taskId", "task_demo_active")
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].requestId").value(runId));

    RobotInspectionImageEntity storedImage = imageRepository.findById(imageId).orElseThrow();
    storedImage.setStorageKey(null);
    storedImage.setStatus("ORIGINAL_PURGED");
    storedImage.setOriginalPurgedAt("2026-07-18T01:00:00Z");
    imageRepository.saveAndFlush(storedImage);
    mockMvc.perform(get("/api/v1/detections/runs/" + runId).header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.originalAvailable").value(false))
      .andExpect(jsonPath("$.data.inputImageUrl").doesNotExist());
  }

  @Test
  void robotImageDetectionDefaultsMissingRiskRulesToAlarmDisabled() throws Exception {
    given(locateAnythingGateway.detectCheckpoint(any()))
      .willReturn(new LocateAnythingResult(List.of(), List.of(), null));
    String token = login("admin", "Admin@123");
    String imageId = importedImageId(token, "task_demo_active", "robot_demo_004", "cp_demo_101");

    mockMvc.perform(post("/api/v1/detections/robot-image")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"imageId":"%s","detections":[{
            "itemId":"person_custom","type":"CUSTOM_PERSON","name":"人员检测",
            "enabled":true,"displayLabel":"人员","prompt":"定位人员"
          }]}
          """.formatted(imageId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.detections[0].alarmEnabled").value(false))
      .andExpect(jsonPath("$.data.detections[0].alarmOnFinding").value(false))
      .andExpect(jsonPath("$.data.detections[0].alarmSeverity").value("MEDIUM"))
      .andExpect(jsonPath("$.data.detections[0].alarmMessage").value(""));
  }

  @Test
  void robotImageDetectionRejectsInvalidAlarmSeverity() throws Exception {
    String token = login("admin", "Admin@123");
    String imageId = importedImageId(token, "task_demo_active", "robot_demo_004", "cp_demo_101");

    mockMvc.perform(post("/api/v1/detections/robot-image")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"imageId":"%s","detections":[{
            "type":"FIRE","enabled":true,"prompt":"定位明火",
            "alarmEnabled":true,"alarmOnFinding":true,"alarmSeverity":"critical"
          }]}
          """.formatted(imageId)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("告警级别必须是 LOW、MEDIUM、HIGH 或 CRITICAL"));
  }

  @Test
  void bridgeUploadRequiresDeviceIdentityAndIsIdempotent() throws Exception {
    String revisionId = "rev_image_" + UUID.randomUUID().toString().replace("-", "");
    RouteRevisionEntity revision = new RouteRevisionEntity();
    revision.setId(revisionId);
    revision.setRouteId("route_demo_002");
    revision.setRevisionNo(System.nanoTime());
    revision.setExecutorJson("""
      {"schema_version":"3.0","active_route_id":"route_demo_002","targets":[{"id":"cp_demo_101","name":"10kV 开关柜 A 段","pose":{"x":1,"y":2,"yaw":0}}],"routes":[{"id":"route_demo_002","name":"城西开关室日常巡检","target_ids":["cp_demo_101"]}],"schedules":[]}
      """);
    revision.setContentSha256("a".repeat(64));
    revision.setMapAssetId("map-test");
    revision.setMapImageSha256("b".repeat(64));
    revision.setValidationReportJson("{\"valid\":true}");
    revision.setCreatedAt("2026-07-18T00:00:00Z");
    revisionRepository.saveAndFlush(revision);

    TaskExecutionEntity execution = new TaskExecutionEntity();
    execution.setTaskId("task_demo_active");
    execution.setExecutionId("exec-image-test");
    execution.setRouteRevisionId(revisionId);
    execution.setRobotId("robot_demo_004");
    execution.setRouteContentSha256("a".repeat(64));
    execution.setMapImageSha256("b".repeat(64));
    execution.setStatus("RUNNING");
    execution.setCreatedAt("2026-07-18T00:00:00Z");
    execution.setUpdatedAt("2026-07-18T00:00:00Z");
    executionRepository.saveAndFlush(execution);

    String key = UUID.randomUUID().toString();
    byte[] image = testImage();
    mockMvc.perform(bridgeUpload(key, image).header("Authorization", "Bearer wrong"))
      .andExpect(status().isUnauthorized());
    String response = mockMvc.perform(bridgeUpload(key, image).header("Authorization", "Bearer test-bridge-token"))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.data.source").value("ROBOT_BRIDGE"))
      .andExpect(jsonPath("$.data.executionId").value("exec-image-test"))
      .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    String imageId = objectMapper.readTree(response).path("data").path("id").asText();

    mockMvc.perform(bridgeUpload(key, image).header("Authorization", "Bearer test-bridge-token"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(imageId));
    mockMvc.perform(bridgeUpload(key, differentImage()).header("Authorization", "Bearer test-bridge-token"))
      .andExpect(status().isConflict());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder importImage(
      String token, String taskId, String robotId, String checkpointId) throws Exception {
    return multipart("/api/v1/robot-inspection-images/import")
      .file(new MockMultipartFile("image", "inspection.jpg", MediaType.IMAGE_JPEG_VALUE, testImage()))
      .param("taskId", taskId)
      .param("robotId", robotId)
      .param("checkpointId", checkpointId)
      .param("capturedAt", "2026-07-18T00:00:00Z")
      .header("Authorization", bearer(token));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder bridgeUpload(String key, byte[] bytes) {
    return multipart("/api/v1/internal/robot-inspection-images")
      .file(new MockMultipartFile("image", "inspection.jpg", MediaType.IMAGE_JPEG_VALUE, bytes))
      .param("executionId", "exec-image-test")
      .param("taskId", "task_demo_active")
      .param("checkpointId", "cp_demo_101")
      .param("capturedAt", "2026-07-18T00:00:00Z")
      .param("imageSha256", sha256(bytes))
      .header("X-Bridge-Robot-Id", "robot-device-004")
      .header("Idempotency-Key", key);
  }

  private String importedImageId(String token, String taskId, String robotId, String checkpointId) throws Exception {
    String response = mockMvc.perform(importImage(token, taskId, robotId, checkpointId))
      .andExpect(status().isCreated())
      .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("id").asText();
  }

  private JsonNode awaitRun(String runId, String token) throws Exception {
    for (int i = 0; i < 30; i += 1) {
      String response = mockMvc.perform(get("/api/v1/detections/runs/" + runId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
      JsonNode data = objectMapper.readTree(response).path("data");
      if (!"RUNNING".equals(data.path("status").asText())) return data;
      Thread.sleep(100);
    }
    throw new AssertionError("robot image detection did not finish");
  }

  private byte[] testImage() throws Exception {
    BufferedImage image = new BufferedImage(6, 4, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", output);
    return output.toByteArray();
  }

  private byte[] differentImage() throws Exception {
    BufferedImage image = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
    image.setRGB(1, 1, 0xff336699);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", output);
    return output.toByteArray();
  }

  private String sha256(byte[] value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
    catch (Exception ex) { throw new IllegalStateException(ex); }
  }

  private String login(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"remember\":true}"))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("token").asText();
  }

  private String bearer(String token) { return "Bearer " + token; }

  private Map<String, Object> mutable(Map<String, Object> values) {
    return new java.util.LinkedHashMap<>(values);
  }
}
