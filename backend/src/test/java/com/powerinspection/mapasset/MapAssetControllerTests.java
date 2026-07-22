package com.powerinspection.mapasset;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class MapAssetControllerTests {
  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Test
  void mapAssetCanBeUploadedReferencedRestoredAndCleaned() throws Exception {
    String token = login("dispatcher", "Disp@123");
    String siteId = "site_map_asset_test";
    String routeId = "route_map_asset_test";

    mockMvc.perform(post("/api/v1/sites")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", siteId, "name", "地图资产测试站点",
          "center", Map.of("lat", 30.1, "lng", 120.1))))
      .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/routes")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("id", routeId, "siteId", siteId, "name", "地图资产测试路线")))
      .andExpect(status().isOk());

    String yamlText = """
      image: floor.pgm
      resolution: 5e-2
      origin: [-2.89, -6.37, 0.0]
      negate: false
      occupied_thresh: 0.65
      free_thresh: 0.196
      mode: trinary
      """;
    byte[] pgmBytes = pgmBytes();
    MockMultipartFile yaml = new MockMultipartFile("yaml", "floor.yaml", "application/yaml", yamlText.getBytes(StandardCharsets.UTF_8));
    MockMultipartFile pgm = new MockMultipartFile("pgm", "floor.pgm", "image/x-portable-graymap", pgmBytes);

    String uploadResponse = mockMvc.perform(multipart("/api/v1/map-assets")
        .file(yaml)
        .file(pgm)
        .param("siteId", siteId)
        .header("Authorization", bearer(token)))
      .andExpect(status().isCreated())
      .andExpect(header().string("Location", containsString("/api/v1/map-assets/")))
      .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
      .andExpect(jsonPath("$.data.width").value(2))
      .andExpect(jsonPath("$.data.height").value(1))
      .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    String mapId = objectMapper.readTree(uploadResponse).path("data").path("id").asText();

    mockMvc.perform(get("/api/v1/map-assets/{id}/yaml", mapId).header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith("application/yaml"))
      .andExpect(content().string(containsString("image: floor.pgm")));

    mockMvc.perform(get("/api/v1/map-assets/{id}/pgm", mapId).header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(content().bytes(pgmBytes));

    mockMvc.perform(patch("/api/v1/routes/{id}", routeId)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("mapId", mapId)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.mapId").value(mapId));

    mockMvc.perform(get("/api/v1/routes/{id}", routeId).header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.mapId").value(mapId));

    mockMvc.perform(delete("/api/v1/map-assets/{id}", mapId).header("Authorization", bearer(token)))
      .andExpect(status().isConflict());

    mockMvc.perform(delete("/api/v1/routes/{id}", routeId).header("Authorization", bearer(token)))
      .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/map-assets/{id}", mapId).header("Authorization", bearer(token)))
      .andExpect(status().isNotFound());

    mockMvc.perform(delete("/api/v1/sites/{id}", siteId).header("Authorization", bearer(token)))
      .andExpect(status().isOk());
  }

  @Test
  void uploadRejectsYamlPgmNameMismatch() throws Exception {
    String token = login("dispatcher", "Disp@123");
    MockMultipartFile yaml = new MockMultipartFile(
      "yaml", "map.yaml", "application/yaml",
      "image: other.pgm\nresolution: 0.05\norigin: [0, 0, 0]\n".getBytes(StandardCharsets.UTF_8)
    );
    MockMultipartFile pgm = new MockMultipartFile("pgm", "floor.pgm", "image/x-portable-graymap", pgmBytes());

    mockMvc.perform(multipart("/api/v1/map-assets")
        .file(yaml)
        .file(pgm)
        .param("siteId", "site_001")
        .header("Authorization", bearer(token)))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("YAML 的 image 与上传的 PGM 文件名不匹配"));
  }

  private byte[] pgmBytes() {
    byte[] header = "P5\n2 1\n255\n".getBytes(StandardCharsets.US_ASCII);
    byte[] bytes = new byte[header.length + 2];
    System.arraycopy(header, 0, bytes, 0, header.length);
    bytes[header.length] = 0;
    bytes[header.length + 1] = (byte) 255;
    return bytes;
  }

  private String login(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json("username", username, "password", password, "remember", true)))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("token").asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }

  private String json(Object... values) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) body.put(String.valueOf(values[i]), values[i + 1]);
    return objectMapper.writeValueAsString(body);
  }
}
