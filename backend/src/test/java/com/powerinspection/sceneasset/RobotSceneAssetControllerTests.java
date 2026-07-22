package com.powerinspection.sceneasset;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
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
  "app.robot.bridge-platform-token=test-scene-bridge-token",
  "app.robot.bridge-robot-id-mappings.robot_001=robot-001"
})
@AutoConfigureMockMvc
class RobotSceneAssetControllerTests {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void robotSceneUploadIsIdempotentDownloadableAndReviewable() throws Exception {
    String key = "scene-test-" + UUID.randomUUID();
    byte[] ply = """
      ply
      format ascii 1.0
      element vertex 2
      property float x
      property float y
      property float z
      end_header
      0 0 0
      1 1 1
      """.getBytes(StandardCharsets.US_ASCII);
    String metadata = """
      {"reconstructProfile":"zed-medium","sceneFrame":"zed_3d_map","referenceFrame":"map",
       "sceneToReferenceTransform":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]}
      """;

    String created = mockMvc.perform(upload(key, ply, metadata))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.data.status").value("PENDING_REVIEW"))
      .andExpect(jsonPath("$.data.pointCount").value(2))
      .andExpect(jsonPath("$.data.reportedPointCount").value(3))
      .andExpect(jsonPath("$.data.pointCountMismatch").value(true))
      .andExpect(jsonPath("$.data.filesReady").value(true))
      .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    String sceneId = objectMapper.readTree(created).path("data").path("id").asText();

    mockMvc.perform(upload(key, ply, metadata))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.id").value(sceneId));

    String token = login("dispatcher", "Disp@123");
    mockMvc.perform(get("/api/v1/scene-assets/{id}/model", sceneId).header("Authorization", bearer(token)))
      .andExpect(status().isOk()).andExpect(content().bytes(ply));
    mockMvc.perform(get("/api/v1/scene-assets/{id}/metadata", sceneId).header("Authorization", bearer(token)))
      .andExpect(status().isOk()).andExpect(content().string(containsString("zed-medium")));
    mockMvc.perform(post("/api/v1/scene-assets/{id}/review", sceneId)
        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"action\":\"APPROVE\",\"comment\":\"点云范围核验通过\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    mockMvc.perform(post("/api/v1/scene-assets/{id}/review", sceneId)
        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
        .content("{\"action\":\"REJECT\",\"comment\":\"重复审核\"}"))
      .andExpect(status().isConflict());
  }

  private org.springframework.test.web.servlet.RequestBuilder upload(
      String key, byte[] ply, String metadata) throws Exception {
    return multipart("/api/v1/internal/robot-scene-assets")
      .file(new MockMultipartFile("model", "pointcloud.ply", "application/octet-stream", ply))
      .file(new MockMultipartFile("metadata", "metadata.json", "application/json", metadata.getBytes(StandardCharsets.UTF_8)))
      .param("modelSha256", sha256(ply)).param("assetKind", "POINT_CLOUD").param("format", "PLY")
      .param("sourceSessionId", "reconstruct-test").param("reconstructedAt", "2026-07-22T00:00:00Z")
      .param("coordinateSystem", "RIGHT_HANDED_Z_UP").param("unit", "METER").param("pointCount", "3")
      .header("Authorization", "Bearer test-scene-bridge-token")
      .header("X-Bridge-Robot-Id", "robot-001").header("Idempotency-Key", key);
  }

  private String login(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
      .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("token").asText();
  }

  private String sha256(byte[] value) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private String bearer(String token) { return "Bearer " + token; }
}
