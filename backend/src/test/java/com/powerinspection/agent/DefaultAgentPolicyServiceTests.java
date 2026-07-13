package com.powerinspection.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.policy.DefaultAgentPolicyService;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.user.PermissionService;
import org.junit.jupiter.api.Test;

class DefaultAgentPolicyServiceTests {
  private final DefaultAgentPolicyService policy = new DefaultAgentPolicyService(new PermissionService(), null);

  @Test
  void deniesUnknownActionsAndRequiresApprovalForWhitelistedActionsWithNullUser() {
    assertThat(policy.evaluate(AgentEnums.ActionType.CREATE_WORK_ORDER_DRAFT, null).decision())
      .isEqualTo(AgentEnums.PolicyDecisionType.REQUIRE_APPROVAL);
    assertThat(policy.evaluate(AgentEnums.ActionType.PUSH_NOTIFICATION, null).decision())
      .isEqualTo(AgentEnums.PolicyDecisionType.REQUIRE_APPROVAL);
    assertThat(policy.evaluate(AgentEnums.ActionType.ROBOT_MANUAL_CONTROL, null).decision())
      .isEqualTo(AgentEnums.PolicyDecisionType.DENY);
  }
}
