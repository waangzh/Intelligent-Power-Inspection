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
      .andExpect(jsonPath("$.data.items[0].prompt").value("定位图像中所有清晰可见的人员"))
      .andExpect(jsonPath("$.data.items[0].alarmMode").value("OFF"));
  }

  @Test
  void persistsDetectionRiskRules() throws Exception {
    String token = login("admin", "Admin@123");

    String created = mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name":"明火风险模板",
            "scope":"CHECKPOINT",
            "items":[{
              "itemId":"fire-risk",
              "type":"FIRE",
              "name":"明火风险",
              "displayLabel":"明火",
              "enabled":true,
              "prompt":"定位明火",
              "alarmMode":"ON_FINDING",
              "alarmSeverity":"CRITICAL",
              "alarmMessage":"  检查点发现{label}  "
            }]
          }
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].alarmMode").value("ON_FINDING"))
      .andExpect(jsonPath("$.data.items[0].alarmSeverity").value("CRITICAL"))
      .andExpect(jsonPath("$.data.items[0].alarmMessage").value("  检查点发现{label}  "))
      .andReturn()
      .getResponse()
      .getContentAsString(StandardCharsets.UTF_8);
    String id = objectMapper.readTree(created).path("data").path("id").asText();

    mockMvc.perform(get("/api/v1/detection-templates/" + id)
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].alarmMode").value("ON_FINDING"))
      .andExpect(jsonPath("$.data.items[0].alarmSeverity").value("CRITICAL"))
      .andExpect(jsonPath("$.data.items[0].alarmMessage").value("  检查点发现{label}  "));
  }

  @Test
  void rejectsInvalidAlarmSeverity() throws Exception {
    String token = login("admin", "Admin@123");

    mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name":"非法风险级别","scope":"CHECKPOINT","items":[{
            "type":"FIRE","enabled":true,"prompt":"定位明火",
            "alarmMode":"ON_FINDING","alarmSeverity":"URGENT"
          }]}
          """))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("告警级别必须是 LOW、MEDIUM、HIGH 或 CRITICAL"));
  }

  @Test
  void rejectsAlarmSeverityWithDifferentCasing() throws Exception {
    String token = login("admin", "Admin@123");

    mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name":"小写风险级别","scope":"CHECKPOINT","items":[{
            "type":"FIRE","enabled":true,"prompt":"定位明火",
            "alarmMode":"ON_FINDING","alarmSeverity":"critical"
          }]}
          """))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("告警级别必须是 LOW、MEDIUM、HIGH 或 CRITICAL"));
  }

  @Test
  void legacyTemplateExplicitlyDefaultsToAlarmDisabled() throws Exception {
    String token = login("admin", "Admin@123");

    mockMvc.perform(get("/api/v1/detection-templates/tpl_route_001")
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].alarmMode").value("OFF"))
      .andExpect(jsonPath("$.data.items[0].alarmSeverity").value("MEDIUM"))
      .andExpect(jsonPath("$.data.items[0].alarmMessage").value(""));
  }

  @Test
  void persistsAlarmModeAndDefaultsLegacyItemsToOff() throws Exception {
    String token = login("admin", "Admin@123");

    mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name":"人员风险模板","scope":"CHECKPOINT","items":[{
            "itemId":"person-risk","type":"PERSON","name":"人员检测","enabled":true,
            "displayLabel":"人员","prompt":"定位人员","alarmMode":"ON_FINDING"
          }]}
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].alarmMode").value("ON_FINDING"));

    mockMvc.perform(get("/api/v1/detection-templates/tpl_route_001")
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].alarmMode").value("OFF"));
  }

  @Test
  void rejectsInvalidAlarmMode() throws Exception {
    String token = login("admin", "Admin@123");

    mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name":"非法告警规则","scope":"CHECKPOINT","items":[{
            "itemId":"person-risk","type":"PERSON","enabled":true,"prompt":"定位人员",
            "alarmMode":"ALWAYS"
          }]}
          """))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("告警规则必须是 OFF 或 ON_FINDING"));
  }

  @Test
  void allowsMultipleCustomItemsWhenItemIdsAreDistinct() throws Exception {
    String token = login("admin", "Admin@123");

    mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {"name":"多个自定义检测项","scope":"CHECKPOINT","items":[
            {"itemId":"custom-a","type":"CUSTOM","name":"自定义 A","enabled":true,"prompt":"定位目标 A"},
            {"itemId":"custom-b","type":"CUSTOM","name":"自定义 B","enabled":true,"prompt":"定位目标 B"}
          ]}
          """))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.items[0].itemId").value("custom-a"))
      .andExpect(jsonPath("$.data.items[1].itemId").value("custom-b"));
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
  void rejectsDuplicateItemIdsEvenWhenTypesAreShared() throws Exception {
    String token = login("admin", "Admin@123");

    mockMvc.perform(post("/api/v1/detection-templates")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
          {
            "name":"重复检测项标识",
            "scope":"CHECKPOINT",
            "items":[
              {"itemId":"custom-person","type":"CUSTOM","enabled":false,"prompt":""},
              {"itemId":" custom-person ","type":"CUSTOM","enabled":false,"prompt":""}
            ]
          }
          """))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.message").value("检测项标识不能重复：custom-person"));
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
