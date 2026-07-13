package com.powerinspection.agent;

import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.planner.AgentPlanningContext;
import com.powerinspection.agent.planner.PlannerConclusion;
import com.powerinspection.agent.planner.PlannerDecision;
import java.util.List;
import java.util.Map;

public interface AgentLlmGateway {
  AgentLlmAnalysis analyze(Map<String, Object> session, List<Map<String, Object>> evidence);

  default PlannerDecision decide(AgentPlanningContext context) {
    AgentLlmAnalysis analysis = analyze(
      Map.of("caseId", context.caseId(), "runId", context.runId(), "goal", context.goal()),
      context.evidence().stream().map(item -> Map.<String, Object>of("id", item.id(), "type", item.sourceType().name(), "summary", item.summary(), "payload", item.payload())).toList()
    );
    AgentEnums.RiskLevel risk = AgentEnums.RiskLevel.valueOf(analysis.defectLevel());
    return PlannerDecision.finish("LLM 已基于现有证据完成研判", new PlannerConclusion(risk, analysis.cause(), analysis.recommendedActions()), analysis.evidenceIds(), analysis.confidence());
  }
}
