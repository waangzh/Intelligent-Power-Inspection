package com.powerinspection.agent;

import java.util.List;

public record AgentLlmAnalysis(
  String defectLevel,
  String cause,
  List<String> recommendedActions,
  List<String> evidenceIds,
  double confidence
) {
}
