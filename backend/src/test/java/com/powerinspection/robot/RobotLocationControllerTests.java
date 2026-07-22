package com.powerinspection.robot;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.time.Instant;
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
class RobotLocationControllerTests {
  private static final String ROBOT_ID = "robot-location-test";

  @Autowired private RobotLocationService locationService;
  @Autowired private RobotHeartbeatService heartbeatService;
  @Autowired private RobotTelemetryRepository telemetryRepository;
  @Autowired private RobotLocationHistoryRepository historyRepository;
  @Autowired private DataStoreService dataStore;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    telemetryRepository.deleteById(ROBOT_ID);
    historyRepository.deleteAll();
    dataStore.upsert(DataCategory.ROBOT, robotIdentity());
  }

  @AfterEach
  void cleanUp() {
    telemetryRepository.deleteById(ROBOT_ID);
    historyRepository.deleteAll();
    dataStore.delete(DataCategory.ROBOT, ROBOT_ID);
  }

  @Test
  void locationAndTrackApisExposeGnssFix() throws Exception {
    Instant now = Instant.parse("2026-07-15T06:00:00Z");
    BridgeGnssFix fix = new BridgeGnssFix(
        true, false, "gps_link", 31.2304, 121.4737, 12.5, 4, "RTK_FIXED", 18, 0.8, 0.2, "BS-01", 0.5, now);
    BridgePatrolSnapshot patrol = new BridgePatrolSnapshot(
        "route-1", "checkpoint-2", "2号配电柜", 1, 6, "target", 2, 5, false, null, "");
    BridgeRobotSnapshot snapshot = new BridgeRobotSnapshot(
        ROBOT_ID, now, "1.0", "boot-gps", "running", "execution-1", "test-build", 1,
        Map.of(), patrol, fix);
    heartbeatService.applyBridgeSnapshot(snapshot, now);
    locationService.applySnapshot(snapshot, now);

    String token = login("dispatcher", "Disp@123");

    mockMvc.perform(get("/api/v1/robots/{id}/location", ROBOT_ID).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.robotId").value(ROBOT_ID))
        .andExpect(jsonPath("$.data.locationAvailable").value(true))
        .andExpect(jsonPath("$.data.state").value("running"))
        .andExpect(jsonPath("$.data.executionId").value("execution-1"))
        .andExpect(jsonPath("$.data.gnssFix.latitude").value(31.2304))
        .andExpect(jsonPath("$.data.gnssFix.fixType").value("RTK_FIXED"));

    mockMvc.perform(get("/api/v1/robots/locations").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].robotId").value(ROBOT_ID));

    mockMvc.perform(get("/api/v1/robots/{id}/track", ROBOT_ID)
            .header("Authorization", bearer(token))
            .param("start", "2026-07-15T05:00:00Z")
            .param("end", "2026-07-15T07:00:00Z")
            .param("executionId", "execution-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.robotId").value(ROBOT_ID))
        .andExpect(jsonPath("$.data.executionId").value("execution-1"))
        .andExpect(jsonPath("$.data.points[0].latitude").value(31.2304))
        .andExpect(jsonPath("$.data.points[0].robotState").value("running"))
        .andExpect(jsonPath("$.data.points[0].navigationPhase").value("target"))
        .andExpect(jsonPath("$.data.points[0].targetId").value("checkpoint-2"))
        .andExpect(jsonPath("$.data.points[0].cycleIndex").value(2));
  }

  @Test
  void serverRecomputesStaleAndKeepsLastValidPosition() {
    Instant observedAt = Instant.parse("2026-07-15T06:00:00Z");
    BridgeGnssFix fix = new BridgeGnssFix(
        true, false, "gps_link", 31.2304, 121.4737, 12.5, 4, "RTK_FIXED", 18, 0.8,
        null, null, 0.1, observedAt);
    BridgeRobotSnapshot first = new BridgeRobotSnapshot(
        ROBOT_ID, observedAt, "1.0", "boot-gps", "running", "execution-stale",
        "test-build", 1, Map.of(), null, fix);
    locationService.applySnapshot(first, observedAt);

    BridgeRobotSnapshot delayed = new BridgeRobotSnapshot(
        ROBOT_ID, observedAt.plusSeconds(10), "1.0", "boot-gps", "running",
        "execution-stale", "test-build", 2, Map.of(), null, fix);
    locationService.applySnapshot(delayed, observedAt.plusSeconds(10));

    RobotTelemetryEntity telemetry = telemetryRepository.findById(ROBOT_ID).orElseThrow();
    assertFalse(telemetry.getGpsValid());
    assertTrue(telemetry.getGpsStale());
    assertEquals(31.2304, telemetry.getGpsLatitude());
    assertEquals(1, historyRepository.count());
  }

  @Test
  void terminalExecutionStoresAtMostOneTrackPoint() {
    Instant firstAt = Instant.parse("2026-07-15T06:00:00Z");
    BridgeGnssFix firstFix = new BridgeGnssFix(
        true, false, "gps_link", 31.2304, 121.4737, null, 4, "RTK_FIXED", 18, 0.8,
        null, null, 0.1, firstAt);
    BridgeRobotSnapshot first = new BridgeRobotSnapshot(
        ROBOT_ID, firstAt, "1.0", "boot-gps", "succeeded", "execution-terminal",
        "test-build", 1, Map.of(), null, firstFix);
    locationService.applySnapshot(first, firstAt);

    Instant secondAt = firstAt.plusSeconds(10);
    BridgeGnssFix secondFix = new BridgeGnssFix(
        true, false, "gps_link", 31.2304, 121.4737, null, 4, "RTK_FIXED", 18, 0.8,
        null, null, 0.1, secondAt);
    BridgeRobotSnapshot second = new BridgeRobotSnapshot(
        ROBOT_ID, secondAt, "1.0", "boot-gps", "succeeded", "execution-terminal",
        "test-build", 2, Map.of(), null, secondFix);
    locationService.applySnapshot(second, secondAt);

    assertEquals(1, historyRepository.count());
  }

  @Test
  void viewerCanReadLocationButNotTrack() throws Exception {
    Instant now = Instant.parse("2026-07-15T06:00:00Z");
    BridgeGnssFix fix = new BridgeGnssFix(
        true, false, "gps_link", 31.2304, 121.4737, null, 3, "SINGLE_POINT", 10, 1.2, null, null, null, now);
    BridgeRobotSnapshot snapshot = new BridgeRobotSnapshot(
        ROBOT_ID, now, "1.0", "boot-gps", "idle", "test-build", 0, Map.of(), fix);
    heartbeatService.applyBridgeSnapshot(snapshot, now);
    locationService.applySnapshot(snapshot, now);

    String token = login("viewer", "View@123");

    mockMvc.perform(get("/api/v1/robots/{id}/location", ROBOT_ID).header("Authorization", bearer(token)))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/robots/{id}/track", ROBOT_ID).header("Authorization", bearer(token)))
        .andExpect(status().isForbidden());
  }

  @Test
  void trackRejectsNonPositiveLimit() throws Exception {
    String token = login("dispatcher", "Disp@123");

    mockMvc.perform(get("/api/v1/robots/{id}/track", ROBOT_ID)
            .header("Authorization", bearer(token))
            .param("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("INVALID_TRACK_POINT_LIMIT: limit must be at least 1"));
  }

  private Map<String, Object> robotIdentity() {
    Map<String, Object> identity = new LinkedHashMap<>();
    identity.put("id", ROBOT_ID);
    identity.put("name", "GPS 测试机器人");
    identity.put("serialNo", "SN-GPS-001");
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

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
