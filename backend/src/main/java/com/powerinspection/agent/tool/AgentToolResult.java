package com.powerinspection.agent.tool;

import java.util.List;

public record AgentToolResult<O>(O output, String summary, List<AgentEvidenceDraft> evidence) {
  public AgentToolResult {
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }
}
