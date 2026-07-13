package com.powerinspection.agent.planner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A non-executable action suggestion emitted by a planner. */
public record ActionProposal(
  String actionType,
  String title,
  String reason,
  Map<String, Object> payload,
  List<String> evidenceIds,
  double confidence
) {
  public ActionProposal {
    payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
    evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
  }
}
