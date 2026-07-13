package com.powerinspection.agent.tool;

import com.powerinspection.agent.domain.AgentEnums;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

public record AgentEvidenceDraft(
  AgentEnums.EvidenceSourceType sourceType,
  String sourceId,
  String title,
  String summary,
  Map<String, Object> payload
) {
  public AgentEvidenceDraft {
    payload = payload == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
  }
}
