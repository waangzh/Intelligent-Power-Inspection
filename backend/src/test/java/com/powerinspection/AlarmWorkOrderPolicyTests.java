package com.powerinspection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.alarm.AlarmService;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import com.powerinspection.workorder.WorkOrderService;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
class AlarmWorkOrderPolicyTests {
  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  AlarmService alarmService;

  @Autowired
  WorkOrderService workOrderService;

  @Autowired
  DataStoreService dataStore;

  @Autowired
  UserRepository userRepository;

  @Test
  void criticalAlarmAutoConversionIsIdempotentAndDoesNotAcknowledgeAlarm() throws Exception {
    saveDefaultPolicy(login("admin", "Admin@123"));

    Map<String, Object> alarm = alarmService.create(alarm("alarm_policy_auto", "CRITICAL"));

    assertThat(alarm.get("acknowledged")).isEqualTo(false);
    assertThat(alarm.get("workOrderConversionStatus")).isEqualTo("SUCCEEDED");
    Map<String, Object> order = workOrderService.findByAlarmId("alarm_policy_auto");
    assertThat(order)
      .containsEntry("priority", "URGENT")
      .containsEntry("source", "AUTO")
      .containsEntry("createdById", "system");

    UserEntity admin = userRepository.findByUsername("admin").orElseThrow();
    Map<String, Object> duplicate = workOrderService.createFromAlarm(
      "alarm_policy_auto", "MANUAL", admin, admin.getDisplayName(), null
    );
    assertThat(duplicate.get("id")).isEqualTo(order.get("id"));
    assertThat(dataStore.list(DataCategory.WORK_ORDER).stream()
      .filter(item -> "alarm_policy_auto".equals(item.get("alarmId")))
      .count()).isEqualTo(1);
    assertThat(dataStore.get(DataCategory.ALARM, "alarm_policy_auto").get("acknowledged")).isEqualTo(false);
  }

  @Test
  void policyDemoCriticalAlarmUsesAutomaticConversion() {
    Map<String, Object> alarm = dataStore.get(DataCategory.ALARM, "alarm_demo_policy_critical");

    assertThat(alarm)
      .containsEntry("workOrderModeApplied", "AUTO")
      .containsEntry("workOrderConversionStatus", "SUCCEEDED")
      .containsEntry("acknowledged", false);
    assertThat(workOrderService.findByAlarmId("alarm_demo_policy_critical"))
      .containsEntry("id", "wo_alarm_alarm_demo_policy_critical")
      .containsEntry("priority", "URGENT")
      .containsEntry("source", "AUTO")
      .containsEntry("createdById", "system");
  }

  @Test
  void lowAlarmWaitsForManualConversionAndKeepsLowPriority() throws Exception {
    saveDefaultPolicy(login("admin", "Admin@123"));

    Map<String, Object> alarm = alarmService.create(alarm("alarm_policy_manual", "LOW"));

    assertThat(alarm.get("workOrderConversionStatus")).isEqualTo("WAITING_MANUAL");
    assertThat(workOrderService.findByAlarmId("alarm_policy_manual")).isNull();

    UserEntity admin = userRepository.findByUsername("admin").orElseThrow();
    Map<String, Object> order = workOrderService.createFromAlarm(
      "alarm_policy_manual", "MANUAL", admin, admin.getDisplayName(), null
    );
    assertThat(order)
      .containsEntry("priority", "LOW")
      .containsEntry("source", "MANUAL");
    Map<String, Object> converted = dataStore.get(DataCategory.ALARM, "alarm_policy_manual");
    assertThat(converted.get("workOrderConversionStatus")).isEqualTo("SUCCEEDED");
    assertThat(converted.get("acknowledged")).isEqualTo(false);
  }

  @Test
  void onlyAdminCanUpdateAlarmWorkOrderPolicy() throws Exception {
    String viewerToken = login("viewer", "View@123");
    String adminToken = login("admin", "Admin@123");

    mockMvc.perform(put("/api/v1/alarms/work-order-policy")
        .header("Authorization", bearer(viewerToken))
        .contentType(MediaType.APPLICATION_JSON)
        .content(defaultPolicyBody()))
      .andExpect(status().isForbidden());

    saveDefaultPolicy(adminToken);
  }

  private void saveDefaultPolicy(String token) throws Exception {
    mockMvc.perform(put("/api/v1/alarms/work-order-policy")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(defaultPolicyBody()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.rules.CRITICAL").value("AUTO"));
  }

  private String defaultPolicyBody() throws Exception {
    return objectMapper.writeValueAsString(Map.of(
      "rules", Map.of(
        "LOW", "MANUAL",
        "MEDIUM", "MANUAL",
        "HIGH", "MANUAL",
        "CRITICAL", "AUTO"
      )
    ));
  }

  private Map<String, Object> alarm(String id, String severity) {
    Map<String, Object> alarm = new LinkedHashMap<>();
    alarm.put("id", id);
    alarm.put("type", "FIRE");
    alarm.put("severity", severity);
    alarm.put("message", "Policy test alarm " + id);
    alarm.put("siteId", "site_001");
    alarm.put("siteName", "Site 1");
    alarm.put("acknowledged", false);
    return alarm;
  }

  private String login(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of(
          "username", username,
          "password", password,
          "remember", true
        ))))
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
