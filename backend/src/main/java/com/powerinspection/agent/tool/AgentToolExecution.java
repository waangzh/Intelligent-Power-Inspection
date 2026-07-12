package com.powerinspection.agent.tool;

import java.util.Map;

public record AgentToolExecution(
  AgentToolDescriptor descriptor,
  Map<String, Object> normalizedArguments,
  String argumentsJson,
  String argumentsHash,
  AgentToolResult<Object> result
) {
}
