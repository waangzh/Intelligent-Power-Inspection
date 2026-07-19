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
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.model.LocateAnythingRequest;
import com.powerinspection.model.LocateAnythingResult;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
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
@SpringBootTest(properties = "app.model.locate-anything.input-file-base-url=http://127.0.0.1:18080/model-files/")
@AutoConfigureMockMvc
class ManualDetectionControllerTests {
  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  DetectionRunRepository detectionRunRepository;

  @MockBean
  LocateAnythingGateway locateAnythingGateway;

  @Test
  void manualDetectionUploadsImageAndReturnsAsyncJobThenModelFindings() throws Exception {
    given(locateAnythingGateway.detectCheckpoint(any())).willReturn(new LocateAnythingResult(List.of(new LocateAnythingFinding(
      "SWITCH",
      "红色刀闸开关",
      0.88,
      List.of(12, 20, 120, 160),
      "abnormal",
      null,
      Map.of("rawAnswer", "<box><100><100><500><500></box>")
    )), List.of("模型输出已截断"), null));

    MockMultipartFile image = new MockMultipartFile(
      "image",
      "switch.jpg",
      MediaType.IMAGE_JPEG_VALUE,
      testImage()
    );
    MockMultipartFile detections = new MockMultipartFile(
      "detections",
      "",
      MediaType.APPLICATION_JSON_VALUE,
      "[{\"type\":\"SWITCH\",\"enabled\":true,\"prompt\":\"红色刀闸开关\",\"threshold\":0.75}]".getBytes(StandardCharsets.UTF_8)
    );

    String token = login("admin", "Admin@123");
    String created = mockMvc.perform(multipart("/api/v1/detections/manual")
        .file(image)
        .file(detections)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.requestId").exists())
      .andExpect(jsonPath("$.data.status").value("RUNNING"))
      .andExpect(jsonPath("$.data.inputImageUrl", containsString("/model-files/locate-anything/uploads/")))
      .andReturn()
      .getResponse()
      .getContentAsString(StandardCharsets.UTF_8);
    JsonNode createdData = objectMapper.readTree(created).path("data");
    String requestId = createdData.path("requestId").asText();
    assertThat(createdData.path("inputImageUrl").asText()).doesNotContain("127.0.0.1:18080");

    JsonNode finished = awaitJob(requestId, token);
    ArgumentCaptor<LocateAnythingRequest> requestCaptor = ArgumentCaptor.forClass(LocateAnythingRequest.class);
    verify(locateAnythingGateway).detectCheckpoint(requestCaptor.capture());
    LocateAnythingRequest modelRequest = requestCaptor.getValue();
    assertThat(modelRequest.imageUrl())
      .startsWith("http://127.0.0.1:18080/model-files/locate-anything/uploads/");
    assertThat(modelRequest.imageWidth()).isEqualTo(4);
    assertThat(modelRequest.imageHeight()).isEqualTo(3);
    mockMvc.perform(get("/api/v1/detections/manual/" + requestId).header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
      .andExpect(jsonPath("$.data.inputImageUrl", containsString("/model-files/locate-anything/uploads/")))
      .andExpect(jsonPath("$.data.findings[0].type").value("SWITCH"))
      .andExpect(jsonPath("$.data.findings[0].bbox[0]").value(12))
      .andExpect(jsonPath("$.data.warnings[0]").value("模型输出已截断"));
    assertThat(finished.path("status").asText()).isEqualTo("SUCCEEDED");
    DetectionRunEntity persisted = detectionRunRepository.findById(requestId).orElseThrow();
    assertThat(persisted.getSourceType()).isEqualTo("LOCAL_UPLOAD");
    assertThat(persisted.getStatus()).isEqualTo("SUCCEEDED");
  }

  private byte[] testImage() throws Exception {
    BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", output);
    return output.toByteArray();
  }

  private JsonNode awaitJob(String requestId, String token) throws Exception {
    for (int i = 0; i < 20; i += 1) {
      String response = mockMvc.perform(get("/api/v1/detections/manual/" + requestId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
      JsonNode data = objectMapper.readTree(response).path("data");
      if (!"RUNNING".equals(data.path("status").asText())) {
        return data;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("manual detection job did not finish");
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
