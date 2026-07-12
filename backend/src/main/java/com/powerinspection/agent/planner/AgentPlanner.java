package com.powerinspection.agent.planner;

public interface AgentPlanner {
  PlannerDecision decide(AgentPlanningContext context);
}
