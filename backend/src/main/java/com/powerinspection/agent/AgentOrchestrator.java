package com.powerinspection.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.api.AgentDtos;
import com.powerinspection.agent.domain.AgentCaseEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.domain.AgentEvidenceEntity;
import com.powerinspection.agent.domain.AgentRunEntity;
import com.powerinspection.agent.domain.AgentStepEntity;
import com.powerinspection.agent.domain.AgentToolCallEntity;
import com.powerinspection.agent.persistence.AgentCaseRepository;
import com.powerinspection.agent.persistence.AgentEvidenceRepository;
import com.powerinspection.agent.persistence.AgentRunRepository;
import com.powerinspection.agent.persistence.AgentStepRepository;
import com.powerinspection.agent.persistence.AgentToolCallRepository;
import com.powerinspection.agent.planner.AgentPlanningContext;
import com.powerinspection.agent.planner.LlmAgentPlanner;
import com.powerinspection.agent.planner.PlannerDecision;
import com.powerinspection.agent.planner.PlannerDecisionType;
import com.powerinspection.agent.planner.PlannerDecisionValidator;
import com.powerinspection.agent.planner.PlannerValidationException;
import com.powerinspection.agent.planner.PlanningEvidence;
import com.powerinspection.agent.planner.RuleBasedAgentPlanner;
import com.powerinspection.agent.tool.AgentEvidenceDraft;
import com.powerinspection.agent.tool.AgentToolExecution;
import com.powerinspection.agent.tool.AgentToolExecutionContext;
import com.powerinspection.agent.tool.AgentToolExecutionException;
import com.powerinspection.agent.tool.AgentToolExecutor;
import com.powerinspection.agent.tool.AgentToolRegistry;
import com.powerinspection.common.Ids;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/** Durable, bounded Plan-Act-Observe loop. It never executes external write actions. */
@Service
public class AgentOrchestrator {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
  private final AgentCaseRepository caseRepository;
  private final AgentRunRepository runRepository;
  private final AgentStepRepository stepRepository;
  private final AgentToolCallRepository toolCallRepository;
  private final AgentEvidenceRepository evidenceRepository;
  private final UserRepository userRepository;
  private final LlmAgentPlanner llmPlanner;
  private final RuleBasedAgentPlanner rulePlanner;
  private final PlannerDecisionValidator decisionValidator;
  private final AgentToolRegistry toolRegistry;
  private final AgentToolExecutor toolExecutor;
  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;
  private final AgentOrchestratorProperties limits;
  private final ConcurrentHashMap<String, ReentrantLock> stepLocks = new ConcurrentHashMap<>();

  public AgentOrchestrator(AgentCaseRepository caseRepository, AgentRunRepository runRepository, AgentStepRepository stepRepository, AgentToolCallRepository toolCallRepository, AgentEvidenceRepository evidenceRepository, UserRepository userRepository, LlmAgentPlanner llmPlanner, RuleBasedAgentPlanner rulePlanner, PlannerDecisionValidator decisionValidator, AgentToolRegistry toolRegistry, AgentToolExecutor toolExecutor, ObjectMapper objectMapper, SimpMessagingTemplate messagingTemplate, AgentOrchestratorProperties limits) {
    this.caseRepository = caseRepository; this.runRepository = runRepository; this.stepRepository = stepRepository; this.toolCallRepository = toolCallRepository; this.evidenceRepository = evidenceRepository; this.userRepository = userRepository;
    this.llmPlanner = llmPlanner; this.rulePlanner = rulePlanner; this.decisionValidator = decisionValidator; this.toolRegistry = toolRegistry; this.toolExecutor = toolExecutor; this.objectMapper = objectMapper; this.messagingTemplate = messagingTemplate; this.limits = limits;
  }

  public AgentEnums.RunStatus execute(String runId) {
    AgentRunEntity run = runRepository.findById(runId).orElse(null);
    if (run == null || terminal(run.getStatus())) { return run == null ? AgentEnums.RunStatus.FAILED : run.getStatus(); }
    AgentCaseEntity agentCase = caseRepository.findById(run.getCaseId()).orElse(null);
    UserEntity user = userRepository.findById(run.getCreatedById()).orElse(null);
    if (agentCase == null || user == null) { return fail(run, agentCase, "RUN_CONTEXT_UNAVAILABLE", "运行上下文不可用", AgentEnums.RunStatus.FAILED); }
    String previousDecision = null;
    for (int step = 0; step < limits.getMaxSteps(); step += 1) {
      if (runRepository.findById(runId).map(item -> item.getStatus() == AgentEnums.RunStatus.CANCELLED).orElse(true)) { return AgentEnums.RunStatus.CANCELLED; }
      if (timedOut(run)) { return fail(run, agentCase, "RUN_TIMEOUT", "运行超过允许时长", AgentEnums.RunStatus.TIMED_OUT); }
      AgentPlanningContext context = context(run, agentCase);
      PlannerDecision decision;
      boolean degraded = false;
      try {
        decision = llmPlanner.decide(context);
        decisionValidator.validate(decision, context);
        run.setPlannerType("LLM_CONSTRAINED");
      } catch (Exception ex) {
        degraded = true;
        run.setPlannerType("RULE_BASED");
        run.setDegraded(true);
        run.setDegradationReason(abbreviate(reason(ex), 500));
        saveFallbackEvidence(agentCase, run, reason(ex));
        try {
          decision = rulePlanner.decide(context(run, agentCase));
          decisionValidator.validate(decision, context(run, agentCase));
        } catch (Exception fallbackError) {
          return fail(run, agentCase, code(fallbackError, "PLANNER_INVALID"), "规划决策无效", AgentEnums.RunStatus.FAILED);
        }
      }
      String signature = decision.type() + "|" + decision.toolName() + "|" + json(normalizedToolArguments(decision.toolArguments())) + "|" + decision.evidenceIds() + "|" + context.evidenceIds();
      if (signature.equals(previousDecision)) { return fail(run, agentCase, "PLANNER_LOOP_DETECTED", "检测到重复规划决策", AgentEnums.RunStatus.FAILED); }
      previousDecision = signature;
      recordStep(run, AgentEnums.StepType.PLAN_CREATED, decision.summary(), Map.of("plannerType", run.getPlannerType(), "degraded", degraded, "decisionType", decision.type().name()));
      run = runRepository.save(run);
      if (decision.type() == PlannerDecisionType.CALL_TOOL) {
        AgentEnums.RunStatus status = callTool(run, agentCase, user, decision);
        if (status != AgentEnums.RunStatus.RUNNING) { return status; }
        run = runRepository.findById(runId).orElse(run);
        continue;
      }
      if (decision.type() == PlannerDecisionType.ASK_HUMAN) {
        run.setPendingQuestionJson(json(decision.question())); run.setStatus(AgentEnums.RunStatus.WAITING_HUMAN); runRepository.save(run);
        agentCase.setStatus(AgentEnums.CaseStatus.WAITING_HUMAN); agentCase.setUpdatedAt(Instant.now()); caseRepository.save(agentCase);
        recordStep(run, AgentEnums.StepType.HUMAN_INPUT_REQUESTED, decision.summary(), Map.of("questionType", decision.question().type()));
        return run.getStatus();
      }
      if (decision.type() == PlannerDecisionType.FINISH) {
        List<AgentDtos.EvidenceReference> refs = decision.evidenceIds().stream().map(id -> new AgentDtos.EvidenceReference(id, "supporting", "规划结论引用" )).toList();
        run.setConclusionJson(json(new AgentDtos.AgentConclusion(decision.conclusion().defectLevel(), decision.conclusion().cause(), decision.conclusion().recommendedActions(), refs, decision.confidence())));
        // The legacy adapter may still create audited action proposals from this conclusion.
        // Keep the Run non-terminal until that compatibility finalization completes.
        run.setPendingQuestionJson(null); run.setStatus(AgentEnums.RunStatus.RUNNING); runRepository.save(run);
        agentCase.setStatus(AgentEnums.CaseStatus.RESOLVED); agentCase.setUpdatedAt(Instant.now()); caseRepository.save(agentCase);
        recordStep(run, AgentEnums.StepType.RUN_FINISHED, decision.summary(), Map.of("evidenceCount", refs.size(), "plannerType", run.getPlannerType()));
        return AgentEnums.RunStatus.SUCCEEDED;
      }
      return fail(run, agentCase, "UNSUPPORTED_PLANNER_DECISION", "不支持的规划决策", AgentEnums.RunStatus.FAILED);
    }
    return fail(run, agentCase, "MAX_STEPS_REACHED", "已达到最大规划步数", AgentEnums.RunStatus.STEP_LIMIT_REACHED);
  }

  public boolean cancel(String runId) {
    AgentRunEntity run = runRepository.findById(runId).orElseThrow(() -> new IllegalArgumentException("run not found"));
    if (terminal(run.getStatus())) { return false; }
    run.setStatus(AgentEnums.RunStatus.CANCELLED); run.setCompletedAt(Instant.now()); run.setErrorCode("RUN_CANCELLED"); run.setErrorMessage("运行已取消"); runRepository.save(run);
    caseRepository.findById(run.getCaseId()).ifPresent(item -> { item.setStatus(AgentEnums.CaseStatus.OPEN); item.setUpdatedAt(Instant.now()); caseRepository.save(item); });
    recordStep(run, AgentEnums.StepType.RUN_FAILED, "运行已取消", Map.of("errorCode", "RUN_CANCELLED"));
    return true;
  }

  public void recoverExpiredRuns() {
    for (AgentRunEntity run : runRepository.findByStatusIn(List.of(AgentEnums.RunStatus.RUNNING, AgentEnums.RunStatus.WAITING_TOOL))) {
      boolean expired = timedOut(run);
      String errorCode = expired ? "RECOVERY_STALE_RUN" : "RECOVERY_INTERRUPTED_RUN";
      String message = expired ? "检测到过期运行" : "检测到服务重启时中断的运行";
      AgentEnums.RunStatus status = expired ? AgentEnums.RunStatus.TIMED_OUT : AgentEnums.RunStatus.FAILED;
      caseRepository.findById(run.getCaseId()).ifPresent(agentCase -> fail(run, agentCase, errorCode, message, status));
    }
  }

  private AgentEnums.RunStatus callTool(AgentRunEntity run, AgentCaseEntity agentCase, UserEntity user, PlannerDecision decision) {
    List<AgentToolCallEntity> calls = toolCallRepository.findByRunIdOrderByCreatedAtAsc(run.getId());
    if (calls.size() >= limits.getMaxToolCalls()) { return fail(run, agentCase, "MAX_TOOL_CALLS_REACHED", "已达到最大工具调用次数", AgentEnums.RunStatus.STEP_LIMIT_REACHED); }
    long visionCalls = calls.stream().filter(item -> "inspect_alarm_image".equals(item.getToolName())).count();
    if ("inspect_alarm_image".equals(decision.toolName()) && visionCalls >= limits.getMaxVisionCalls()) { return fail(run, agentCase, "MAX_VISION_CALLS_REACHED", "已达到视觉调用上限", AgentEnums.RunStatus.STEP_LIMIT_REACHED); }
    Map<String, Object> arguments = normalizedToolArguments(decision.toolArguments());
    String argumentJson = json(arguments); String argumentHash = sha256(argumentJson);
    if (toolCallRepository.existsByRunIdAndToolNameAndArgumentsHashAndStatus(run.getId(), decision.toolName(), argumentHash, AgentEnums.ToolCallStatus.SUCCEEDED)) {
      return fail(run, agentCase, "DUPLICATE_TOOL_CALL", "重复工具调用已被拦截", AgentEnums.RunStatus.FAILED);
    }
    AgentStepEntity requested = recordStep(run, AgentEnums.StepType.TOOL_CALL_REQUESTED, decision.summary(), Map.of("toolName", decision.toolName()));
    AgentToolCallEntity call = new AgentToolCallEntity();
    Instant now = Instant.now();
    call.setId(Ids.next("agent_tool")); call.setCaseId(run.getCaseId()); call.setRunId(run.getId()); call.setStepNo(requested.getSequenceNo()); call.setSequenceNo(requested.getSequenceNo()); call.setToolName(decision.toolName()); call.setArgumentsJson(argumentJson); call.setArgumentsHash(argumentHash); call.setStatus(AgentEnums.ToolCallStatus.RUNNING); call.setReason(decision.summary()); call.setStartedAt(now); call.setCreatedAt(now); toolCallRepository.save(call);
    run.setStatus(AgentEnums.RunStatus.WAITING_TOOL); run = runRepository.save(run); recordStep(run, AgentEnums.StepType.TOOL_CALL_STARTED, decision.toolName() + " 开始执行", Map.of("toolCallId", call.getId()));
    try {
      AgentToolExecution execution = toolExecutor.execute(decision.toolName(), arguments, new AgentToolExecutionContext(run.getCaseId(), run.getId(), user));
      for (AgentEvidenceDraft draft : execution.result().evidence()) { saveEvidence(agentCase, run, call.getId(), draft); }
      call.setStatus(AgentEnums.ToolCallStatus.SUCCEEDED); call.setCompletedAt(Instant.now()); call.setDurationMs(Duration.between(now, call.getCompletedAt()).toMillis()); call.setResultSummary(abbreviate(execution.result().summary(), 1000)); toolCallRepository.save(call);
      run.setStatus(AgentEnums.RunStatus.RUNNING); run = runRepository.save(run); recordStep(run, AgentEnums.StepType.TOOL_CALL_SUCCEEDED, decision.toolName() + " 调用完成", Map.of("toolCallId", call.getId()));
      return AgentEnums.RunStatus.RUNNING;
    } catch (AgentToolExecutionException ex) {
      call.setStatus("TOOL_TIMEOUT".equals(ex.getCode()) ? AgentEnums.ToolCallStatus.TIMED_OUT : AgentEnums.ToolCallStatus.FAILED); call.setCompletedAt(Instant.now()); call.setDurationMs(Duration.between(now, call.getCompletedAt()).toMillis()); call.setErrorCode(ex.getCode()); call.setErrorMessage(ex.getMessage()); toolCallRepository.save(call);
      recordStep(run, AgentEnums.StepType.TOOL_CALL_FAILED, decision.toolName() + " 调用失败", Map.of("toolCallId", call.getId(), "errorCode", ex.getCode()));
      return fail(run, agentCase, ex.getCode(), ex.getMessage(), "TOOL_TIMEOUT".equals(ex.getCode()) ? AgentEnums.RunStatus.TIMED_OUT : AgentEnums.RunStatus.FAILED);
    }
  }

  private AgentPlanningContext context(AgentRunEntity run, AgentCaseEntity agentCase) {
    List<PlanningEvidence> evidence = evidenceRepository.findByRunIdOrderByCreatedAtAsc(run.getId()).stream().map(item -> new PlanningEvidence(item.getId(), item.getSourceType(), item.getSourceId(), item.getTitle(), item.getSummary(), map(item.getPayloadJson()))).toList();
    return new AgentPlanningContext(agentCase.getId(), run.getId(), run.getCreatedById(), run.getGoalSnapshot(), map(run.getInputSnapshotJson()), evidence, toolRegistry.descriptors(), run.getStartedAt());
  }

  private void saveFallbackEvidence(AgentCaseEntity agentCase, AgentRunEntity run, String reason) {
    if (!evidenceRepository.findByRunIdOrderByCreatedAtAsc(run.getId()).stream().anyMatch(item -> item.getSourceType() == AgentEnums.EvidenceSourceType.LLM_FALLBACK)) {
      saveEvidence(agentCase, run, null, new AgentEvidenceDraft(AgentEnums.EvidenceSourceType.LLM_FALLBACK, null, "Planner 降级", "结构化 LLM 规划不可用，已切换规则 Planner。", Map.of("reason", abbreviate(reason, 500))));
    }
  }

  private AgentEvidenceEntity saveEvidence(AgentCaseEntity agentCase, AgentRunEntity run, String toolCallId, AgentEvidenceDraft draft) {
    Instant now = Instant.now(); AgentEvidenceEntity item = new AgentEvidenceEntity();
    item.setId(Ids.next("agent_ev")); item.setCaseId(agentCase.getId()); item.setRunId(run.getId()); item.setToolCallId(toolCallId); item.setSourceType(draft.sourceType()); item.setSourceId(draft.sourceId()); item.setTitle(abbreviate(draft.title(), 255)); item.setSummary(abbreviate(draft.summary(), 1000)); item.setContentType("application/json"); item.setPayloadJson(json(draft.payload())); item.setContentHash(sha256(item.getPayloadJson())); item.setCollectedAt(now); item.setCreatedAt(now); item = evidenceRepository.save(item);
    recordStep(run, AgentEnums.StepType.EVIDENCE_ADDED, item.getTitle(), Map.of("evidenceId", item.getId(), "sourceType", item.getSourceType().name())); return item;
  }

  private AgentStepEntity recordStep(AgentRunEntity run, AgentEnums.StepType type, String summary, Map<String, Object> detail) {
    ReentrantLock lock = stepLocks.computeIfAbsent(run.getId(), key -> new ReentrantLock()); lock.lock();
    try {
      AgentStepEntity step = new AgentStepEntity(); step.setId(Ids.next("agent_step")); step.setCaseId(run.getCaseId()); step.setRunId(run.getId()); step.setSequenceNo(stepRepository.findMaxSequenceNo(run.getId()) + 1); step.setType(type); step.setSummary(abbreviate(summary, 500)); step.setDetailJson(json(detail)); step.setCreatedAt(Instant.now()); step = stepRepository.save(step);
      messagingTemplate.convertAndSend("/topic/agent-cases/" + run.getCaseId(), new AgentDtos.AgentEvent(step.getId(), run.getCaseId(), run.getId(), type, step.getSequenceNo(), step.getSummary(), step.getCreatedAt())); return step;
    } finally { lock.unlock(); }
  }

  private AgentEnums.RunStatus fail(AgentRunEntity run, AgentCaseEntity agentCase, String errorCode, String message, AgentEnums.RunStatus status) {
    run.setStatus(status); run.setErrorCode(errorCode); run.setErrorMessage(abbreviate(message, 1000)); run.setCompletedAt(Instant.now()); runRepository.save(run);
    if (agentCase != null) { agentCase.setStatus(AgentEnums.CaseStatus.FAILED); agentCase.setUpdatedAt(Instant.now()); caseRepository.save(agentCase); }
    recordStep(run, AgentEnums.StepType.RUN_FAILED, message, Map.of("errorCode", errorCode)); return status;
  }
  private boolean terminal(AgentEnums.RunStatus status) { return status == AgentEnums.RunStatus.SUCCEEDED || status == AgentEnums.RunStatus.FAILED || status == AgentEnums.RunStatus.CANCELLED || status == AgentEnums.RunStatus.TIMED_OUT || status == AgentEnums.RunStatus.STEP_LIMIT_REACHED; }
  private boolean timedOut(AgentRunEntity run) { return run.getStartedAt() != null && run.getStartedAt().plus(limits.getRunTimeout()).isBefore(Instant.now()); }
  private String code(Exception ex, String fallback) { return ex instanceof PlannerValidationException item ? item.getCode() : fallback; }
  private String reason(Exception ex) { return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }
  private String abbreviate(String value, int max) { if (value == null) return ""; return value.length() <= max ? value : value.substring(0, max); }
  private Map<String, Object> normalizedToolArguments(Map<String, Object> arguments) {
    Map<String, Object> normalized = new TreeMap<>();
    if (arguments != null) { arguments.forEach((key, value) -> { if (key != null && value != null) { normalized.put(key, value); } }); }
    return normalized;
  }
  private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new IllegalStateException("JSON serialization failed", ex); } }
  private Map<String, Object> map(String value) { try { return value == null || value.isBlank() ? Map.of() : objectMapper.readValue(value, MAP_TYPE); } catch (JsonProcessingException ex) { return Map.of(); } }
  private String sha256(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder output = new StringBuilder(); for (byte item : bytes) output.append(String.format("%02x", item)); return output.toString(); } catch (Exception ex) { throw new IllegalStateException(ex); } }
}
