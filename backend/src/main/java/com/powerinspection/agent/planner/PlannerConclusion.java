package com.powerinspection.agent.planner;

import com.powerinspection.agent.domain.AgentEnums;
import java.util.List;

public record PlannerConclusion(
  AgentEnums.RiskLevel defectLevel,
  String cause,
  List<String> recommendedActions
) {
  public PlannerConclusion {
    recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
  }
}
