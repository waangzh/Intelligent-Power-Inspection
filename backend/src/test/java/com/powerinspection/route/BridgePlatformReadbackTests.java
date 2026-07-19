package com.powerinspection.route;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(properties = "app.robot.bridge-platform-token=bridge-readback-test-token")
@AutoConfigureMockMvc
class BridgePlatformReadbackTests {
  private static final String BRIDGE_TOKEN = "bridge-readback-test-token";

  @Autowired private MockMvc mockMvc;
  @Autowired private RouteRevisionRepository revisionRepository;
  @Autowired private RouteDeploymentRepository deploymentRepository;
  @Autowired private DataStoreService dataStore;

  @Test
  void bridgePlatformTokenCanReadOnlyDeploymentRevisionAndMap() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String revisionId = "revision-bridge-" + suffix;
    String deploymentId = "deployment-bridge-" + suffix;
    String now = Instant.now().toString();

    RouteRevisionEntity revision = new RouteRevisionEntity();
    revision.setId(revisionId);
    revision.setRouteId("route-bridge-" + suffix);
    revision.setRevisionNo(1);
    revision.setExecutorJson("{}");
    revision.setContentSha256("a".repeat(64));
    revision.setMapAssetId("map-bridge-" + suffix);
    revision.setMapImageSha256("b".repeat(64));
    revision.setValidationReportJson("{}");
    revision.setCreatedAt(now);
    revisionRepository.save(revision);

    RouteDeploymentEntity deployment = new RouteDeploymentEntity();
    deployment.setId(deploymentId);
    deployment.setRouteRevisionId(revisionId);
    deployment.setRobotId("robot_001");
    deployment.setRequestId("request-bridge-" + suffix);
    deployment.setState(RouteDeploymentState.UNKNOWN.name());
    deployment.setAttemptNo(1);
    deployment.setCreatedAt(now);
    deployment.setUpdatedAt(now);
    deploymentRepository.save(deployment);
    dataStore.upsert(DataCategory.MAP_ASSET, new LinkedHashMap<>(Map.of(
      "id", revision.getMapAssetId(), "status", "AVAILABLE", "yamlName", "floor.yaml", "pgmName", "floor.pgm",
      "yamlSha256", "c".repeat(64), "pgmSha256", "b".repeat(64)
    )));

    mockMvc.perform(get("/api/v1/route-deployments/{id}", deploymentId).header("Authorization", bearer()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(deploymentId))
      .andExpect(jsonPath("$.data.robotId").value("robot-001"));
    mockMvc.perform(get("/api/v1/route-revisions/{id}", revisionId).header("Authorization", bearer()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(revisionId));
    mockMvc.perform(get("/api/v1/map-assets/{id}", revision.getMapAssetId()).header("Authorization", bearer()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(revision.getMapAssetId()));
    mockMvc.perform(get("/api/v1/map-assets/{id}/yaml", "missing-map").header("Authorization", bearer()))
      .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/map-assets/{id}/pgm", "missing-map").header("Authorization", bearer()))
      .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/v1/route-deployments/{id}", deploymentId))
      .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/map-assets/{id}", revision.getMapAssetId()))
      .andExpect(status().isUnauthorized());
    // Bridge 凭据不是有效 JWT，命中需登录的接口时属于“未认证”而非“已登录但无权限”。
    mockMvc.perform(post("/api/v1/route-deployments/{id}/reconcile", deploymentId).header("Authorization", bearer()))
      .andExpect(status().isUnauthorized());
  }

  private String bearer() {
    return "Bearer " + BRIDGE_TOKEN;
  }
}
