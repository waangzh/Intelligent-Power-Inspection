package com.powerinspection.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.powerinspection.agent.api.AgentDtos;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.domain.AgentRunEntity;
import com.powerinspection.agent.persistence.AgentRunRepository;
import com.powerinspection.agent.planner.LlmAgentPlanner;
import com.powerinspection.agent.planner.PlannerDecision;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = { "app.agent.orchestration.max-steps=3", "app.agent.orchestration.max-tool-calls=10" })
class AgentOrchestratorLimitTests {
  private static final String ALARM_ID = "alarm_agent_limit_test";
  @Autowired AuditedAgentService agentService;
  @Autowired UserRepository userRepository;
  @Autowired DataStoreService dataStore;
  @Autowired AgentOrchestratorProperties limits;
  @Autowired AgentOrchestrator orchestrator;
  @Autowired AgentRunRepository runRepository;
  @MockBean LlmAgentPlanner llmPlanner;
  private UserEntity dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = userRepository.findByUsername("dispatcher").orElseThrow();
    dataStore.upsert(DataCategory.ALARM, new LinkedHashMap<>(Map.of(
      "id", ALARM_ID, "routeName", "Agent 测试路线", "type", "FIRE", "severity", "HIGH",
      "message", "Agent 限额测试告警", "acknowledged", false
    )));
  }

  @AfterEach
  void resetTimeout() { limits.setRunTimeout(Duration.ofMinutes(10)); }

  @Test
  void enforcesMaximumPlanningSteps() throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    when(llmPlanner.decide(any())).thenAnswer(invocation -> PlannerDecision.callTool(
      "读取任务", "get_task", Map.of("taskId", "task_step_" + sequence.incrementAndGet()), List.of()
    ));
    AgentDtos.RunDetail detail = awaitTerminal(start());
    assertThat(detail.run().status()).isEqualTo(AgentEnums.RunStatus.STEP_LIMIT_REACHED);
    assertThat(detail.run().errorCode()).isEqualTo("MAX_STEPS_REACHED");
  }

  @Test
  void timesOutRunBeforePlanningWhenDeadlineHasPassed() throws Exception {
    limits.setRunTimeout(Duration.ofMillis(1));
    AgentDtos.RunDetail detail = awaitTerminal(start());
    assertThat(detail.run().status()).isEqualTo(AgentEnums.RunStatus.TIMED_OUT);
    assertThat(detail.run().errorCode()).isEqualTo("RUN_TIMEOUT");
  }

  @Test
  void marksInterruptedRunFailedDuringStartupRecovery() {
    AgentDtos.CaseSummary created = agentService.createCase(
      new AgentDtos.CreateCaseRequest("recovery check", null, ALARM_ID, "HIGH", null), dispatcher
    );
    Instant now = Instant.now();
    AgentRunEntity run = new AgentRunEntity();
    run.setId(Ids.next("agent_run"));
    run.setCaseId(created.id());
    run.setRunNumber(1);
    run.setStatus(AgentEnums.RunStatus.RUNNING);
    run.setGoalSnapshot(created.goal());
    run.setInputSnapshotJson("{}");
    run.setPlannerType("LLM_CONSTRAINED");
    run.setStartedAt(now);
    run.setCreatedById(dispatcher.getId());
    run.setCreatedAt(now);
    runRepository.save(run);

    orchestrator.recoverExpiredRuns();

    AgentRunEntity recovered = runRepository.findById(run.getId()).orElseThrow();
    assertThat(recovered.getStatus()).isEqualTo(AgentEnums.RunStatus.FAILED);
    assertThat(recovered.getErrorCode()).isEqualTo("RECOVERY_INTERRUPTED_RUN");
    assertThat(recovered.getCompletedAt()).isNotNull();
  }

  private AgentDtos.RunSummary start() {
    AgentDtos.CaseSummary agentCase = agentService.createCase(new AgentDtos.CreateCaseRequest("限额验收", null, ALARM_ID, "HIGH", null), dispatcher);
    return agentService.startRun(agentCase.id(), new AgentDtos.StartRunRequest("LIMIT_TEST"), dispatcher);
  }

  private AgentDtos.RunDetail awaitTerminal(AgentDtos.RunSummary run) throws Exception {
    for (int attempt = 0; attempt < 100; attempt += 1) {
      AgentDtos.RunDetail detail = agentService.runDetail(run.id());
      if (detail.run().status() == AgentEnums.RunStatus.STEP_LIMIT_REACHED || detail.run().status() == AgentEnums.RunStatus.TIMED_OUT) return detail;
      Thread.sleep(25);
    }
    throw new AssertionError("run did not reach a limit state");
  }
}
