package com.powerinspection.agent.policy;

import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.planner.ActionProposal;
import com.powerinspection.user.UserEntity;

public interface AgentPolicyService {
  AgentPolicyDecision evaluate(AgentPolicyContext context);

  default AgentPolicyDecision evaluate(AgentEnums.ActionType type, UserEntity user) {
    return evaluate(new AgentPolicyContext(user, null, null, new ActionProposal(type.name(), "兼容动作", "兼容策略评估", java.util.Map.of(), java.util.List.of(), 1), java.util.List.of(), true));
  }
}
