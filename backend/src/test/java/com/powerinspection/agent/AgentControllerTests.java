package com.powerinspection.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerTests {
  @Autowired
  MockMvc mockMvc;

  @Autowired
  ObjectMapper objectMapper;

  @MockBean
  AgentLlmGateway agentLlmGateway;

  @Test
  void dispatcherCreatesSessionAndConfirmsSafeWorkOrderAction() throws Exception {
    given(agentLlmGateway.analyze(any(), any())).willReturn(new AgentLlmAnalysis(
      "HIGH",
      "检测到未佩戴安全帽，存在现场作业风险。",
      List.of("创建工单安排现场复核"),
      List.of("告警证据"),
      0.82
    ));

    String token = login("dispatcher", "Disp@123");
    String created = mockMvc.perform(post("/api/v1/agents/sessions")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"alarmId\":\"alarm_seed_001\",\"prompt\":\"请优先判断是否需要派单\"}"))
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
    mockMvc.perform(post("/api/v1/agents/actions/" + createActionId + "/confirm")
        .header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
      .andExpect(jsonPath("$.data.resultRef.id").exists());
  }

  @Test
  void viewerCannotRunAgent() throws Exception {
    String token = login("viewer", "View@123");
    mockMvc.perform(post("/api/v1/agents/sessions")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"alarmId\":\"alarm_seed_001\"}"))
      .andExpect(status().isForbidden());
  }

  @Test
  void listsAndLoadsAgentSessionDetail() throws Exception {
    given(agentLlmGateway.analyze(any(), any())).willReturn(new AgentLlmAnalysis(
      "MEDIUM",
      "告警需要复核。",
      List.of("查看告警图像"),
      List.of("告警证据"),
      0.7
    ));

    String token = login("dispatcher", "Disp@123");
    String created = mockMvc.perform(post("/api/v1/agents/sessions")
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"alarmId\":\"alarm_seed_003\"}"))
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString(StandardCharsets.UTF_8);
    String sessionId = objectMapper.readTree(created).path("data").path("id").asText();

    mockMvc.perform(get("/api/v1/agents/sessions").header("Authorization", bearer(token)))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].id").exists());

    mockMvc.perform(get("/api/v1/agents/sessions/" + sessionId).header("Authorization", bearer(token)))
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

  private JsonNode awaitSession(String sessionId, String token, String status) throws Exception {
    for (int i = 0; i < 20; i += 1) {
      String response = mockMvc.perform(get("/api/v1/agents/sessions/" + sessionId).header("Authorization", bearer(token)))
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
