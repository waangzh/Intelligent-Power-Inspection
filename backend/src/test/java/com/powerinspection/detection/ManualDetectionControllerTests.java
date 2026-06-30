package com.powerinspection.detection;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.LocateAnythingGateway;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ManualDetectionControllerTests {
  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @MockBean
  LocateAnythingGateway locateAnythingGateway;

  @Test
  void manualDetectionUploadsImageAndReturnsModelFindings() throws Exception {
    given(locateAnythingGateway.detectCheckpoint(any())).willReturn(List.of(new LocateAnythingFinding(
      "SWITCH",
      "红色刀闸开关",
      0.88,
      List.of(12, 20, 120, 160),
      "abnormal",
      "http://127.0.0.1:9001/files/annotated/manual_0.jpg",
      Map.of("rawAnswer", "<box><100><100><500><500></box>")
    )));

    MockMultipartFile image = new MockMultipartFile(
      "image",
      "switch.jpg",
      MediaType.IMAGE_JPEG_VALUE,
      new byte[] {1, 2, 3, 4}
    );
    MockMultipartFile detections = new MockMultipartFile(
      "detections",
      "",
      MediaType.APPLICATION_JSON_VALUE,
      "[{\"type\":\"SWITCH\",\"enabled\":true,\"prompt\":\"红色刀闸开关\",\"threshold\":0.75}]".getBytes(StandardCharsets.UTF_8)
    );

    mockMvc.perform(multipart("/api/v1/detections/manual")
        .file(image)
        .file(detections)
        .header("Authorization", bearer(login("admin", "Admin@123"))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.requestId").exists())
      .andExpect(jsonPath("$.data.inputImageUrl", containsString("/model-files/locate-anything/uploads/")))
      .andExpect(jsonPath("$.data.resultImageUrl").value("http://127.0.0.1:9001/files/annotated/manual_0.jpg"))
      .andExpect(jsonPath("$.data.findings[0].type").value("SWITCH"))
      .andExpect(jsonPath("$.data.findings[0].bbox[0]").value(12));
  }

  private String login(String username, String password) throws Exception {
    String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"remember\":true}";
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString(StandardCharsets.UTF_8);
    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("token").asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}