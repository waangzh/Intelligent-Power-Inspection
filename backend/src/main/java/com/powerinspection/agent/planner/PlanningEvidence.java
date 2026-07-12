package com.powerinspection.agent.planner;

import com.powerinspection.agent.domain.AgentEnums;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record PlanningEvidence(
  String id,
  AgentEnums.EvidenceSourceType sourceType,
  String sourceId,
  String title,
  String summary,
  Map<String, Object> payload
) {
  public PlanningEvidence {
    payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
  }
}
