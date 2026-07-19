package com.powerinspection.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.powerinspection.agent.action.AgentActionExecutor;
import com.powerinspection.agent.api.AgentDtos;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import com.powerinspection.user.UserRole;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class AuditedAgentServiceTests {
  @Autowired AuditedAgentService agentService;

  @Autowired DataStoreService dataStore;

  @Autowired UserRepository userRepository;

  @MockBean AgentLlmGateway agentLlmGateway;

  @MockBean AgentActionExecutor actionExecutor;

  private UserEntity dispatcher;
  private UserEntity approver;
  private final AtomicInteger actionExecutionCount = new AtomicInteger();

  @BeforeEach
  void setUp() {
    actionExecutionCount.set(0);
    dispatcher = userRepository.findById("agent_test_dispatcher").orElseGet(UserEntity::new);
    dispatcher.setId("agent_test_dispatcher");
    dispatcher.setUsername("agent-test-dispatcher");
    dispatcher.setPasswordHash("unused");
    dispatcher.setDisplayName("测试调度员");
    dispatcher.setRole(UserRole.DISPATCHER);
    dispatcher.setEnabled(true);
    dispatcher.setCreatedAt(Instant.now().toString());
    userRepository.save(dispatcher);

    approver = userRepository.findById("agent_test_approver").orElseGet(UserEntity::new);
    approver.setId("agent_test_approver");
    approver.setUsername("agent-test-approver");
    approver.setPasswordHash("unused");
    approver.setDisplayName("测试审批员");
    // Separate dispatcher: initiator cannot approve their own actions; ADMIN lacks TASK_DISPATCH.
    approver.setRole(UserRole.DISPATCHER);
    approver.setEnabled(true);
    approver.setCreatedAt(Instant.now().toString());
    userRepository.save(approver);

    seedAlarm("agent_test_alarm", "HIGH", "测试安全帽告警");
    seedAlarm("agent_test_alarm_iso", "HIGH", "测试安全帽告警-隔离");
    seedAlarm("agent_test_alarm_fallback", "HIGH", "测试安全帽告警-降级");

    when(agentLlmGateway.analyze(any(), any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              List<Map<String, Object>> evidence = invocation.getArgument(1);
              return new AgentLlmAnalysis(
                  "HIGH",
                  "告警证据支持现场复核。",
                  List.of("建议创建工单"),
                  List.of(String.valueOf(evidence.get(0).get("id"))),
                  0.82);
            });
    when(actionExecutor.execute(any(), any()))
        .thenAnswer(
            invocation -> Map.of("id", "side-effect-" + actionExecutionCount.incrementAndGet()));
  }

  private void seedAlarm(String id, String severity, String message) {
    Map<String, Object> alarm = new LinkedHashMap<>();
    alarm.put("id", id);
    alarm.put("severity", severity);
    alarm.put("message", message);
    alarm.put("imageUrl", "https://example.test/alarm.jpg");
    alarm.put("createdAt", Instant.now().toString());
    dataStore.upsert(DataCategory.ALARM, alarm);
  }

  @Test
  void keepsEvidenceAndActionsIsolatedPerRunAndRejectsStaleApproval() throws Exception {
    AgentDtos.CaseSummary agentCase =
        agentService.createCase(
            new AgentDtos.CreateCaseRequest(
                "判断告警是否需要创建工单", null, "agent_test_alarm_iso", "HIGH", "现场文字仅作为证据"),
            dispatcher);
    AgentDtos.RunSummary firstRun =
        agentService.startRun(
            agentCase.id(), new AgentDtos.StartRunRequest("INITIAL_ANALYSIS"), dispatcher);
    AgentDtos.RunDetail first = awaitRun(firstRun.id());

    assertThat(first.conclusion()).isNotNull();
    assertThat(first.conclusion().evidenceReferences()).isNotEmpty();
    assertThat(first.conclusion().evidenceReferences())
        .allSatisfy(
            reference ->
                assertThat(first.evidence())
                    .extracting(AgentDtos.EvidenceResponse::id)
                    .contains(reference.evidenceId()));
    assertThat(first.actions())
        .extracting(AgentDtos.ActionResponse::status)
        .contains(AgentEnums.ActionStatus.PROPOSED);

    AgentDtos.ActionResponse workOrder =
        first.actions().stream()
            .filter(item -> item.type() == AgentEnums.ActionType.CREATE_WORK_ORDER_DRAFT)
            .findFirst()
            .orElseThrow();
    AgentDtos.ActionResponse executed =
        agentService.approveAction(
            workOrder.id(),
            new AgentDtos.ActionDecisionRequest(workOrder.version(), "同意建单"),
            approver);
    assertThat(executed.status()).isEqualTo(AgentEnums.ActionStatus.SUCCEEDED);
    assertThatThrownBy(
            () ->
                agentService.approveAction(
                    workOrder.id(),
                    new AgentDtos.ActionDecisionRequest(workOrder.version(), "重复批准"),
                    approver))
        .isInstanceOf(ApiException.class)
        .satisfies(error -> assertThat(((ApiException) error).status().value()).isEqualTo(409));

    AgentDtos.RunSummary secondRun =
        agentService.startRun(
            agentCase.id(), new AgentDtos.StartRunRequest("DATA_UPDATED"), dispatcher);
    AgentDtos.RunDetail second = awaitRun(secondRun.id());
    assertThat(second.run().id()).isNotEqualTo(first.run().id());
    assertThat(second.evidence())
        .allSatisfy(
            item ->
                assertThat(item.id())
                    .isNotIn(
                        first.evidence().stream().map(AgentDtos.EvidenceResponse::id).toList()));
  }

  @Test
  void fallsBackWhenLlmReferencesEvidenceOutsideTheCurrentRun() throws Exception {
    doReturn(
            new AgentLlmAnalysis(
                "HIGH",
                "untrusted conclusion",
                List.of("create action"),
                List.of("evidence_from_another_run"),
                0.8))
        .when(agentLlmGateway)
        .analyze(any(), any());
    AgentDtos.CaseSummary agentCase =
        agentService.createCase(
            new AgentDtos.CreateCaseRequest(
                "verify evidence validation", null, "agent_test_alarm_fallback", "HIGH", null),
            dispatcher);

    AgentDtos.RunDetail run =
        awaitRun(
            agentService
                .startRun(
                    agentCase.id(),
                    new AgentDtos.StartRunRequest("INVALID_LLM_EVIDENCE"),
                    dispatcher)
                .id());

    assertThat(run.evidence())
        .extracting(AgentDtos.EvidenceResponse::sourceType)
        .contains(AgentEnums.EvidenceSourceType.LLM_FALLBACK);
    assertThat(run.conclusion().evidenceReferences())
        .isNotEmpty()
        .allSatisfy(
            reference ->
                assertThat(run.evidence())
                    .extracting(AgentDtos.EvidenceResponse::id)
                    .contains(reference.evidenceId()));
  }

  @Test
  void concurrentApprovalExecutesOnlyOneSideEffect() throws Exception {
    Map<String, Object> alarm = new LinkedHashMap<>();
    alarm.put("id", "agent_test_alarm_concurrent");
    alarm.put("severity", "HIGH");
    alarm.put("message", "concurrent approval alarm");
    alarm.put("createdAt", Instant.now().toString());
    dataStore.upsert(DataCategory.ALARM, alarm);
    AgentDtos.CaseSummary agentCase =
        agentService.createCase(
            new AgentDtos.CreateCaseRequest(
                "verify approval concurrency", null, "agent_test_alarm_concurrent", "HIGH", null),
            dispatcher);
    AgentDtos.RunDetail run =
        awaitRun(
            agentService
                .startRun(
                    agentCase.id(),
                    new AgentDtos.StartRunRequest("CONCURRENT_APPROVAL"),
                    dispatcher)
                .id());
    AgentDtos.ActionResponse action =
        run.actions().stream()
            .filter(item -> item.type() == AgentEnums.ActionType.CREATE_WORK_ORDER_DRAFT)
            .findFirst()
            .orElseThrow();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<String> approval =
        () -> {
          ready.countDown();
          start.await(5, TimeUnit.SECONDS);
          try {
            return agentService
                .approveAction(
                    action.id(),
                    new AgentDtos.ActionDecisionRequest(action.version(), "concurrent approval"),
                    approver)
                .status()
                .name();
          } catch (ApiException ex) {
            return "HTTP_" + ex.status().value();
          }
        };
    ExecutorService workers = Executors.newFixedThreadPool(2);
    try {
      Future<String> first = workers.submit(approval);
      Future<String> second = workers.submit(approval);
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder("SUCCEEDED", "HTTP_409");
    } finally {
      workers.shutdownNow();
    }
    assertThat(actionExecutionCount).hasValue(1);
  }

  private AgentDtos.RunDetail awaitRun(String runId) throws Exception {
    for (int attempt = 0; attempt < 50; attempt += 1) {
      AgentDtos.RunDetail detail = agentService.runDetail(runId);
      if (detail.run().status() == AgentEnums.RunStatus.SUCCEEDED
          || detail.run().status() == AgentEnums.RunStatus.WAITING_APPROVAL) {
        return detail;
      }
      if (detail.run().status() == AgentEnums.RunStatus.FAILED) {
        throw new AssertionError("run failed: " + detail.run().errorMessage());
      }
      Thread.sleep(50);
    }
    throw new AssertionError("run did not finish");
  }
}
