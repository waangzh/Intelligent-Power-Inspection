package com.powerinspection.agent.tool;

import com.powerinspection.agent.AgentToolService;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.user.Permission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;

@Service
public class AgentToolRegistry {
  private final Map<String, AgentTool<?, ?>> tools = new LinkedHashMap<>();

  public AgentToolRegistry(AgentToolService toolService) {
    register(readTool("get_task", "获取任务", "读取指定巡检任务的受限上下文", AgentToolInputs.TaskIdInput.class, Map.class, Set.of("taskId"), input -> {
      Map<String, Object> value = toolService.queryTask(input.taskId());
      return result(value, "任务查询完成", List.of(draft(AgentEnums.EvidenceSourceType.TASK, input.taskId(), "任务上下文", value)));
    }));
    register(readTool("get_task_events", "获取任务事件", "读取指定任务的事件轨迹", AgentToolInputs.TaskIdInput.class, List.class, Set.of("taskId"), input -> {
      List<Map<String, Object>> value = toolService.queryTaskEvents(input.taskId());
      return result(value, "任务事件查询完成", List.of(draft(AgentEnums.EvidenceSourceType.TASK_EVENT, input.taskId(), "任务事件", Map.of("items", value))));
    }));
    register(readTool("get_alarm", "获取告警", "读取指定告警", AgentToolInputs.AlarmIdInput.class, Map.class, Set.of("alarmId"), input -> {
      Map<String, Object> value = toolService.queryAlarm(input.alarmId());
      return result(value, "告警查询完成", List.of(draft(AgentEnums.EvidenceSourceType.ALARM, input.alarmId(), "告警证据", value)));
    }));
    register(readTool("list_task_alarms", "列出任务告警", "读取任务关联的告警", AgentToolInputs.TaskIdInput.class, List.class, Set.of("taskId"), input -> {
      List<Map<String, Object>> value = toolService.queryAlarms(null, input.taskId());
      return result(value, "任务告警查询完成", List.of(draft(AgentEnums.EvidenceSourceType.ALARM, input.taskId(), "任务关联告警", Map.of("items", value))));
    }));
    register(readTool("get_robot", "获取机器人", "读取指定机器人状态", AgentToolInputs.RobotIdInput.class, Map.class, Set.of("robotId"), input -> {
      Map<String, Object> value = toolService.queryRobot(input.robotId());
      return result(value, "机器人查询完成", List.of(draft(AgentEnums.EvidenceSourceType.ROBOT, input.robotId(), "机器人状态", value)));
    }));
    register(readTool("get_route", "获取路线", "读取指定巡检路线", AgentToolInputs.RouteIdInput.class, Map.class, Set.of("routeId"), input -> {
      Map<String, Object> value = toolService.queryRoute(input.routeId());
      return result(value, "路线查询完成", List.of(draft(AgentEnums.EvidenceSourceType.ROUTE, input.routeId(), "路线信息", value)));
    }));
    register(readTool("list_related_work_orders", "列出关联工单", "按任务或告警读取关联工单", AgentToolInputs.RelatedWorkOrdersInput.class, List.class, Set.of("alarmId", "taskId"), input -> {
      List<Map<String, Object>> value = toolService.queryWorkOrders(input.alarmId(), input.taskId());
      return result(value, "关联工单查询完成", List.of(draft(AgentEnums.EvidenceSourceType.WORK_ORDER, first(input.alarmId(), input.taskId()), "关联工单", Map.of("items", value))));
    }));
    register(readTool("inspect_alarm_image", "复核告警图像", "使用 LocateAnything 复核告警关联图像", AgentToolInputs.InspectAlarmImageInput.class, List.class, Set.of("alarmId", "taskId"), input -> {
      List<Map<String, Object>> value = toolService.inspectAlarmImage(input.alarmId(), input.taskId());
      return result(value, "告警图像复核完成", List.of(draft(AgentEnums.EvidenceSourceType.VISION_RESULT, input.alarmId(), "视觉复核结果", Map.of("items", value))));
    }));
  }

  public Optional<AgentTool<?, ?>> find(String name) { return Optional.ofNullable(tools.get(name)); }
  public List<AgentToolDescriptor> descriptors() { return tools.values().stream().map(AgentTool::descriptor).toList(); }
  private void register(AgentTool<?, ?> tool) { tools.put(tool.descriptor().name(), tool); }

  private <I> AgentTool<I, Object> readTool(String name, String displayName, String description, Class<I> inputType, Class<?> outputType, Set<String> fields, Function<I, AgentToolResult<Object>> executor) {
    AgentToolDescriptor descriptor = new AgentToolDescriptor(name, displayName, description, inputType, outputType, Permission.AGENT_VIEW, true, AgentEnums.RiskLevel.LOW, Duration.ofSeconds(15), fields);
    return new AgentTool<>() {
      @Override public AgentToolDescriptor descriptor() { return descriptor; }
      @Override public AgentToolResult<Object> execute(I input, AgentToolExecutionContext context) { return executor.apply(input); }
    };
  }

  private AgentToolResult<Object> result(Object value, String summary, List<AgentEvidenceDraft> evidence) { return new AgentToolResult<>(value, summary, evidence); }
  private AgentEvidenceDraft draft(AgentEnums.EvidenceSourceType type, String sourceId, String title, Object payload) {
    Map<String, Object> safe = sanitize(type, payload);
    return new AgentEvidenceDraft(type, sourceId, title, summary(title, safe), safe);
  }
  private Map<String, Object> sanitize(AgentEnums.EvidenceSourceType type, Object payload) {
    Map<String, Object> value = payload instanceof Map<?, ?> map ? copy(map) : Map.of("items", payload == null ? List.of() : payload);
    return switch (type) {
      case TASK -> taskSummary(value);
      case ALARM -> value.containsKey("items") ? Map.of("items", list(value.get("items"), Set.of("id", "taskId", "severity", "message", "imageUrl", "createdAt", "missing"))) : fields(value, Set.of("id", "taskId", "severity", "message", "imageUrl", "createdAt", "missing"));
      case WORK_ORDER -> Map.of("items", list(value.get("items"), Set.of("id", "alarmId", "taskId", "title", "status", "priority", "updatedAt")));
      case TASK_EVENT -> Map.of("items", list(value.get("items"), Set.of("id", "taskId", "type", "message", "createdAt", "status")));
      case ROBOT -> fields(value, Set.of("id", "name", "status", "battery", "siteId", "updatedAt", "missing"));
      case ROUTE -> fields(value, Set.of("id", "name", "siteId", "status", "checkpointCount", "updatedAt", "missing"));
      case VISION_RESULT -> Map.of("items", list(value.get("items"), Set.of("title", "content", "sourceId", "imageUrl")));
      default -> fields(value, Set.of("reason", "text", "missing"));
    };
  }
  private Map<String, Object> taskSummary(Map<String, Object> value) {
    Map<String, Object> item = new LinkedHashMap<>();
    if (value.get("task") instanceof Map<?, ?> task) item.put("task", fields(copy(task), Set.of("id", "name", "status", "routeId", "robotId", "siteId", "updatedAt")));
    if (value.get("route") instanceof Map<?, ?> route) item.put("route", fields(copy(route), Set.of("id", "name", "siteId", "status", "checkpointCount")));
    if (value.get("robot") instanceof Map<?, ?> robot) item.put("robot", fields(copy(robot), Set.of("id", "name", "status", "battery", "siteId")));
    item.put("eventCount", value.get("events") instanceof List<?> events ? Math.min(events.size(), 20) : 0);
    if (Boolean.TRUE.equals(value.get("missing"))) item.put("missing", true);
    return item;
  }
  private Map<String, Object> fields(Map<String, Object> value, Set<String> allowed) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String key : allowed) {
      Object item = value.get(key);
      if (item == null) continue;
      result.put(key, item instanceof String text ? abbreviate(text, 500) : item instanceof Number || item instanceof Boolean ? item : String.valueOf(item));
    }
    return result;
  }
  private List<Map<String, Object>> list(Object value, Set<String> allowed) {
    if (!(value instanceof List<?> values)) return List.of();
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : values) {
      if (result.size() >= 20) break;
      if (item instanceof Map<?, ?> map) result.add(fields(copy(map), allowed));
    }
    return result;
  }
  private Map<String, Object> copy(Map<?, ?> source) { Map<String, Object> value = new LinkedHashMap<>(); source.forEach((key, item) -> value.put(String.valueOf(key), item)); return value; }
  private String summary(String title, Map<String, Object> payload) { return Boolean.TRUE.equals(payload.get("missing")) ? title + "不存在" : title + "已采集"; }
  private String first(String left, String right) { return left != null && !left.isBlank() ? left : right; }
  private String abbreviate(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
