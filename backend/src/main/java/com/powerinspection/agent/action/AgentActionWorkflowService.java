package com.powerinspection.agent.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.api.AgentDtos;
import com.powerinspection.agent.domain.AgentActionEntity;
import com.powerinspection.agent.domain.AgentCaseEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.domain.AgentRunEntity;
import com.powerinspection.agent.domain.AgentStepEntity;
import com.powerinspection.agent.persistence.AgentActionRepository;
import com.powerinspection.agent.persistence.AgentCaseRepository;
import com.powerinspection.agent.persistence.AgentRunRepository;
import com.powerinspection.agent.persistence.AgentStepRepository;
import com.powerinspection.agent.planner.ActionProposal;
import com.powerinspection.agent.policy.AgentPolicyDecision;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.config.JwtProperties;
import com.powerinspection.security.AuthenticatedUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import com.powerinspection.user.UserEntity;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AgentActionWorkflowService {
  private static final Logger log = LoggerFactory.getLogger(AgentActionWorkflowService.class);
  private final AgentActionRepository actionRepository;
  private final AgentCaseRepository caseRepository;
  private final AgentRunRepository runRepository;
  private final AgentStepRepository stepRepository;
  private final AgentActionProposalService proposalService;
  private final AgentActionExecutor executor;
  private final AgentExecutionClaimService claimService;
  private final PermissionService permissionService;
  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;
  private final JwtProperties jwtProperties;

  public AgentActionWorkflowService(
      AgentActionRepository actionRepository,
      AgentCaseRepository caseRepository,
      AgentRunRepository runRepository,
      AgentStepRepository stepRepository,
      AgentActionProposalService proposalService,
      AgentActionExecutor executor,
      AgentExecutionClaimService claimService,
      PermissionService permissionService,
      ObjectMapper objectMapper,
      SimpMessagingTemplate messagingTemplate,
      JwtProperties jwtProperties) {
    this.actionRepository = actionRepository;
    this.caseRepository = caseRepository;
    this.runRepository = runRepository;
    this.stepRepository = stepRepository;
    this.proposalService = proposalService;
    this.executor = executor;
    this.claimService = claimService;
    this.permissionService = permissionService;
    this.objectMapper = objectMapper;
    this.messagingTemplate = messagingTemplate;
    this.jwtProperties = jwtProperties;
  }

  @Transactional
  public AgentActionEntity approve(
      String actionId, AgentDtos.ActionDecisionRequest request, UserEntity user) {
    permissionService.require(user, Permission.AGENT_APPROVE);
    AgentActionEntity action = action(actionId);
    forbidSelfApproval(action, user);
    requireRecentAuth();
    requireVersionAndState(action, request.expectedVersion(), AgentEnums.ActionStatus.PROPOSED);
    AgentCaseEntity agentCase = agentCase(action);
    AgentRunEntity run = run(action);
    Map<String, Object> nextPayload =
        request.payload() == null || request.payload().isEmpty()
            ? proposalService.payload(action)
            : request.payload();
    ActionProposal proposal = proposalService.revalidatedProposal(action, agentCase, nextPayload);
    AgentPolicyDecision policy = proposalService.policy(action, agentCase, run, proposal, user);
    applyPayloadAndPolicy(action, proposal, policy, request.payload(), user);
    if (policy.decision() == AgentEnums.PolicyDecisionType.DENY) {
      action.setStatus(AgentEnums.ActionStatus.REJECTED);
      action.setRejectedById(user.getId());
      action.setRejectedAt(Instant.now());
      action.setRejectionComment(policy.reason());
      action = save(action);
      recordStep(
          run,
          AgentEnums.StepType.ACTION_REJECTED,
          "策略拒绝了审批动作",
          Map.of("actionId", action.getId(), "policyCode", policy.policyCode()));
      syncState(agentCase, run);
      return action;
    }
    action.setStatus(AgentEnums.ActionStatus.APPROVED);
    action.setApprovedById(user.getId());
    action.setApprovedAt(Instant.now());
    action.setApprovalComment(request.comment().trim());
    action.setUpdatedAt(Instant.now());
    action = save(action);
    recordStep(
        run,
        AgentEnums.StepType.ACTION_APPROVED,
        "动作已获人工批准",
        Map.of("actionId", action.getId(), "policyCode", policy.policyCode()));
    return execute(action, user);
  }

  @Transactional
  public AgentActionEntity reject(
      String actionId, AgentDtos.ActionDecisionRequest request, UserEntity user) {
    permissionService.require(user, Permission.AGENT_APPROVE);
    AgentActionEntity action = action(actionId);
    forbidSelfApproval(action, user);
    requireRecentAuth();
    requireVersionAndState(action, request.expectedVersion(), AgentEnums.ActionStatus.PROPOSED);
    action.setStatus(AgentEnums.ActionStatus.REJECTED);
    action.setRejectedById(user.getId());
    action.setRejectedAt(Instant.now());
    action.setRejectionComment(request.comment().trim());
    action.setUpdatedAt(Instant.now());
    action = save(action);
    AgentRunEntity run = run(action);
    recordStep(
        run, AgentEnums.StepType.ACTION_REJECTED, "动作已被人工拒绝", Map.of("actionId", action.getId()));
    syncState(agentCase(action), run);
    return action;
  }

  @Transactional
  public AgentActionEntity retry(
      String actionId, AgentDtos.ActionDecisionRequest request, UserEntity user) {
    permissionService.require(user, Permission.AGENT_APPROVE);
    AgentActionEntity action = action(actionId);
    forbidSelfApproval(action, user);
    requireRecentAuth();
    requireVersionAndState(action, request.expectedVersion(), AgentEnums.ActionStatus.FAILED);
    AgentCaseEntity agentCase = agentCase(action);
    AgentRunEntity run = run(action);
    ActionProposal proposal =
        proposalService.revalidatedProposal(action, agentCase, proposalService.payload(action));
    AgentPolicyDecision policy = proposalService.policy(action, agentCase, run, proposal, user);
    applyPayloadAndPolicy(action, proposal, policy, null, user);
    if (policy.decision() == AgentEnums.PolicyDecisionType.DENY) {
      action.setStatus(AgentEnums.ActionStatus.REJECTED);
      action.setRejectedAt(Instant.now());
      action.setRejectedById(user.getId());
      action.setRejectionComment(policy.reason());
      action = save(action);
      syncState(agentCase, run);
      return action;
    }
    action.setStatus(AgentEnums.ActionStatus.APPROVED);
    action.setApprovalComment("重试：" + request.comment().trim());
    action.setUpdatedAt(Instant.now());
    action = save(action);
    recordStep(
        run,
        AgentEnums.StepType.ACTION_APPROVED,
        "失败动作已重新获准执行",
        Map.of("actionId", action.getId()));
    return execute(action, user);
  }

  @Transactional
  public AgentActionEntity autoExecute(AgentActionEntity action, UserEntity user) {
    if (action.getStatus() != AgentEnums.ActionStatus.PROPOSED
        || action.getPolicyDecision() != AgentEnums.PolicyDecisionType.AUTO_EXECUTE) return action;
    action.setStatus(AgentEnums.ActionStatus.APPROVED);
    action.setApprovedById(user.getId());
    action.setApprovedAt(Instant.now());
    action.setApprovalComment("策略自动批准：" + action.getPolicyReason());
    action.setUpdatedAt(Instant.now());
    action = save(action);
    recordStep(
        run(action),
        AgentEnums.StepType.ACTION_APPROVED,
        "策略自动批准动作",
        Map.of("actionId", action.getId(), "policyCode", action.getPolicyCode()));
    return execute(action, user);
  }

  private AgentActionEntity execute(AgentActionEntity action, UserEntity user) {
    AgentCaseEntity agentCase = agentCase(action);
    AgentRunEntity run = run(action);
    ActionProposal proposal =
        proposalService.revalidatedProposal(action, agentCase, proposalService.payload(action));
    AgentPolicyDecision policy = proposalService.policy(action, agentCase, run, proposal, user);
    applyPayloadAndPolicy(action, proposal, policy, null, user);
    if (policy.decision() == AgentEnums.PolicyDecisionType.DENY
        || (action.getApprovedById() == null
            && policy.decision() == AgentEnums.PolicyDecisionType.REQUIRE_APPROVAL)) {
      action.setStatus(AgentEnums.ActionStatus.REJECTED);
      action.setRejectedAt(Instant.now());
      action.setRejectionComment(policy.reason());
      action = save(action);
      recordStep(
          run,
          AgentEnums.StepType.ACTION_REJECTED,
          "执行前策略拒绝动作",
          Map.of("actionId", action.getId(), "policyCode", policy.policyCode()));
      syncState(agentCase, run);
      return action;
    }
    AgentExecutionClaimService.ClaimResult claim =
        claimService.claim(action.getIdempotencyKey(), action.getId());
    if (!claim.owner()) {
      if (claim.claim().getStatus() == AgentEnums.ExecutionClaimStatus.SUCCEEDED) {
        action.setStatus(AgentEnums.ActionStatus.SUCCEEDED);
        action.setResultJson(claim.claim().getResultJson());
        action.setExecutionCompletedAt(Instant.now());
        action.setUpdatedAt(Instant.now());
        action = save(action);
        recordStep(
            run,
            AgentEnums.StepType.ACTION_SUCCEEDED,
            "动作复用了已有幂等结果",
            Map.of("actionId", action.getId()));
        syncState(agentCase, run);
        return action;
      }
      throw ApiException.conflict("相同动作正在执行，请刷新后查看结果");
    }
    action.setStatus(AgentEnums.ActionStatus.EXECUTING);
    action.setExecutionStartedAt(Instant.now());
    action.setUpdatedAt(Instant.now());
    action = save(action);
    agentCase.setStatus(AgentEnums.CaseStatus.ACTION_EXECUTING);
    agentCase.setUpdatedAt(Instant.now());
    caseRepository.save(agentCase);
    recordStep(
        run, AgentEnums.StepType.ACTION_STARTED, "动作正在执行", Map.of("actionId", action.getId()));
    try {
      String result = json(executor.execute(action, user));
      action.setStatus(AgentEnums.ActionStatus.SUCCEEDED);
      action.setResultJson(result);
      action.setExecutionCompletedAt(Instant.now());
      action.setUpdatedAt(Instant.now());
      action.setErrorCode(null);
      action.setErrorMessage(null);
      action = save(action);
      claimService.complete(claim.claim(), AgentEnums.ExecutionClaimStatus.SUCCEEDED, result);
      recordStep(
          run, AgentEnums.StepType.ACTION_SUCCEEDED, "动作执行成功", Map.of("actionId", action.getId()));
    } catch (Exception ex) {
      action.setStatus(AgentEnums.ActionStatus.FAILED);
      action.setErrorCode("ACTION_EXECUTION_FAILED");
      action.setErrorMessage(abbreviate(ex.getMessage(), 1000));
      action.setExecutionCompletedAt(Instant.now());
      action.setUpdatedAt(Instant.now());
      action = save(action);
      claimService.complete(claim.claim(), AgentEnums.ExecutionClaimStatus.FAILED, null);
      recordStep(
          run, AgentEnums.StepType.ACTION_FAILED, "动作执行失败", Map.of("actionId", action.getId()));
    }
    syncState(agentCase, run);
    return action;
  }

  private void applyPayloadAndPolicy(
      AgentActionEntity action,
      ActionProposal proposal,
      AgentPolicyDecision policy,
      Map<String, Object> requestedPayload,
      UserEntity user) {
    if (requestedPayload != null
        && !requestedPayload.isEmpty()
        && !json(proposal.payload()).equals(action.getPayloadJson())) {
      action.setPayloadAuditJson(
          json(
              Map.of(
                  "before",
                  proposalService.payload(action),
                  "after",
                  proposal.payload(),
                  "changedBy",
                  user.getId(),
                  "changedAt",
                  Instant.now().toString())));
      action.setPayloadJson(json(proposal.payload()));
      action.setIdempotencyKey(
          proposalService.idempotencyKey(
              action.getType(), businessId(proposal, action), proposal.payload()));
    }
    action.setConfidence(proposal.confidence());
    action.setPolicyDecision(policy.decision());
    action.setPolicyCode(policy.policyCode());
    action.setPolicyReason(policy.reason());
    action.setRiskLevel(policy.riskLevel());
    action.setRequiresApproval(policy.decision() == AgentEnums.PolicyDecisionType.REQUIRE_APPROVAL);
    action.setUpdatedAt(Instant.now());
  }

  private void syncState(AgentCaseEntity agentCase, AgentRunEntity run) {
    List<AgentActionEntity> runActions =
        actionRepository.findByRunIdOrderByCreatedAtAsc(run.getId());
    boolean executing =
        runActions.stream().anyMatch(item -> item.getStatus() == AgentEnums.ActionStatus.EXECUTING);
    boolean pending =
        runActions.stream()
            .anyMatch(
                item ->
                    item.getStatus() == AgentEnums.ActionStatus.PROPOSED
                        && item.isRequiresApproval());
    boolean failed =
        runActions.stream().anyMatch(item -> item.getStatus() == AgentEnums.ActionStatus.FAILED);
    if (failed) {
      run.setStatus(AgentEnums.RunStatus.FAILED);
      run.setErrorCode("ACTION_FAILED");
      run.setErrorMessage("受控动作执行失败");
      run.setCompletedAt(Instant.now());
      agentCase.setStatus(AgentEnums.CaseStatus.FAILED);
    } else if (executing) {
      run.setStatus(AgentEnums.RunStatus.ACTION_EXECUTING);
      agentCase.setStatus(AgentEnums.CaseStatus.ACTION_EXECUTING);
    } else if (pending) {
      run.setStatus(AgentEnums.RunStatus.WAITING_APPROVAL);
      agentCase.setStatus(AgentEnums.CaseStatus.WAITING_APPROVAL);
    } else if (run.getStatus() == AgentEnums.RunStatus.WAITING_APPROVAL) {
      run.setStatus(AgentEnums.RunStatus.SUCCEEDED);
      run.setCompletedAt(Instant.now());
      agentCase.setStatus(AgentEnums.CaseStatus.RESOLVED);
      agentCase.setResolvedAt(Instant.now());
    }
    agentCase.setUpdatedAt(Instant.now());
    runRepository.save(run);
    caseRepository.save(agentCase);
  }

  private void forbidSelfApproval(AgentActionEntity action, UserEntity user) {
    String requestedById = action.getRequestedById();
    if (requestedById == null || requestedById.isBlank()) {
      requestedById = run(action).getCreatedById();
    }
    if (requestedById != null && requestedById.equals(user.getId())) {
      throw ApiException.forbidden("发起人不能审批自己的动作");
    }
  }

  private void requireRecentAuth() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
      // Service-layer / test calls without JWT context skip the window check;
      // HTTP requests always carry AuthenticatedUser after JwtAuthenticationFilter.
      return;
    }
    long authTime = principal.authTimeEpochSeconds();
    if (authTime <= 0
        || Instant.now().getEpochSecond() - authTime > jwtProperties.getReauthWindowSeconds()) {
      throw ApiException.forbidden("高风险操作需要近期重新认证，请先验证密码");
    }
  }

  private void requireVersionAndState(
      AgentActionEntity action, long expectedVersion, AgentEnums.ActionStatus expected) {
    if (action.getVersion() != expectedVersion || action.getStatus() != expected)
      throw ApiException.conflict("动作已被其他请求处理，请刷新后重试");
  }

  private AgentActionEntity action(String id) {
    return actionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Agent 动作不存在"));
  }

  private AgentCaseEntity agentCase(AgentActionEntity action) {
    return caseRepository
        .findById(action.getCaseId())
        .orElseThrow(() -> ApiException.notFound("处置案件不存在"));
  }

  private AgentRunEntity run(AgentActionEntity action) {
    return runRepository
        .findById(action.getRunId())
        .orElseThrow(() -> ApiException.notFound("分析运行不存在"));
  }

  private AgentActionEntity save(AgentActionEntity action) {
    try {
      return actionRepository.saveAndFlush(action);
    } catch (OptimisticLockingFailureException ex) {
      throw ApiException.conflict("动作已被其他请求处理，请刷新后重试");
    }
  }

  private String businessId(ActionProposal proposal, AgentActionEntity action) {
    Object alarmId = proposal.payload().get("alarmId");
    if (alarmId != null) return alarmId.toString();
    Object taskId = proposal.payload().get("taskId");
    return taskId == null ? action.getCaseId() : taskId.toString();
  }

  private void recordStep(
      AgentRunEntity run, AgentEnums.StepType type, String summary, Map<String, Object> detail) {
    AgentStepEntity step = new AgentStepEntity();
    step.setId(Ids.next("agent_step"));
    step.setCaseId(run.getCaseId());
    step.setRunId(run.getId());
    step.setSequenceNo(stepRepository.findMaxSequenceNo(run.getId()) + 1);
    step.setType(type);
    step.setSummary(summary);
    step.setDetailJson(json(detail));
    step.setCreatedAt(Instant.now());
    step = stepRepository.save(step);
    try {
      messagingTemplate.convertAndSend(
          "/topic/agent-cases/" + run.getCaseId(),
          new AgentDtos.AgentEvent(
              step.getId(),
              run.getCaseId(),
              run.getId(),
              type,
              step.getSequenceNo(),
              step.getSummary(),
              step.getCreatedAt()));
    } catch (Exception ex) {
      log.warn("Failed to send agent WebSocket event for case={}", run.getCaseId(), ex);
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("动作审计序列化失败", ex);
    }
  }

  private String abbreviate(String value, int max) {
    if (value == null) return "动作执行失败";
    return value.length() <= max ? value : value.substring(0, max);
  }
}
