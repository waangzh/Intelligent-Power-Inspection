package com.powerinspection.agent.action;

import com.powerinspection.agent.domain.AgentCaseEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.domain.AgentEvidenceEntity;
import com.powerinspection.agent.planner.ActionProposal;
import com.powerinspection.agent.planner.PlannerValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Keeps planner and approval payloads inside a deliberately small, typed surface. */
@Component
public class AgentActionPayloadValidator {
  private static final Set<String> FORBIDDEN_TYPES = Set.of(
    "ROBOT_MANUAL_CONTROL", "CANCEL_TASK", "MODIFY_USER_PERMISSION", "MODIFY_MODEL_CONFIGURATION",
    "EXECUTE_ARBITRARY_HTTP_REQUEST", "EXECUTE_COMMAND", "READ_LOCAL_FILE"
  );

  public AgentEnums.ActionType actionType(String value) {
    try {
      return AgentEnums.ActionType.valueOf(value);
    } catch (Exception ex) {
      throw invalid("UNKNOWN_ACTION_TYPE", "未允许的 Agent 动作类型");
    }
  }

  public ActionProposal validate(ActionProposal proposal, AgentCaseEntity agentCase, List<AgentEvidenceEntity> evidence) {
    if (proposal == null) throw invalid("INVALID_ACTION_PROPOSAL", "缺少动作提案");
    AgentEnums.ActionType type = actionType(proposal.actionType());
    if (blank(proposal.title()) || proposal.title().length() > 255 || blank(proposal.reason()) || proposal.reason().length() > 500) {
      throw invalid("INVALID_ACTION_PROPOSAL", "动作标题或业务理由不合法");
    }
    if (!Double.isFinite(proposal.confidence()) || proposal.confidence() < 0 || proposal.confidence() > 1) {
      throw invalid("INVALID_ACTION_PROPOSAL", "动作置信度必须在 0 到 1 之间");
    }
    Set<String> evidenceIds = evidence.stream().map(AgentEvidenceEntity::getId).collect(java.util.stream.Collectors.toSet());
    if (proposal.evidenceIds().isEmpty() || !evidenceIds.containsAll(proposal.evidenceIds())) {
      throw invalid("EVIDENCE_NOT_IN_RUN", "动作只能引用当前 Run 的证据");
    }
    return new ActionProposal(type.name(), proposal.title().trim(), proposal.reason().trim(), validatePayload(type, proposal.payload(), agentCase), proposal.evidenceIds(), proposal.confidence());
  }

  public Map<String, Object> validatePayload(AgentEnums.ActionType type, Map<String, Object> payload, AgentCaseEntity agentCase) {
    Map<String, Object> value = strings(payload);
    if (FORBIDDEN_TYPES.contains(type.name())) {
      if (!value.isEmpty()) throw invalid("INVALID_ACTION_PAYLOAD", "禁止动作不能携带执行参数");
      return value;
    }
    return switch (type) {
      case PUSH_NOTIFICATION_TO_SELF -> notification(value);
      case CREATE_WORK_ORDER_DRAFT -> workOrder(value, agentCase);
      case ACKNOWLEDGE_ALARM -> identifier(value, "alarmId", agentCase.getAlarmId());
      case REQUEST_TASK_PAUSE -> identifier(value, "taskId", agentCase.getTaskId());
      case PUSH_NOTIFICATION -> throw invalid("LEGACY_ACTION_TYPE", "旧通知动作不能由 Planner 新建");
      default -> throw invalid("INVALID_ACTION_PAYLOAD", "动作参数不受支持");
    };
  }

  private Map<String, Object> notification(Map<String, Object> value) {
    requireKeys(value, Set.of("title", "content", "link"));
    String title = required(value, "title", 120);
    String content = required(value, "content", 500);
    String link = optional(value, "link", 160);
    if (link != null && (!link.startsWith("/") || link.contains("://") || link.contains("\\") || link.contains(".."))) {
      throw invalid("INVALID_ACTION_PAYLOAD", "通知链接只能是站内相对路径");
    }
    return map("title", title, "content", content, "link", link);
  }

  private Map<String, Object> workOrder(Map<String, Object> value, AgentCaseEntity agentCase) {
    requireKeys(value, Set.of("alarmId", "taskId", "title", "description", "priority"));
    String alarmId = optional(value, "alarmId", 64);
    String taskId = optional(value, "taskId", 64);
    if (blank(alarmId) && blank(taskId)) throw invalid("INVALID_ACTION_PAYLOAD", "工单草稿必须关联告警或任务");
    if (!blank(alarmId) && !alarmId.equals(agentCase.getAlarmId())) throw invalid("INVALID_ACTION_PAYLOAD", "工单告警必须属于当前案件");
    if (!blank(taskId) && !taskId.equals(agentCase.getTaskId())) throw invalid("INVALID_ACTION_PAYLOAD", "工单任务必须属于当前案件");
    String priority = required(value, "priority", 16);
    if (!Set.of("LOW", "MEDIUM", "HIGH", "URGENT").contains(priority)) throw invalid("INVALID_ACTION_PAYLOAD", "工单优先级不合法");
    return map("alarmId", alarmId, "taskId", taskId, "title", required(value, "title", 255), "description", required(value, "description", 1000), "priority", priority);
  }

  private Map<String, Object> identifier(Map<String, Object> value, String key, String expected) {
    requireKeys(value, Set.of(key));
    String id = required(value, key, 64);
    if (blank(expected) || !expected.equals(id)) throw invalid("INVALID_ACTION_PAYLOAD", "动作对象不属于当前案件");
    return Map.of(key, id);
  }

  private Map<String, Object> strings(Map<String, Object> payload) {
    Map<String, Object> input = payload == null ? Map.of() : payload;
    Map<String, Object> result = new TreeMap<>();
    for (Map.Entry<String, Object> entry : input.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null || !(entry.getValue() instanceof String)) throw invalid("INVALID_ACTION_PAYLOAD", "动作参数只能是字符串");
      String text = ((String) entry.getValue()).trim();
      if (text.indexOf('\u0000') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) throw invalid("INVALID_ACTION_PAYLOAD", "动作参数包含非法控制字符");
      result.put(entry.getKey(), text);
    }
    return new LinkedHashMap<>(result);
  }

  private void requireKeys(Map<String, Object> value, Set<String> allowed) {
    if (!allowed.containsAll(value.keySet())) throw invalid("INVALID_ACTION_PAYLOAD", "动作参数包含未允许字段");
  }
  private String required(Map<String, Object> value, String key, int max) { String result = optional(value, key, max); if (blank(result)) throw invalid("INVALID_ACTION_PAYLOAD", "动作参数缺少 " + key); return result; }
  private String optional(Map<String, Object> value, String key, int max) { Object raw = value.get(key); if (raw == null || raw.toString().isBlank()) return null; String result = raw.toString(); if (result.length() > max) throw invalid("INVALID_ACTION_PAYLOAD", "动作参数 " + key + " 过长"); return result; }
  private Map<String, Object> map(Object... values) { Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i + 1 < values.length; i += 2) if (values[i + 1] != null) result.put(String.valueOf(values[i]), values[i + 1]); return result; }
  private boolean blank(String value) { return value == null || value.isBlank(); }
  private PlannerValidationException invalid(String code, String message) { return new PlannerValidationException(code, message); }
}
