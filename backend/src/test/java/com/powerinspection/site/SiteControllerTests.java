package com.powerinspection.site;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class SiteControllerTests {
  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void acceptsValidSiteCenter() throws Exception {
    mockMvc.perform(post("/api/v1/sites")
        .header("Authorization", bearer(login()))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name":"坐标校验测试站点",
            "address":"浙江省杭州市",
            "description":"",
            "center":{"lat":30.2741,"lng":120.1551}
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.center.lat").value(30.2741))
      .andExpect(jsonPath("$.data.center.lng").value(120.1551));
  }

  @Test
  void rejectsMissingSiteCenter() throws Exception {
    mockMvc.perform(post("/api/v1/sites")
        .header("Authorization", bearer(login()))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"缺少坐标的站点\"}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("站点中心坐标不能为空"));
  }

  @Test
  void rejectsSuspectedSwappedCoordinates() throws Exception {
    mockMvc.perform(post("/api/v1/sites")
        .header("Authorization", bearer(login()))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name":"疑似反转坐标的站点",
            "center":{"lat":120.1551,"lng":30.2741}
          }
          """))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value(
        "中心坐标疑似经纬度填写反了：纬度应在 -90 到 90 之间，经度应在 -180 到 180 之间；请核对后重新填写，系统不会自动交换"
      ));
  }

  @Test
  void rejectsOutOfRangeLongitudeOnUpdate() throws Exception {
    mockMvc.perform(patch("/api/v1/sites/site_001")
        .header("Authorization", bearer(login()))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"center\":{\"lat\":30.2741,\"lng\":181}}"))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("中心经度必须在 -180 到 180 之间"));
  }

  private String login() throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"admin\",\"password\":\"Admin@123\",\"remember\":true}"))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("token").asText();
  }

  private String bearer(String token) {
    return "Bearer " + token;
  }
}
