package com.powerinspection.agent.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerinspection.agent.AgentLlmGateway;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmAgentPlannerTests {
  @Test
  void delegatesEveryPlanningStepToTheLlmGateway() {
    AgentLlmGateway gateway = mock(AgentLlmGateway.class);
    LlmAgentPlanner planner = new LlmAgentPlanner(gateway);
    AgentPlanningContext context = new AgentPlanningContext(
      "case_1", "run_1", "user_1", "inspect dynamically",
      Map.of("taskId", "task_1"), List.of(), List.of(), Instant.now());
    PlannerDecision expected = PlannerDecision.callTool(
      "inspect robot first", "get_robot", Map.of("robotId", "robot_1"), List.of());
    when(gateway.decide(context)).thenReturn(expected);

    assertThat(planner.decide(context)).isSameAs(expected);
    verify(gateway).decide(context);
  }
}
