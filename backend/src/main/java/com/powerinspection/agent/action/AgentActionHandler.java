package com.powerinspection.agent.action;

import com.powerinspection.agent.domain.AgentActionEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.user.UserEntity;
import java.util.Map;

public interface AgentActionHandler {
  boolean supports(AgentEnums.ActionType type);
  Map<String, Object> execute(AgentActionEntity action, UserEntity user);
}
