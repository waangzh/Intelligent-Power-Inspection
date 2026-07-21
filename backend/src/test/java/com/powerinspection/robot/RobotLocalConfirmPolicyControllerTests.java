package com.powerinspection.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RobotLocalConfirmPolicyControllerTests {
  private static final String ROBOT_ID = "robot-local-confirm-policy-test";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DataStoreService dataStore;
  @Autowired private RobotHeartbeatService heartbeatService;
  @Autowired private RobotHeartbeatStatusRepository heartbeatRepository;
  @Autowired private RobotLocalConfirmPolicyAuditRepository auditRepository;

  @BeforeEach
  void setUp() {
    auditRepository.deleteAll(auditRepository.findByRobotIdOrderByChangedAtDesc(ROBOT_ID));
    heartbeatRepository.deleteById(ROBOT_ID);
    dataStore.upsert(DataCategory.ROBOT, robot());
  }

  @AfterEach
  void cleanUp() {
    auditRepository.deleteAll(auditRepository.findByRobotIdOrderByChangedAtDesc(ROBOT_ID));
    heartbeatRepository.deleteById(ROBOT_ID);
    dataStore.delete(DataCategory.ROBOT, ROBOT_ID);
  }

  @Test
  void adminUsesDedicatedEndpointAndDisableRemainsAvailable() throws Exception {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    heartbeatService.applyBridgeSnapshot(snapshot(now, true, "1", false), now);
    String admin = login("admin", "Admin@123");
    String dispatcher = login("dispatcher", "Disp@123");

    mockMvc.perform(patch("/api/v1/robots/{id}", ROBOT_ID)
        .header("Authorization", bearer(admin))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"supportsLocalConfirmStart\":true}"))
      .andExpect(status().isBadRequest());

    mockMvc.perform(patch("/api/v1/robots/{id}/local-confirm-start-policy", ROBOT_ID)
        .header("Authorization", bearer(dispatcher))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"enabled\":true}"))
      .andExpect(status().isForbidden());

    mockMvc.perform(patch("/api/v1/robots/{id}/local-confirm-start-policy", ROBOT_ID)
        .header("Authorization", bearer(admin))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"enabled\":true}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.reportedSupportsLocalConfirmStart").value(true))
      .andExpect(jsonPath("$.data.localConfirmStartEnabled").value(true))
      .andExpect(jsonPath("$.data.supportsLocalConfirmStart").value(true))
      .andExpect(jsonPath("$.data.localConfirmStartReady").value(false));

    heartbeatService.applyBridgeSnapshot(snapshot(now.plusSeconds(1), false, null, false), now.plusSeconds(1));
    mockMvc.perform(patch("/api/v1/robots/{id}/local-confirm-start-policy", ROBOT_ID)
        .header("Authorization", bearer(admin))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"enabled\":false}"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.localConfirmStartEnabled").value(false));

    assertEquals(2, auditRepository.findByRobotIdOrderByChangedAtDesc(ROBOT_ID).size());
  }

  private BridgeRobotSnapshot snapshot(Instant at, boolean reported, String version, boolean ready) {
    return new BridgeRobotSnapshot(ROBOT_ID, at, "1.0", "boot-policy", "idle", "test-build", 0,
      Map.of("systemMode", "ready"), null, true, reported, version, ready,
      ready ? null : "UI_CONFIRM_ENDPOINT_UNAVAILABLE", at);
  }

  private Map<String, Object> robot() {
    Map<String, Object> robot = new LinkedHashMap<>();
    robot.put("id", ROBOT_ID);
    robot.put("name", "本地确认审批测试机器人");
    robot.put("model", "test");
    robot.put("serialNo", "SN-LOCAL-CONFIRM");
    robot.put("status", "OFFLINE");
    robot.put("localConfirmStartEnabled", false);
    return robot;
  }

  private String login(String username, String password) throws Exception {
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(Map.of(
          "username", username, "password", password, "remember", true))))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("token").asText();
  }

  private String bearer(String token) { return "Bearer " + token; }
}
