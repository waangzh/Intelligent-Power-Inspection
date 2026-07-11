package com.powerinspection.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.policy.DefaultAgentPolicyService;
import org.junit.jupiter.api.Test;

class DefaultAgentPolicyServiceTests {
  private final DefaultAgentPolicyService policy = new DefaultAgentPolicyService();

  @Test
  void permitsOnlyPhaseOneWhitelistedActionsWithApproval() {
    assertThat(policy.evaluate(AgentEnums.ActionType.CREATE_WORK_ORDER_DRAFT, null).decision())
      .isEqualTo(AgentEnums.PolicyDecisionType.REQUIRE_APPROVAL);
    assertThat(policy.evaluate(AgentEnums.ActionType.ROBOT_MANUAL_CONTROL, null).decision())
      .isEqualTo(AgentEnums.PolicyDecisionType.DENY);
  }
}
