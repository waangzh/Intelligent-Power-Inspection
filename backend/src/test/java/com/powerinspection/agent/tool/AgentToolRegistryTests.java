package com.powerinspection.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.powerinspection.agent.AgentToolService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentToolRegistryTests {
  @Test
  @SuppressWarnings("unchecked")
  void preservesStructuredVisionDetailsInEvidence() {
    AgentToolService toolService = mock(AgentToolService.class);
    Map<String, Object> rawResult = Map.of(
      "provider", "LocateAnything",
      "detections", List.of(Map.of("label", "fire", "score", 0.94)));
    when(toolService.inspectAlarmImage("alarm_1", "task_1")).thenReturn(List.of(Map.of(
      "title", "vision result",
      "content", "fire detected",
      "sourceId", "alarm_1",
      "imageUrl", "https://example.test/image.jpg",
      "payload", Map.of(
        "score", 0.94,
        "bbox", List.of(10, 20, 30, 40),
        "label", "fire",
        "imageUrl", "https://example.test/result.jpg",
        "rawResult", rawResult))));
    AgentToolRegistry registry = new AgentToolRegistry(toolService);
    AgentTool<AgentToolInputs.InspectAlarmImageInput, Object> tool =
      (AgentTool<AgentToolInputs.InspectAlarmImageInput, Object>) registry.find("inspect_alarm_image").orElseThrow();

    AgentToolResult<Object> result = tool.execute(
      new AgentToolInputs.InspectAlarmImageInput("alarm_1", "task_1"),
      new AgentToolExecutionContext("case_1", "run_1", null));

    Map<String, Object> evidencePayload = result.evidence().get(0).payload();
    Map<String, Object> item = (Map<String, Object>) ((List<?>) evidencePayload.get("items")).get(0);
    Map<String, Object> payload = (Map<String, Object>) item.get("payload");
    assertThat(payload).containsEntry("score", 0.94).containsEntry("label", "fire");
    assertThat(payload.get("bbox")).isEqualTo(List.of(10, 20, 30, 40));
    assertThat((Map<String, Object>) payload.get("rawResult")).containsEntry("provider", "LocateAnything");
  }
}
