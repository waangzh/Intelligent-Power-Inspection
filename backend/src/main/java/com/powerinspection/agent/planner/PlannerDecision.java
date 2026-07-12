package com.powerinspection.agent.planner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A deliberately small, non-executable planning response. */
public record PlannerDecision(
  PlannerDecisionType type,
  String summary,
  String toolName,
  Map<String, Object> toolArguments,
  List<String> evidenceIds,
  PlannerQuestion question,
  PlannerConclusion conclusion,
  ActionProposal actionProposal,
  double confidence
) {
  public PlannerDecision {
    toolArguments = toolArguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(toolArguments));
    evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
  }

  public static PlannerDecision callTool(String summary, String toolName, Map<String, Object> arguments, List<String> evidenceIds) {
    return new PlannerDecision(PlannerDecisionType.CALL_TOOL, summary, toolName, arguments, evidenceIds, null, null, null, 0.8);
  }

  public static PlannerDecision askHuman(String summary, PlannerQuestion question, List<String> evidenceIds) {
    return new PlannerDecision(PlannerDecisionType.ASK_HUMAN, summary, null, Map.of(), evidenceIds, question, null, null, 0.5);
  }

  public static PlannerDecision finish(String summary, PlannerConclusion conclusion, List<String> evidenceIds, double confidence) {
    return new PlannerDecision(PlannerDecisionType.FINISH, summary, null, Map.of(), evidenceIds, null, conclusion, null, confidence);
  }

  public static PlannerDecision finishWithProposal(String summary, PlannerConclusion conclusion, ActionProposal proposal, List<String> evidenceIds, double confidence) {
    return new PlannerDecision(PlannerDecisionType.FINISH, summary, null, Map.of(), evidenceIds, null, conclusion, proposal, confidence);
  }

  public static PlannerDecision proposeAction(String summary, ActionProposal proposal) {
    return new PlannerDecision(PlannerDecisionType.PROPOSE_ACTION, summary, null, Map.of(), proposal.evidenceIds(), null, null, proposal, proposal.confidence());
  }
}
