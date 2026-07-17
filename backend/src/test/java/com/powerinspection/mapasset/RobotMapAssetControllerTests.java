package com.powerinspection.mapasset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.route.RouteDeploymentEntity;
import com.powerinspection.route.RouteDeploymentRepository;
import com.powerinspection.route.RouteRevisionEntity;
import com.powerinspection.route.RouteRevisionRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = {
  "app.robot.bridge-platform-token=test-bridge-token",
  "app.robot.bridge-robot-id-mappings.robot_001=robot-001",
  "app.robot.bridge-robot-id-mappings.robot_missing=bridge-missing",
  "app.robot.bridge-robot-id-mappings.robot_unbound=bridge-unbound"
})
@AutoConfigureMockMvc
class RobotMapAssetControllerTests {
  private static final String INTERNAL_URL = "/api/v1/internal/robot-map-assets";
  private static final String BRIDGE_AUTH = "Bearer test-bridge-token";

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired DataStoreService dataStore;
  @Autowired MapAssetService mapAssetService;
  @Autowired RouteRevisionRepository revisionRepository;
  @Autowired RouteDeploymentRepository deploymentRepository;

  @Test
  void bridgeCredentialMappingRobotAndSiteAreAllRequired() throws Exception {
    mockMvc.perform(upload("bad-key", "robot-001").header("Authorization", "Bearer wrong"))
      .andExpect(status().isUnauthorized());

    String userToken = login("dispatcher", "Disp@123");
    mockMvc.perform(upload("jwt-key", "robot-001").header("Authorization", bearer(userToken)))
      .andExpect(status().isUnauthorized());

    mockMvc.perform(upload("unknown-bridge", "not-mapped").header("Authorization", BRIDGE_AUTH))
      .andExpect(status().isNotFound());
    mockMvc.perform(upload("missing-robot", "bridge-missing").header("Authorization", BRIDGE_AUTH))
      .andExpect(status().isNotFound());

    dataStore.upsert(DataCategory.ROBOT, new LinkedHashMap<>(Map.of(
      "id", "robot_unbound", "name", "未绑定机器人", "status", "OFFLINE")));
    mockMvc.perform(upload("unbound", "bridge-unbound").header("Authorization", BRIDGE_AUTH))
      .andExpect(status().isForbidden());
  }

  @Test
  void validUploadCreatesPendingRobotMetadataAndSupportsIdempotency() throws Exception {
    String key = key();
    String response = mockMvc.perform(upload(key, "robot-001").header("Authorization", BRIDGE_AUTH))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
      .andExpect(jsonPath("$.data.source").value("ROBOT"))
      .andExpect(jsonPath("$.data.sourceRobotId").value("robot_001"))
      .andExpect(jsonPath("$.data.sourceBridgeRobotId").value("robot-001"))
      .andExpect(jsonPath("$.data.uploadIdempotencyKey").value(key))
      .andExpect(jsonPath("$.data.contentIdentitySha256").isString())
      .andExpect(jsonPath("$.data.occupiedThresh").value("0.65"))
      .andExpect(jsonPath("$.data.freeThresh").value("0.2"))
      .andExpect(jsonPath("$.data.mode").value("trinary"))
      .andExpect(jsonPath("$.data.capturedAt").value("2026-07-17T00:00:00Z"))
      .andExpect(jsonPath("$.data.reviewedBy").doesNotExist())
      .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    String mapId = objectMapper.readTree(response).path("data").path("id").asText();

    String token = login("dispatcher", "Disp@123");
    mockMvc.perform(get("/api/v1/map-assets/{id}/yaml", mapId).header("Authorization", bearer(token)))
      .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/map-assets/{id}", mapId).header("Authorization", BRIDGE_AUTH))
      .andExpect(status().isNotFound());

    mockMvc.perform(upload(key, "robot-001").header("Authorization", BRIDGE_AUTH))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(mapId));

    mockMvc.perform(upload(key, "robot-001", yaml(
        "mode: trinary\nfree_thresh: 0.2000\norigin: [0.0, 0.00, -0]\nimage: floor.pgm\n"
          + "occupied_thresh: 0.6500\nresolution: 0.0500\nnegate: 0\n"), pgmBytes((byte) 0))
        .header("Authorization", BRIDGE_AUTH))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(mapId));

    mockMvc.perform(upload(key, "robot-001", pgmBytes((byte) 7)).header("Authorization", BRIDGE_AUTH))
      .andExpect(status().isConflict());
  }

  @Test
  void robotUploadReusesYamlAndPgmValidation() throws Exception {
    MockMultipartFile duplicateYaml = yaml("image: floor.pgm\nimage: floor.pgm\nresolution: 0.05\norigin: [0, 0, 0]\n");
    mockMvc.perform(multipart(INTERNAL_URL).file(duplicateYaml).file(pgm()).param("capturedAt", "2026-07-17T00:00:00Z")
        .header("Authorization", BRIDGE_AUTH).header("X-Bridge-Robot-Id", "robot-001").header("Idempotency-Key", key()))
      .andExpect(status().isBadRequest());

    MockMultipartFile truncated = new MockMultipartFile("pgm", "floor.pgm", "image/x-portable-graymap",
      "P5\n2 2\n255\n\u0000".getBytes(StandardCharsets.ISO_8859_1));
    mockMvc.perform(multipart(INTERNAL_URL).file(yaml()).file(truncated).param("capturedAt", "2026-07-17T00:00:00Z")
        .header("Authorization", BRIDGE_AUTH).header("X-Bridge-Robot-Id", "robot-001").header("Idempotency-Key", key()))
      .andExpect(status().isBadRequest());
  }

  @Test
  void concurrentSameUploadCreatesOnlyOneAsset() throws Exception {
    String key = key();
    CountDownLatch start = new CountDownLatch(1);
    Callable<RobotMapUploadResult> call = () -> {
      start.await();
      return mapAssetService.createForRobot("site_001", "robot_001", "robot-001", key,
        null, Instant.parse("2026-07-17T00:00:00Z"), yaml(), pgm());
    };
    var executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(call);
      var second = executor.submit(call);
      start.countDown();
      RobotMapUploadResult left = first.get();
      RobotMapUploadResult right = second.get();
      assertEquals(left.asset().get("id"), right.asset().get("id"));
      assertEquals(1, List.of(left, right).stream().filter(RobotMapUploadResult::created).count());
      assertEquals(1, dataStore.list(DataCategory.MAP_ASSET).stream()
        .filter(asset -> key.equals(asset.get("uploadIdempotencyKey"))).count());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void reviewControlsRouteAvailabilityAndDoesNotMutateExistingRouteOrDeployment() throws Exception {
    String token = login("dispatcher", "Disp@123");
    String approvedMapId = uploadMap(key());
    String routeId = "route_robot_map_" + suffix();
    mockMvc.perform(post("/api/v1/routes").header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON).content(json("id", routeId, "siteId", "site_001", "name", "审核测试路线")))
      .andExpect(status().isOk());

    mockMvc.perform(patch("/api/v1/routes/{id}", routeId).header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON).content(json("mapId", approvedMapId)))
      .andExpect(status().isNotFound());

    mockMvc.perform(post("/api/v1/map-assets/{id}/review", approvedMapId).header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON).content(json("action", "APPROVE", "comment", "地图校验通过")))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    mockMvc.perform(patch("/api/v1/routes/{id}", routeId).header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON).content(json("mapId", approvedMapId)))
      .andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/map-assets/{id}/review", approvedMapId).header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON).content(json("action", "APPROVE")))
      .andExpect(status().isConflict());

    RouteRevisionEntity revision = revision(routeId, approvedMapId);
    revisionRepository.saveAndFlush(revision);
    RouteDeploymentEntity deployment = deployment(revision.getId());
    deploymentRepository.saveAndFlush(deployment);

    String rejectedMapId = uploadMap(key());
    mockMvc.perform(post("/api/v1/map-assets/{id}/review", rejectedMapId).header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON).content(json("action", "REJECT", "comment", "")))
      .andExpect(status().isBadRequest());
    mockMvc.perform(post("/api/v1/map-assets/{id}/review", rejectedMapId).header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON).content(json("action", "REJECT", "comment", "边界与现场不一致")))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
    mockMvc.perform(patch("/api/v1/routes/{id}", routeId).header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON).content(json("mapId", rejectedMapId)))
      .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/map-assets").queryParam("source", "ROBOT").queryParam("status", "REJECTED")
        .queryParam("siteId", "site_001").header("Authorization", bearer(token)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data[?(@.id == '%s')]", rejectedMapId).exists());

    mockMvc.perform(get("/api/v1/routes/{id}", routeId).header("Authorization", bearer(token)))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data.mapId").value(approvedMapId));
    assertEquals(approvedMapId, revisionRepository.findById(revision.getId()).orElseThrow().getMapAssetId());
    assertEquals("READY_FOR_ROBOT", deploymentRepository.findById(deployment.getId()).orElseThrow().getState());
    assertFalse(dataStore.list(DataCategory.MAP_ASSET).stream()
      .filter(asset -> "ROBOT".equals(asset.get("source")) && "REJECTED".equals(asset.get("status"))).toList().isEmpty());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(String key, String bridgeRobotId) {
    return upload(key, bridgeRobotId, pgmBytes((byte) 0));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(String key, String bridgeRobotId, byte[] pgmBytes) {
    return upload(key, bridgeRobotId, yaml(), pgmBytes);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(
      String key, String bridgeRobotId, MockMultipartFile yaml, byte[] pgmBytes) {
    return multipart(INTERNAL_URL).file(yaml).file(new MockMultipartFile("pgm", "floor.pgm", "image/x-portable-graymap", pgmBytes))
      .param("capturedAt", "2026-07-17T00:00:00Z").header("X-Bridge-Robot-Id", bridgeRobotId).header("Idempotency-Key", key);
  }

  private String uploadMap(String key) throws Exception {
    String response = mockMvc.perform(upload(key, "robot-001").header("Authorization", BRIDGE_AUTH))
      .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("id").asText();
  }

  private MockMultipartFile yaml() { return yaml("image: floor.pgm\nresolution: 0.05\norigin: [0, 0, 0]\nnegate: 0\noccupied_thresh: 0.65\nfree_thresh: 0.2\n"); }
  private MockMultipartFile yaml(String body) { return new MockMultipartFile("yaml", "floor.yaml", "application/yaml", body.getBytes(StandardCharsets.UTF_8)); }
  private MockMultipartFile pgm() { return new MockMultipartFile("pgm", "floor.pgm", "image/x-portable-graymap", pgmBytes((byte) 0)); }
  private byte[] pgmBytes(byte firstPixel) {
    byte[] header = "P5\n2 1\n255\n".getBytes(StandardCharsets.US_ASCII);
    byte[] bytes = new byte[header.length + 2];
    System.arraycopy(header, 0, bytes, 0, header.length);
    bytes[header.length] = firstPixel;
    bytes[header.length + 1] = (byte) 255;
    return bytes;
  }

  private RouteRevisionEntity revision(String routeId, String mapAssetId) {
    String id = "revision_" + suffix();
    RouteRevisionEntity entity = new RouteRevisionEntity();
    entity.setId(id); entity.setRouteId(routeId); entity.setRevisionNo(1); entity.setExecutorJson("{}");
    entity.setContentSha256("a".repeat(64)); entity.setMapAssetId(mapAssetId); entity.setMapImageSha256("b".repeat(64));
    entity.setValidationReportJson("{}"); entity.setCreatedAt(Instant.now().toString());
    return entity;
  }

  private RouteDeploymentEntity deployment(String revisionId) {
    RouteDeploymentEntity entity = new RouteDeploymentEntity();
    entity.setId("deployment_" + suffix()); entity.setRouteRevisionId(revisionId); entity.setRobotId("robot_001");
    entity.setRequestId("request_" + suffix()); entity.setState("READY_FOR_ROBOT"); entity.setAttemptNo(1);
    entity.setCreatedAt(Instant.now().toString()); entity.setUpdatedAt(Instant.now().toString());
    return entity;
  }

  private String login(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(json("username", username, "password", password, "remember", true)))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("token").asText();
  }

  private String json(Object... values) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) body.put(String.valueOf(values[i]), values[i + 1]);
    return objectMapper.writeValueAsString(body);
  }
  private String bearer(String token) { return "Bearer " + token; }
  private String key() { return "map-upload-" + UUID.randomUUID(); }
  private String suffix() { return UUID.randomUUID().toString().replace("-", ""); }
}
