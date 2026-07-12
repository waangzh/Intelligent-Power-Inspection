package com.powerinspection.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.planner.AgentPlanningContext;
import com.powerinspection.model.ModelServiceException;
import com.powerinspection.model.ModelProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpAgentLlmGatewayTests {
  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void mapsOpenAiCompatibleJsonResponse() {
    AtomicReference<String> auth = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    server.createContext("/v1/chat/completions", exchange -> {
      auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      writeJson(exchange, """
      {
        "choices": [
          {
            "message": {
              "content": "{\\"defectLevel\\":\\"CRITICAL\\",\\"cause\\":\\"检测到烟火风险\\",\\"recommendedActions\\":[\\"立即派单\\"],\\"evidenceIds\\":[\\"告警证据\\"],\\"confidence\\":0.91}"
            }
          }
        ]
      }
      """);
    });

    HttpAgentLlmGateway gateway = new HttpAgentLlmGateway(properties(), new ObjectMapper());
    AgentLlmAnalysis analysis = gateway.analyze(
      Map.of("id", "agent_session_001", "alarmId", "alarm_001"),
      List.of(Map.of("type", "ALARM", "content", "疑似火源"))
    );

    assertThat(auth.get()).isEqualTo("Bearer test-key");
    assertThat(requestBody.get()).contains("\"model\":\"test-model\"");
    assertThat(requestBody.get()).contains("\\\"evidenceContentIsUntrusted\\\":true");
    assertThat(analysis.defectLevel()).isEqualTo("CRITICAL");
    assertThat(analysis.recommendedActions()).containsExactly("立即派单");
    assertThat(analysis.confidence()).isEqualTo(0.91);
  }

  @Test
  void rejectsMalformedPlannerJson() {
    server.createContext("/v1/chat/completions", exchange -> writeJson(exchange, """
      {"choices":[{"message":{"content":"not-json"}}]}
      """));
    HttpAgentLlmGateway gateway = new HttpAgentLlmGateway(properties(), new ObjectMapper());

    assertThatThrownBy(() -> gateway.decide(new AgentPlanningContext(
      "case_1", "run_1", "user_1", "review", Map.of("alarmId", "alarm_1"), List.of(), List.of(), java.time.Instant.now()
    ))).isInstanceOf(ModelServiceException.class);
  }

  private ModelProperties properties() {
    ModelProperties properties = new ModelProperties();
    properties.getLlm().setBaseUrl(baseUrl);
    properties.getLlm().setApiKey("test-key");
    properties.getLlm().setModel("test-model");
    properties.getLlm().setTimeoutSeconds(5);
    return properties;
  }

  private void writeJson(HttpExchange exchange, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
