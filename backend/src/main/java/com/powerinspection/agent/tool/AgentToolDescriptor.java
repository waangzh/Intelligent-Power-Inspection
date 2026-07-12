package com.powerinspection.agent.tool;

import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.user.Permission;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public record AgentToolDescriptor(
  String name,
  String displayName,
  String description,
  Class<?> inputType,
  Class<?> outputType,
  Permission requiredPermission,
  boolean readOnly,
  AgentEnums.RiskLevel risk,
  Duration timeout,
  Set<String> inputFields
) {
  public AgentToolDescriptor {
    inputFields = inputFields == null ? Set.of() : Set.copyOf(inputFields);
  }

  public List<String> inputFieldList() {
    return inputFields.stream().sorted().toList();
  }
}
