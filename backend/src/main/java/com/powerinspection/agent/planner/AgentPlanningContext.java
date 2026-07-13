package com.powerinspection.agent.planner;

import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.tool.AgentToolDescriptor;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/** Persisted Run data rehydrated into the bounded input visible to a planner. */
public record AgentPlanningContext(
  String caseId,
  String runId,
  String userId,
  String goal,
  Map<String, Object> input,
  List<PlanningEvidence> evidence,
  List<AgentToolDescriptor> availableTools,
  Instant startedAt
) {
  public AgentPlanningContext {
    input = input == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(input));
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
    availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
  }

  public boolean hasEvidence(AgentEnums.EvidenceSourceType type) {
    return evidence.stream().anyMatch(item -> item.sourceType() == type);
  }

  public PlanningEvidence firstEvidence(AgentEnums.EvidenceSourceType type) {
    return evidence.stream().filter(item -> item.sourceType() == type).findFirst().orElse(null);
  }

  public List<String> evidenceIds() {
    return evidence.stream().map(PlanningEvidence::id).toList();
  }
}
