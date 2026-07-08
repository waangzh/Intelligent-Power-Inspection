package com.powerinspection.agent;

import java.util.List;
import java.util.Map;

public interface AgentLlmGateway {
  AgentLlmAnalysis analyze(Map<String, Object> session, List<Map<String, Object>> evidence);
}
