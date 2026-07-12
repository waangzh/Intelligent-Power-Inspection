package com.powerinspection.agent.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.domain.AgentActionEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.domain.AgentRunEntity;
import com.powerinspection.agent.persistence.AgentRunRepository;
import com.powerinspection.common.ApiException;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.user.UserEntity;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PushNotificationToSelfActionHandler implements AgentActionHandler {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
  private final AgentRunRepository runRepository;
  private final NotificationService notificationService;
  private final ObjectMapper objectMapper;
  public PushNotificationToSelfActionHandler(AgentRunRepository runRepository, NotificationService notificationService, ObjectMapper objectMapper) { this.runRepository = runRepository; this.notificationService = notificationService; this.objectMapper = objectMapper; }
  @Override public boolean supports(AgentEnums.ActionType type) { return type == AgentEnums.ActionType.PUSH_NOTIFICATION_TO_SELF || type == AgentEnums.ActionType.PUSH_NOTIFICATION; }
  @Override public Map<String, Object> execute(AgentActionEntity action, UserEntity user) {
    AgentRunEntity run = runRepository.findById(action.getRunId()).orElseThrow(() -> ApiException.notFound("Agent 运行不存在"));
    Map<String, Object> payload = payload(action.getPayloadJson());
    Map<String, Object> notification = notificationService.pushForAgentAction(run.getCreatedById(), "AGENT", string(payload, "title"), string(payload, "content"), optional(payload, "link"), action.getId());
    return Map.of("resourceType", "NOTIFICATION", "resourceId", String.valueOf(notification.get("id")), "summary", "已向运行创建者推送通知");
  }
  private Map<String, Object> payload(String json) { try { return objectMapper.readValue(json, MAP_TYPE); } catch (Exception ex) { throw ApiException.badRequest("通知动作参数损坏"); } }
  private String string(Map<String, Object> value, String key) { Object raw = value.get(key); if (raw == null || raw.toString().isBlank()) throw ApiException.badRequest("通知动作缺少 " + key); return raw.toString(); }
  private String optional(Map<String, Object> value, String key) { Object raw = value.get(key); return raw == null ? null : raw.toString(); }
}
