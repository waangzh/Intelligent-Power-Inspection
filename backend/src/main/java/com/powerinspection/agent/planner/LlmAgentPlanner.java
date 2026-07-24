package com.powerinspection.agent.planner;

import com.powerinspection.agent.AgentLlmGateway;
import org.springframework.stereotype.Component;

/** Lets the model choose each bounded planning step from the current observations. */
@Component
public class LlmAgentPlanner implements AgentPlanner {
  private final AgentLlmGateway gateway;

  public LlmAgentPlanner(AgentLlmGateway gateway) {
    this.gateway = gateway;
  }

  @Override
  public PlannerDecision decide(AgentPlanningContext context) {
    return gateway.decide(context);
  }
}
