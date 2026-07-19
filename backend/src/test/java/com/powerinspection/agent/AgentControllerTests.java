package com.powerinspection.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import com.powerinspection.user.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerTests {
  private static final String HIGH_ALARM_ID = "agent_controller_alarm_high";
  private static final String MEDIUM_ALARM_ID = "agent_controller_alarm_medium";

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  @Autowired UserRepository userRepository;

  @Autowired PasswordEncoder passwordEncoder;

  @Autowired DataStoreService dataStore;

  @MockBean AgentLlmGateway agentLlmGateway;

  @BeforeEach
  void setUpData() {
    saveUser("agent_controller_dispatcher", "dispatcher", "Disp@123", UserRole.DISPATCHER);
    saveUser("agent_controller_approver", "approver", "Appr@123", UserRole.ADMIN);
    saveUser("agent_controller_viewer", "viewer", "View@123", UserRole.VIEWER);
    saveAlarm(HIGH_ALARM_ID, "HIGH", "agent controller high alarm");
    saveAlarm(MEDIUM_ALARM_ID, "MEDIUM", "agent controller medium alarm");
  }

  @Test
  void dispatcherCreatesSessionAndConfirmsSafeWorkOrderAction() throws Exception {
    given(agentLlmGateway.analyze(any(), any()))
        .willReturn(
            new AgentLlmAnalysis(
                "HIGH", "检测到未佩戴安全帽，存在现场作业风险。", List.of("创建工单安排现场复核"), List.of("告警证据"), 0.82));

    String token = login("dispatcher", "Disp@123");
    String approverToken = login("approver", "Appr@123");
    String created =
        mockMvc
            .perform(
                post("/api/v1/agents/sessions")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"alarmId\":\"" + HIGH_ALARM_ID + "\",\"prompt\":\"请优先判断是否需要派单\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    String sessionId = objectMapper.readTree(created).path("data").path("id").asText();
    JsonNode data = awaitSession(sessionId, token, "SUCCEEDED");
    assertThat(data.path("analysis").path("defectLevel").asText()).isEqualTo("HIGH");
    assertThat(hasEvidenceType(data, "ALARM")).isTrue();
    String createActionId = firstActionId(data, "CREATE_WORK_ORDER_DRAFT");
    mockMvc
        .perform(
            post("/api/v1/agents/actions/" + createActionId + "/confirm")
                .header("Authorization", bearer(approverToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.data.resultRef.id").exists());
  }

  @Test
  void viewerCannotRunAgent() throws Exception {
    String token = login("viewer", "View@123");
    mockMvc
        .perform(
            post("/api/v1/agents/sessions")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alarmId\":\"" + HIGH_ALARM_ID + "\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void listsAndLoadsAgentSessionDetail() throws Exception {
    given(agentLlmGateway.analyze(any(), any()))
        .willReturn(
            new AgentLlmAnalysis("MEDIUM", "告警需要复核。", List.of("查看告警图像"), List.of("告警证据"), 0.7));

    String token = login("dispatcher", "Disp@123");
    String created =
        mockMvc
            .perform(
                post("/api/v1/agents/sessions")
                    .header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"alarmId\":\"" + MEDIUM_ALARM_ID + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    String sessionId = objectMapper.readTree(created).path("data").path("id").asText();

    mockMvc
        .perform(get("/api/v1/agents/sessions").header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").exists());

    mockMvc
        .perform(get("/api/v1/agents/sessions/" + sessionId).header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.latestRun.steps[0].type").value("RUN_STARTED"));
  }

  private String firstActionId(JsonNode session, String type) {
    for (JsonNode action : session.path("actions")) {
      if (type.equals(action.path("type").asText())) {
        return action.path("id").asText();
      }
    }
    throw new AssertionError("action not found: " + type);
  }

  private void saveUser(String id, String username, String password, UserRole role) {
    UserEntity user = userRepository.findByUsername(username).orElseGet(UserEntity::new);
    if (user.getId() == null) {
      user.setId(id);
    }
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setDisplayName(username);
    user.setRole(role);
    user.setEnabled(true);
    user.setCreatedAt(Instant.now().toString());
    userRepository.save(user);
  }

  private void saveAlarm(String id, String severity, String message) {
    Map<String, Object> alarm = new LinkedHashMap<>();
    alarm.put("id", id);
    alarm.put("severity", severity);
    alarm.put("message", message);
    alarm.put("createdAt", Instant.now().toString());
    dataStore.upsert(DataCategory.ALARM, alarm);
  }

  private JsonNode awaitSession(String sessionId, String token, String status) throws Exception {
    for (int i = 0; i < 20; i += 1) {
      String response =
          mockMvc
              .perform(
                  get("/api/v1/agents/sessions/" + sessionId)
                      .header("Authorization", bearer(token)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString(StandardCharsets.UTF_8);
      JsonNode data = objectMapper.readTree(response).path("data");
      if (status.equals(data.path("status").asText())) {
        return data;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("agent session did not reach status " + status);
  }

  private boolean hasEvidenceType(JsonNode session, String type) {
    for (JsonNode evidence : session.path("evidence")) {
      if (type.equals(evidence.path("type").asText())) {
        return true;
      }
    }
    return false;
  }

  private String login(String username, String password) throws Exception {
    String body =
        "{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"remember\":true}";
    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
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
