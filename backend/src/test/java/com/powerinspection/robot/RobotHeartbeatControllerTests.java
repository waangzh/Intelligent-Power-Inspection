package com.powerinspection.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RobotHeartbeatControllerTests {
  private static final String ROBOT_ID = "robot-heartbeat-test";

  @Autowired private RobotHeartbeatService heartbeatService;
  @Autowired private RobotHeartbeatStatusRepository repository;
  @Autowired private DataStoreService dataStore;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    repository.deleteById(ROBOT_ID);
    dataStore.upsert(DataCategory.ROBOT, robotIdentity());
  }

  @AfterEach
  void cleanUp() {
    repository.deleteById(ROBOT_ID);
    dataStore.delete(DataCategory.ROBOT, ROBOT_ID);
  }

  @Test
  void legalHeartbeatUpdatesOnlineStatusAndReadApis() throws Exception {
    Instant now = now();
    heartbeatService.applyBridgeSnapshot(snapshot(ROBOT_ID, now, "running", "boot-current"), now);
    String token = login("dispatcher", "Disp@123");

    mockMvc.perform(get("/api/v1/robots/status")
        .header("Authorization", bearer(token))
        .param("online", "true"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.items[0].robotId").value(ROBOT_ID))
      .andExpect(jsonPath("$.data.items[0].online").value(true))
      .andExpect(jsonPath("$.data.items[0].connectionStatus").value("CONNECTED"))
      .andExpect(jsonPath("$.data.items[0].source.name").value("robot-bridge"));

    mockMvc.perform(get("/api/v1/robots/{id}/status", ROBOT_ID).header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.serialNo").value("SN-HEARTBEAT-001"))
      .andExpect(jsonPath("$.data.protocolVersion").value("1.0"))
      .andExpect(jsonPath("$.data.reportedSupportsLocalConfirmStart").value(true))
      .andExpect(jsonPath("$.data.localConfirmProtocolCompatible").value(true))
      .andExpect(jsonPath("$.data.localConfirmStartReady").value(true))
      .andExpect(jsonPath("$.data.diagnosticSummary").value(org.hamcrest.Matchers.containsString("nav2=not_running")));
  }

  @Test
  void unknownRobotIsRejectedBeforeStatusIsPersisted() {
    Instant now = now();
    assertThrows(ApiException.class, () -> heartbeatService.applyBridgeSnapshot(snapshot("robot-unknown", now, "idle", "boot-unknown"), now));
    assertEquals(false, repository.existsById("robot-unknown"));
  }

  @Test
  void timeoutTransitionsToOfflineWithoutDeletingHeartbeatHistory() {
    Instant now = now();
    Instant heartbeatAt = now.minusSeconds(13);
    heartbeatService.applyBridgeSnapshot(snapshot(ROBOT_ID, heartbeatAt, "idle", "boot-timeout"), now);
    heartbeatService.refreshTimeouts(now);

    RobotHeartbeatStatusEntity stored = repository.findById(ROBOT_ID).orElseThrow();
    assertEquals("OFFLINE", stored.getConnectionStatus());
    assertEquals("HEARTBEAT_TIMEOUT", stored.getOfflineReason());
    assertEquals(heartbeatAt, stored.getLastHeartbeatAt());
  }

  @Test
  void olderHeartbeatCannotOverwriteNewerSnapshot() {
    Instant now = now();
    Instant latest = now.minusSeconds(2);
    heartbeatService.applyBridgeSnapshot(snapshot(ROBOT_ID, latest, "running", "boot-new"), now);
    heartbeatService.applyBridgeSnapshot(snapshot(ROBOT_ID, latest.minusSeconds(1), "failed", "boot-old"), now);

    RobotHeartbeatStatusEntity stored = repository.findById(ROBOT_ID).orElseThrow();
    assertEquals(latest, stored.getLastHeartbeatAt());
    assertEquals("running", stored.getRobotState());
    assertEquals("boot-new", stored.getBootId());
  }

  private BridgeRobotSnapshot snapshot(String robotId, Instant at, String state, String bootId) {
    return new BridgeRobotSnapshot(robotId, at, "1.0", bootId, state, null, "test-build", 7,
      Map.of("systemMode", "ready", "nav2", "not_running", "lastError", "must-not-be-exposed"), null, null,
      true, true, "1", true, null, at);
  }

  private Map<String, Object> robotIdentity() {
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("id", ROBOT_ID);
    identity.put("name", "心跳测试机器人");
    identity.put("serialNo", "SN-HEARTBEAT-001");
    identity.put("model", "test");
    identity.put("status", "OFFLINE");
    return identity;
  }

  private String login(String username, String password) throws Exception {
    Map<String, Object> body = Map.of("username", username, "password", password, "remember", true);
    String response = mockMvc.perform(post("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(body)))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    return objectMapper.readTree(response).path("data").path("token").asText();
  }

  private String bearer(String token) { return "Bearer " + token; }
  private Instant now() { return Instant.now().truncatedTo(ChronoUnit.MICROS); }
}
