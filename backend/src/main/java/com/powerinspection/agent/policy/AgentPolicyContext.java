package com.powerinspection.agent.policy;

import com.powerinspection.agent.domain.AgentCaseEntity;
import com.powerinspection.agent.domain.AgentEvidenceEntity;
import com.powerinspection.agent.domain.AgentRunEntity;
import com.powerinspection.agent.planner.ActionProposal;
import com.powerinspection.user.UserEntity;
import java.util.List;

public record AgentPolicyContext(
  UserEntity user,
  AgentCaseEntity agentCase,
  AgentRunEntity run,
  ActionProposal proposal,
  List<AgentEvidenceEntity> evidence,
  boolean autoExecutionEnabled
) {
  public AgentPolicyContext {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }
}
