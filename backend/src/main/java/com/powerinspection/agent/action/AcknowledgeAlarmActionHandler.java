package com.powerinspection.agent.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.domain.AgentActionEntity;
import com.powerinspection.agent.domain.AgentCaseEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.persistence.AgentCaseRepository;
import com.powerinspection.alarm.AlarmService;
import com.powerinspection.common.ApiException;
import com.powerinspection.user.UserEntity;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AcknowledgeAlarmActionHandler implements AgentActionHandler {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private final AgentCaseRepository caseRepository;
  private final AlarmService alarmService;
  private final ObjectMapper objectMapper;

  public AcknowledgeAlarmActionHandler(AgentCaseRepository caseRepository, AlarmService alarmService, ObjectMapper objectMapper) {
    this.caseRepository = caseRepository;
    this.alarmService = alarmService;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(AgentEnums.ActionType type) {
    return type == AgentEnums.ActionType.ACKNOWLEDGE_ALARM;
  }

  @Override
  public Map<String, Object> execute(AgentActionEntity action, UserEntity user) {
    AgentCaseEntity agentCase = caseRepository.findById(action.getCaseId())
      .orElseThrow(() -> ApiException.notFound("处置案件不存在"));
    String alarmId = text(payload(action.getPayloadJson()).get("alarmId"));
    if (alarmId == null || !alarmId.equals(agentCase.getAlarmId())) {
      throw ApiException.badRequest("告警确认对象不属于当前案件");
    }
    alarmService.acknowledge(alarmId);
    return Map.of("resourceType", "ALARM", "resourceId", alarmId, "summary", "告警已确认");
  }

  private Map<String, Object> payload(String json) {
    try {
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception ex) {
      throw ApiException.badRequest("告警确认动作参数损坏");
    }
  }

  private String text(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString();
  }
}
