package com.powerinspection.detection;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class DetectionTemplateControllerTests {
  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Test
  void templatesExposeRealDetectionItems() throws Exception {
    String token = login("admin", "Admin@123");

    mockMvc.perform(get("/api/v1/detection-templates").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].items").isArray())
      .andExpect(jsonPath("$.data.items[0].items[0].enabled").isBoolean())
      .andExpect(jsonPath("$.data.items[0].items[0].displayLabel").isString())
      .andExpect(jsonPath("$.data.items[0].items[0].prompt").isString());
  }

  @Test
  void acceptsCustomDetectionItemsOutsidePresetTypes() throws Exception {
    String token = login("admin", "Admin@123");

    mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name":"人员专项模板",
            "scope":"CHECKPOINT",
            "description":"",
            "items":[{
              "itemId":"person_custom",
              "type":"CUSTOM_PERSON",
              "name":"人员检测",
              "displayLabel":"人员",
              "enabled":true,
              "prompt":"定位图像中所有清晰可见的人员"
            }]
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].itemId").value("person_custom"))
      .andExpect(jsonPath("$.data.items[0].type").value("CUSTOM_PERSON"))
      .andExpect(jsonPath("$.data.items[0].name").value("人员检测"))
      .andExpect(jsonPath("$.data.items[0].displayLabel").value("人员"))
      .andExpect(jsonPath("$.data.items[0].prompt").value("定位图像中所有清晰可见的人员"));
  }

  @Test
  void rejectsEnabledDetectionWithoutPrompt() throws Exception {
    String token = login("admin", "Admin@123");

    mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name":"缺少提示词",
            "scope":"CHECKPOINT",
            "description":"",
            "items":[{"type":"SWITCH","enabled":true,"prompt":""}]
          }
          """))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("已启用检测项 开关/刀闸状态 必须填写提示词"));
  }

  @Test
  void createsAndEditsTemplateItems() throws Exception {
    String token = login("admin", "Admin@123");
    String created = mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name":"主变检查点模板",
            "scope":"CHECKPOINT",
            "description":"初始模板",
            "items":[
              {"type":"SWITCH","enabled":true,"displayLabel":"红色刀闸开关","prompt":"定位处于开启状态的红色刀闸开关"},
              {"type":"METER","enabled":false,"prompt":""}
            ]
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].displayLabel").value("红色刀闸开关"))
      .andExpect(jsonPath("$.data.items[0].prompt").value("定位处于开启状态的红色刀闸开关"))
      .andExpect(jsonPath("$.data.items[1].enabled").value(false))
      .andReturn()
      .getResponse()
      .getContentAsString(StandardCharsets.UTF_8);
    String id = objectMapper.readTree(created).path("data").path("id").asText();

    mockMvc.perform(patch("/api/v1/detection-templates/" + id)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name":"主变检查点模板（已调整）",
            "items":[
              {"type":"SWITCH","enabled":false,"prompt":""},
              {"type":"METER","enabled":true,"prompt":"压力表表盘区域"}
            ]
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.name").value("主变检查点模板（已调整）"))
      .andExpect(jsonPath("$.data.items[0].enabled").value(false))
      .andExpect(jsonPath("$.data.items[1].prompt").value("压力表表盘区域"));
  }

  private String login(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"remember\":true}"))
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
