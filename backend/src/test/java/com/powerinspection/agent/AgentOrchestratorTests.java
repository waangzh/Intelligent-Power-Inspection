package com.powerinspection.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.powerinspection.agent.api.AgentDtos;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.planner.AgentPlanningContext;
import com.powerinspection.agent.planner.LlmAgentPlanner;
import com.powerinspection.agent.planner.PlannerConclusion;
import com.powerinspection.agent.planner.PlannerDecision;
import com.powerinspection.agent.planner.PlannerQuestion;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class AgentOrchestratorTests {
  private static final String ALARM_ID = "alarm_agent_orchestrator_test";
  @Autowired AuditedAgentService agentService;
  @Autowired UserRepository userRepository;
  @Autowired DataStoreService dataStore;
  @MockBean LlmAgentPlanner llmPlanner;
  private UserEntity dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = userRepository.findByUsername("dispatcher").orElseThrow();
    dataStore.upsert(DataCategory.ALARM, new LinkedHashMap<>(Map.of(
      "id", ALARM_ID, "routeName", "Agent 测试路线", "type", "FIRE", "severity", "HIGH",
      "message", "Agent 编排测试告警", "imageUrl", "https://example.test/alarm.jpg", "acknowledged", false
    )));
  }

  @Test
  void blocksDuplicateToolCallWithSameArguments() throws Exception {
    when(llmPlanner.decide(any())).thenReturn(PlannerDecision.callTool("读取告警", "get_alarm", Map.of("alarmId", ALARM_ID), List.of()));
    AgentDtos.RunDetail detail = awaitTerminal(start());
    assertThat(detail.run().status()).isEqualTo(AgentEnums.RunStatus.FAILED);
    assertThat(detail.run().errorCode()).isEqualTo("DUPLICATE_TOOL_CALL");
  }

  @Test
  void blocksDuplicateToolCallWhenArgumentOrderDiffers() throws Exception {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("alarmId", ALARM_ID);
    first.put("taskId", "task_seed_001");
    Map<String, Object> second = new LinkedHashMap<>();
    second.put("taskId", "task_seed_001");
    second.put("alarmId", ALARM_ID);
    when(llmPlanner.decide(any())).thenAnswer(invocation -> {
      AgentPlanningContext context = invocation.getArgument(0);
      return PlannerDecision.callTool("list work orders", "list_related_work_orders", context.evidence().isEmpty() ? first : second, List.of());
    });

    AgentDtos.RunDetail detail = awaitTerminal(start());

    assertThat(detail.run().status()).isEqualTo(AgentEnums.RunStatus.FAILED);
    assertThat(detail.run().errorCode()).isEqualTo("DUPLICATE_TOOL_CALL");
    assertThat(detail.toolCalls()).hasSize(1);
  }

  @Test
  void enforcesMaximumToolCalls() throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    when(llmPlanner.decide(any())).thenAnswer(invocation -> PlannerDecision.callTool(
      "读取任务", "get_task", Map.of("taskId", "task_limit_" + sequence.incrementAndGet()), List.of()
    ));
    AgentDtos.RunDetail detail = awaitTerminal(start());
    assertThat(detail.run().status()).isEqualTo(AgentEnums.RunStatus.STEP_LIMIT_REACHED);
    assertThat(detail.run().errorCode()).isEqualTo("MAX_TOOL_CALLS_REACHED");
  }

  @Test
  void enforcesMaximumVisionCalls() throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    when(llmPlanner.decide(any())).thenAnswer(invocation -> PlannerDecision.callTool(
      "复核图像", "inspect_alarm_image", Map.of("alarmId", ALARM_ID, "taskId", "task_vision_" + sequence.incrementAndGet()), List.of()
    ));
    AgentDtos.RunDetail detail = awaitTerminal(start());
    assertThat(detail.run().status()).isEqualTo(AgentEnums.RunStatus.STEP_LIMIT_REACHED);
    assertThat(detail.run().errorCode()).isEqualTo("MAX_VISION_CALLS_REACHED");
  }

  @Test
  void persistsAskHumanQuestion() throws Exception {
    when(llmPlanner.decide(any())).thenReturn(PlannerDecision.askHuman(
      "需要人工确认", new PlannerQuestion("CONFIRM_SCOPE", "请确认告警处置范围。", List.of("继续", "取消")), List.of()
    ));
    AgentDtos.RunDetail detail = awaitTerminal(start());
    assertThat(detail.run().status()).isEqualTo(AgentEnums.RunStatus.WAITING_HUMAN);
    assertThat(detail.question().question().path("type").asText()).isEqualTo("CONFIRM_SCOPE");
  }

  @Test
  void finishesAfterToolEvidenceAndNeverExecutesUnknownTool() throws Exception {
    when(llmPlanner.decide(any())).thenAnswer(invocation -> {
      AgentPlanningContext context = invocation.getArgument(0);
      if (context.evidence().isEmpty()) {
        return PlannerDecision.callTool("读取告警", "get_alarm", Map.of("alarmId", ALARM_ID), List.of());
      }
      return PlannerDecision.finish("证据充分", new PlannerConclusion(AgentEnums.RiskLevel.HIGH, "告警已核对", List.of("人工复核")), context.evidenceIds(), 0.9);
    });
    AgentDtos.RunDetail detail = awaitTerminal(start());
    assertThat(detail.run().status()).isEqualTo(AgentEnums.RunStatus.SUCCEEDED);
    assertThat(detail.run().completedAt()).isNotNull();
    assertThat(detail.toolCalls()).extracting(AgentDtos.ToolCallResponse::toolName).containsExactly("get_alarm");
  }

  @Test
  void rejectsUnknownToolFromPlannerAndFallsBackWithoutExecutingIt() throws Exception {
    when(llmPlanner.decide(any())).thenReturn(PlannerDecision.callTool("非法工具", "delete_everything", Map.of("alarmId", ALARM_ID), List.of()));
    AgentDtos.RunDetail detail = awaitTerminal(start());
    assertThat(detail.run().status()).isEqualTo(AgentEnums.RunStatus.SUCCEEDED);
    assertThat(detail.run().degraded()).isTrue();
    assertThat(detail.toolCalls()).extracting(AgentDtos.ToolCallResponse::toolName).doesNotContain("delete_everything");
  }

  @Test
  void rejectsIllegalToolArgumentsAndFallsBackWithoutExecutingThem() throws Exception {
    when(llmPlanner.decide(any())).thenReturn(PlannerDecision.callTool("非法参数", "get_alarm", Map.of("path", "C:/sensitive"), List.of()));
    AgentDtos.RunDetail detail = awaitTerminal(start());
    assertThat(detail.run().status()).isEqualTo(AgentEnums.RunStatus.WAITING_APPROVAL);
    assertThat(detail.run().degraded()).isTrue();
    assertThat(detail.toolCalls()).allSatisfy(call -> assertThat(call.arguments().has("path")).isFalse());
  }

  private AgentDtos.RunSummary start() {
    AgentDtos.CaseSummary agentCase = agentService.createCase(new AgentDtos.CreateCaseRequest("编排器验收", null, ALARM_ID, "HIGH", null), dispatcher);
    return agentService.startRun(agentCase.id(), new AgentDtos.StartRunRequest("ORCHESTRATOR_TEST"), dispatcher);
  }

  private AgentDtos.RunDetail awaitTerminal(AgentDtos.RunSummary run) throws Exception {
    for (int attempt = 0; attempt < 100; attempt += 1) {
      AgentDtos.RunDetail detail = agentService.runDetail(run.id());
      if (detail.run().status() == AgentEnums.RunStatus.SUCCEEDED || detail.run().status() == AgentEnums.RunStatus.FAILED || detail.run().status() == AgentEnums.RunStatus.STEP_LIMIT_REACHED || detail.run().status() == AgentEnums.RunStatus.WAITING_HUMAN || detail.run().status() == AgentEnums.RunStatus.WAITING_APPROVAL) {
        return detail;
      }
      Thread.sleep(25);
    }
    throw new AssertionError("run did not reach a terminal or waiting state");
  }
}
