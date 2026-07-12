package com.powerinspection.agent.planner;

import com.powerinspection.agent.AgentLlmGateway;
import org.springframework.stereotype.Component;

/** Lets deterministic evidence collection complete before asking the model for a final bounded decision. */
@Component
public class LlmAgentPlanner implements AgentPlanner {
  private final RuleBasedAgentPlanner ruleBasedPlanner;
  private final AgentLlmGateway gateway;

  public LlmAgentPlanner(RuleBasedAgentPlanner ruleBasedPlanner, AgentLlmGateway gateway) {
    this.ruleBasedPlanner = ruleBasedPlanner;
    this.gateway = gateway;
  }

  @Override
  public PlannerDecision decide(AgentPlanningContext context) {
    PlannerDecision minimum = ruleBasedPlanner.decide(context);
    return minimum.type() == PlannerDecisionType.FINISH ? gateway.decide(context) : minimum;
  }
}
