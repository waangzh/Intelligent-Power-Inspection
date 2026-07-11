package com.powerinspection.agent.policy;

import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.user.UserEntity;
import org.springframework.stereotype.Service;

@Service
public class DefaultAgentPolicyService implements AgentPolicyService {
  @Override
  public AgentPolicyDecision evaluate(AgentEnums.ActionType type, UserEntity user) {
    return switch (type) {
      case CREATE_WORK_ORDER_DRAFT -> new AgentPolicyDecision(
        AgentEnums.PolicyDecisionType.REQUIRE_APPROVAL,
        AgentEnums.RiskLevel.LOW,
        "AGENT_WORK_ORDER_APPROVAL",
        "创建工单草稿必须由具备审批权限的用户确认。"
      );
      case PUSH_NOTIFICATION -> new AgentPolicyDecision(
        AgentEnums.PolicyDecisionType.REQUIRE_APPROVAL,
        AgentEnums.RiskLevel.LOW,
        "AGENT_NOTIFICATION_APPROVAL",
        "阶段 1 保持通知动作人工确认，以保留现有行为。"
      );
      default -> new AgentPolicyDecision(
        AgentEnums.PolicyDecisionType.DENY,
        AgentEnums.RiskLevel.CRITICAL,
        "AGENT_ACTION_DENIED",
        "该动作不在阶段 1 的受控执行白名单内。"
      );
    };
  }
}
