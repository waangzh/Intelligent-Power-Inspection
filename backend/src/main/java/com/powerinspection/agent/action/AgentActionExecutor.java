package com.powerinspection.agent.action;

import com.powerinspection.agent.domain.AgentActionEntity;
import com.powerinspection.common.ApiException;
import com.powerinspection.user.UserEntity;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AgentActionExecutor {
  private final List<AgentActionHandler> handlers;

  public AgentActionExecutor(List<AgentActionHandler> handlers) { this.handlers = List.copyOf(handlers); }

  public Map<String, Object> execute(AgentActionEntity action, UserEntity user) {
    return handlers.stream().filter(item -> item.supports(action.getType())).findFirst().orElseThrow(() -> ApiException.forbidden("该 Agent 动作没有受控执行器")).execute(action, user);
  }
}
