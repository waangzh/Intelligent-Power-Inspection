package com.powerinspection.agent.policy;

import com.powerinspection.agent.domain.AgentEnums;

public record AgentPolicyDecision(
  AgentEnums.PolicyDecisionType decision,
  AgentEnums.RiskLevel riskLevel,
  String policyCode,
  String reason
) {
}
