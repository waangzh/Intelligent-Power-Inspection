package com.powerinspection.agent.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.AgentToolService;
import com.powerinspection.agent.domain.AgentActionEntity;
import com.powerinspection.agent.domain.AgentCaseEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.persistence.AgentCaseRepository;
import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.user.UserEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CreateWorkOrderDraftActionHandler implements AgentActionHandler {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
  private final AgentCaseRepository caseRepository;
  private final AgentToolService toolService;
  private final DataStoreService dataStore;
  private final ObjectMapper objectMapper;
  public CreateWorkOrderDraftActionHandler(AgentCaseRepository caseRepository, AgentToolService toolService, DataStoreService dataStore, ObjectMapper objectMapper) { this.caseRepository = caseRepository; this.toolService = toolService; this.dataStore = dataStore; this.objectMapper = objectMapper; }
  @Override public boolean supports(AgentEnums.ActionType type) { return type == AgentEnums.ActionType.CREATE_WORK_ORDER_DRAFT; }
  @Override public Map<String, Object> execute(AgentActionEntity action, UserEntity user) {
    AgentCaseEntity agentCase = caseRepository.findById(action.getCaseId()).orElseThrow(() -> ApiException.notFound("处置案件不存在"));
    Map<String, Object> payload = new LinkedHashMap<>(payload(action.getPayloadJson()));
    String alarmId = text(payload.get("alarmId")); String taskId = text(payload.get("taskId"));
    if ((alarmId == null && taskId == null) || (alarmId != null && !alarmId.equals(agentCase.getAlarmId())) || (taskId != null && !taskId.equals(agentCase.getTaskId()))) throw ApiException.badRequest("工单动作对象不属于当前案件");
    Map<String, Object> existing = dataStore.list(DataCategory.WORK_ORDER).stream().filter(order -> action.getId().equals(text(order.get("agentActionId"))) || action.getIdempotencyKey().equals(text(order.get("agentIdempotencyKey")))).findFirst().orElse(null);
    if (existing != null) return result(existing, "已复用此前创建的工单草稿");
    boolean related = dataStore.list(DataCategory.WORK_ORDER).stream().anyMatch(order -> (alarmId != null && alarmId.equals(text(order.get("alarmId")))) || (taskId != null && taskId.equals(text(order.get("taskId")))));
    if (related) throw ApiException.conflict("关联对象已存在工单");
    payload.put("agentActionId", action.getId()); payload.put("agentIdempotencyKey", action.getIdempotencyKey());
    Map<String, Object> created = toolService.createWorkOrderDraft(payload, user);
    return result(created, "已创建工单草稿");
  }
  private Map<String, Object> result(Map<String, Object> order, String summary) { return Map.of("resourceType", "WORK_ORDER", "resourceId", String.valueOf(order.get("id")), "summary", summary); }
  private Map<String, Object> payload(String json) { try { return objectMapper.readValue(json, MAP_TYPE); } catch (Exception ex) { throw ApiException.badRequest("工单动作参数损坏"); } }
  private String text(Object value) { return value == null || value.toString().isBlank() ? null : value.toString(); }
}
