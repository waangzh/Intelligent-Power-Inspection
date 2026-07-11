package com.powerinspection.agent.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.AgentToolService;
import com.powerinspection.agent.domain.AgentActionEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.common.ApiException;
import com.powerinspection.user.UserEntity;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AgentActionExecutor {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };
  private final AgentToolService toolService;
  private final ObjectMapper objectMapper;

  public AgentActionExecutor(AgentToolService toolService, ObjectMapper objectMapper) {
    this.toolService = toolService;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> execute(AgentActionEntity action, UserEntity user) {
    Map<String, Object> payload = payload(action.getPayloadJson());
    return switch (action.getType()) {
      case CREATE_WORK_ORDER_DRAFT -> toolService.createWorkOrderDraft(payload, user);
      case PUSH_NOTIFICATION -> toolService.pushNotification(payload);
      default -> throw ApiException.forbidden("该 Agent 动作不允许执行");
    };
  }

  private Map<String, Object> payload(String json) {
    try {
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception ex) {
      throw ApiException.badRequest("Agent 动作参数损坏");
    }
  }
}
