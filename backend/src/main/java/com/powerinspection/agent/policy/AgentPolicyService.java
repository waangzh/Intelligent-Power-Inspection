package com.powerinspection.agent.policy;

import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.user.UserEntity;

public interface AgentPolicyService {
  AgentPolicyDecision evaluate(AgentEnums.ActionType type, UserEntity user);
}
