package com.powerinspection.agent.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.AgentOrchestratorProperties;
import com.powerinspection.agent.domain.AgentActionEntity;
import com.powerinspection.agent.domain.AgentCaseEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.domain.AgentEvidenceEntity;
import com.powerinspection.agent.domain.AgentRunEntity;
import com.powerinspection.agent.persistence.AgentActionRepository;
import com.powerinspection.agent.persistence.AgentEvidenceRepository;
import com.powerinspection.agent.planner.ActionProposal;
import com.powerinspection.agent.policy.AgentPolicyContext;
import com.powerinspection.agent.policy.AgentPolicyDecision;
import com.powerinspection.agent.policy.AgentPolicyService;
import com.powerinspection.common.Ids;
import com.powerinspection.user.UserEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class AgentActionProposalService {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private final AgentActionRepository actionRepository;
  private final AgentEvidenceRepository evidenceRepository;
  private final AgentPolicyService policyService;
  private final AgentActionPayloadValidator payloadValidator;
  private final AgentOrchestratorProperties orchestratorProperties;
  private final ObjectMapper objectMapper;

  public AgentActionProposalService(
      AgentActionRepository actionRepository,
      AgentEvidenceRepository evidenceRepository,
      AgentPolicyService policyService,
      AgentActionPayloadValidator payloadValidator,
      AgentOrchestratorProperties orchestratorProperties,
      ObjectMapper objectMapper) {
    this.actionRepository = actionRepository;
    this.evidenceRepository = evidenceRepository;
    this.policyService = policyService;
    this.payloadValidator = payloadValidator;
    this.orchestratorProperties = orchestratorProperties;
    this.objectMapper = objectMapper;
  }

  public ProposalResult propose(
      AgentCaseEntity agentCase, AgentRunEntity run, ActionProposal raw, UserEntity user) {
    List<AgentEvidenceEntity> evidence =
        evidenceRepository.findByRunIdOrderByCreatedAtAsc(run.getId());
    ActionProposal proposal = payloadValidator.validate(raw, agentCase, evidence);
    AgentEnums.ActionType type = payloadValidator.actionType(proposal.actionType());
    String key = idempotencyKey(type, businessId(proposal, agentCase), proposal.payload());
    AgentActionEntity existing =
        actionRepository.findByIdempotencyKeyOrderByCreatedAtAsc(key).stream()
            .findFirst()
            .orElse(null);
    if (existing != null) return new ProposalResult(existing, false);
    AgentPolicyDecision policy =
        policyService.evaluate(
            new AgentPolicyContext(
                user,
                agentCase,
                run,
                proposal,
                evidence,
                orchestratorProperties.isAutoExecutionEnabled()));
    Instant now = Instant.now();
    AgentActionEntity action = new AgentActionEntity();
    action.setId(Ids.next("agent_act"));
    action.setCaseId(agentCase.getId());
    action.setRunId(run.getId());
    action.setType(type);
    action.setTitle(proposal.title());
    action.setReason(proposal.reason());
    action.setRiskLevel(policy.riskLevel());
    action.setStatus(
        policy.decision() == AgentEnums.PolicyDecisionType.DENY
            ? AgentEnums.ActionStatus.REJECTED
            : AgentEnums.ActionStatus.PROPOSED);
    action.setPayloadJson(json(proposal.payload()));
    action.setConfidence(proposal.confidence());
    action.setEvidenceIdsJson(json(proposal.evidenceIds()));
    action.setPolicyDecision(policy.decision());
    action.setPolicyCode(policy.policyCode());
    action.setPolicyReason(policy.reason());
    action.setRequiresApproval(policy.decision() == AgentEnums.PolicyDecisionType.REQUIRE_APPROVAL);
    action.setIdempotencyKey(key);
    action.setRequestedById(user == null ? run.getCreatedById() : user.getId());
    if (policy.decision() == AgentEnums.PolicyDecisionType.DENY) {
      action.setRejectedAt(now);
      action.setRejectionComment(policy.reason());
    }
    action.setCreatedAt(now);
    action.setUpdatedAt(now);
    return new ProposalResult(actionRepository.save(action), true);
  }

  public ActionProposal revalidatedProposal(
      AgentActionEntity action, AgentCaseEntity agentCase, Map<String, Object> payload) {
    List<AgentEvidenceEntity> evidence =
        evidenceRepository.findByRunIdOrderByCreatedAtAsc(action.getRunId());
    ActionProposal raw =
        new ActionProposal(
            action.getType().name(),
            action.getTitle(),
            action.getReason(),
            payload,
            evidenceIds(action),
            action.getConfidence());
    return payloadValidator.validate(raw, agentCase, evidence);
  }

  public AgentPolicyDecision policy(
      AgentActionEntity action,
      AgentCaseEntity agentCase,
      AgentRunEntity run,
      ActionProposal proposal,
      UserEntity user) {
    return policyService.evaluate(
        new AgentPolicyContext(
            user,
            agentCase,
            run,
            proposal,
            evidenceRepository.findByRunIdOrderByCreatedAtAsc(run.getId()),
            orchestratorProperties.isAutoExecutionEnabled()));
  }

  public String idempotencyKey(
      AgentEnums.ActionType type, String businessId, Map<String, Object> payload) {
    return type.name() + ":" + businessId + ":" + sha256(json(new TreeMap<>(payload)));
  }

  public List<String> evidenceIds(AgentActionEntity action) {
    try {
      return objectMapper.readValue(action.getEvidenceIdsJson(), STRING_LIST);
    } catch (Exception ex) {
      return List.of();
    }
  }

  public Map<String, Object> payload(AgentActionEntity action) {
    try {
      return objectMapper.readValue(action.getPayloadJson(), MAP_TYPE);
    } catch (Exception ex) {
      return Map.of();
    }
  }

  private String businessId(ActionProposal proposal, AgentCaseEntity agentCase) {
    Object alarmId = proposal.payload().get("alarmId");
    if (alarmId != null && !alarmId.toString().isBlank()) return alarmId.toString();
    Object taskId = proposal.payload().get("taskId");
    if (taskId != null && !taskId.toString().isBlank()) return taskId.toString();
    return agentCase.getId();
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("动作审计序列化失败", ex);
    }
  }

  private String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder output = new StringBuilder();
      for (byte item : digest) output.append(String.format("%02x", item));
      return output.toString();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  public record ProposalResult(AgentActionEntity action, boolean created) {}
}
