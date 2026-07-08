package com.powerinspection.agent;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.model.LocateAnythingRequest;
import com.powerinspection.model.ModelServiceException;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.user.UserEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AgentToolService {
  private final DataStoreService dataStore;
  private final LocateAnythingGateway locateAnythingGateway;
  private final NotificationService notificationService;

  public AgentToolService(DataStoreService dataStore, LocateAnythingGateway locateAnythingGateway, NotificationService notificationService) {
    this.dataStore = dataStore;
    this.locateAnythingGateway = locateAnythingGateway;
    this.notificationService = notificationService;
  }

  public Map<String, Object> queryTask(String taskId) {
    if (!hasText(taskId)) {
      return null;
    }
    Map<String, Object> task = dataStore.find(DataCategory.TASK, taskId);
    if (task == null) {
      return map("missing", true, "taskId", taskId);
    }
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("task", task);
    context.put("events", dataStore.list(DataCategory.EVENT).stream().filter(event -> taskId.equals(text(event.get("taskId")))).toList());
    String routeId = text(task.get("routeId"));
    if (hasText(routeId)) {
      context.put("route", dataStore.find(DataCategory.ROUTE, routeId));
    }
    String robotId = text(task.get("robotId"));
    if (hasText(robotId)) {
      context.put("robot", dataStore.find(DataCategory.ROBOT, robotId));
    }
    return context;
  }

  public List<Map<String, Object>> queryAlarms(String alarmId, String taskId) {
    if (hasText(alarmId)) {
      Map<String, Object> alarm = dataStore.find(DataCategory.ALARM, alarmId);
      return alarm == null ? List.of(map("missing", true, "alarmId", alarmId)) : List.of(alarm);
    }
    if (!hasText(taskId)) {
      return List.of();
    }
    return dataStore.list(DataCategory.ALARM).stream()
      .filter(alarm -> taskId.equals(text(alarm.get("taskId"))))
      .toList();
  }

  public List<Map<String, Object>> queryWorkOrders(String alarmId, String taskId) {
    return dataStore.list(DataCategory.WORK_ORDER).stream()
      .filter(order -> matchesWorkOrder(order, alarmId, taskId))
      .toList();
  }

  public List<Map<String, Object>> locateAnythingEvidence(Map<String, Object> taskContext, List<Map<String, Object>> alarms) {
    List<Map<String, Object>> items = new ArrayList<>();
    for (Map<String, Object> alarm : alarms) {
      if (Boolean.TRUE.equals(alarm.get("missing"))) {
        continue;
      }
      if (alarm.get("finding") instanceof Map<?, ?> finding) {
        items.add(map(
          "title", "已有 LocateAnything 结果",
          "content", "告警已携带模型识别结果，可直接作为引用依据。",
          "sourceId", alarm.get("id"),
          "imageUrl", firstText(finding.get("imageUrl"), alarm.get("imageUrl")),
          "payload", normalize(finding)
        ));
        continue;
      }
      String imageUrl = text(alarm.get("imageUrl"));
      if (!hasText(imageUrl)) {
        items.add(map(
          "title", "LocateAnything 未执行",
          "content", "告警缺少 imageUrl，无法重新调用视觉模型。",
          "sourceId", alarm.get("id"),
          "payload", map("reason", "missing_image_url")
        ));
        continue;
      }
      LocateRequestParts parts = locateRequestParts(taskContext, alarm);
      try {
        List<LocateAnythingFinding> findings = locateAnythingGateway.detectCheckpoint(
          new LocateAnythingRequest(parts.task(), parts.route(), parts.checkpoint(), imageUrl, parts.detections())
        );
        if (findings.isEmpty()) {
          items.add(map(
            "title", "LocateAnything 未发现新增异常",
            "content", "视觉模型完成复核，但未返回新的异常框选结果。",
            "sourceId", alarm.get("id"),
            "imageUrl", imageUrl,
            "payload", map("findings", List.of())
          ));
        }
        for (LocateAnythingFinding finding : findings) {
          items.add(map(
            "title", "LocateAnything 复核结果",
            "content", "模型识别到 " + firstText(finding.label(), finding.type(), "abnormal") + "，置信度 " + finding.score(),
            "sourceId", alarm.get("id"),
            "imageUrl", firstText(finding.imageUrl(), imageUrl),
            "payload", findingToMap(finding)
          ));
        }
      } catch (ModelServiceException ex) {
        items.add(map(
          "title", "LocateAnything 调用失败",
          "content", ex.getMessage(),
          "sourceId", alarm.get("id"),
          "imageUrl", imageUrl,
          "payload", map("errorMessage", ex.getMessage())
        ));
      }
    }
    return items;
  }

  public Map<String, Object> createWorkOrderDraft(Map<String, Object> payload, UserEntity user) {
    String alarmId = text(payload.get("alarmId"));
    if (hasText(alarmId)) {
      dataStore.list(DataCategory.WORK_ORDER).stream()
        .filter(order -> alarmId.equals(text(order.get("alarmId"))))
        .findFirst()
        .ifPresent(order -> {
          throw ApiException.badRequest("该告警已有关联工单");
        });
    }
    String now = Instant.now().toString();
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", Ids.next("wo"));
    order.put("title", firstText(payload.get("title"), "Agent 工单草稿"));
    order.put("description", firstText(payload.get("description"), "由巡检处置 Agent 生成"));
    if (hasText(alarmId)) {
      order.put("alarmId", alarmId);
    }
    order.put("status", "PENDING");
    order.put("priority", firstText(payload.get("priority"), "MEDIUM"));
    order.put("assigneeName", firstText(payload.get("assigneeName"), user.getDisplayName()));
    order.put("createdById", user.getId());
    order.put("createdByName", user.getDisplayName());
    order.put("createdAt", now);
    order.put("updatedAt", now);
    return dataStore.upsert(DataCategory.WORK_ORDER, order);
  }

  public Map<String, Object> pushNotification(Map<String, Object> payload) {
    String userId = firstText(payload.get("userId"), "*");
    String type = firstText(payload.get("type"), "AGENT");
    String title = firstText(payload.get("title"), "Agent 处置建议");
    String content = firstText(payload.get("content"), "巡检处置 Agent 已生成建议动作，请及时确认。");
    String link = text(payload.get("link"));
    return notificationService.push(userId, type, title, content, link);
  }

  private boolean matchesWorkOrder(Map<String, Object> order, String alarmId, String taskId) {
    if (hasText(alarmId) && alarmId.equals(text(order.get("alarmId")))) {
      return true;
    }
    return hasText(taskId) && taskId.equals(text(order.get("taskId")));
  }

  @SuppressWarnings("unchecked")
  private LocateRequestParts locateRequestParts(Map<String, Object> taskContext, Map<String, Object> alarm) {
    Map<String, Object> task = taskContext == null ? null : (Map<String, Object>) taskContext.get("task");
    Map<String, Object> route = taskContext == null ? null : (Map<String, Object>) taskContext.get("route");
    if (task == null) {
      task = map("id", firstText(alarm.get("taskId"), "agent_task"), "name", "Agent Alarm Review");
    }
    if (route == null) {
      route = map("id", "agent_route", "name", firstText(alarm.get("routeName"), "Agent Review"));
    }
    Map<String, Object> checkpoint = findCheckpoint(route, text(alarm.get("checkpointName")));
    if (checkpoint == null) {
      checkpoint = map("id", "agent_checkpoint", "name", firstText(alarm.get("checkpointName"), "Alarm Image"));
    }
    List<Map<String, Object>> detections = checkpoint.get("detections") instanceof List<?> list
      ? (List<Map<String, Object>>) list
      : List.of(map("type", firstText(alarm.get("type"), "FOREIGN_OBJECT"), "prompt", text(alarm.get("message")), "threshold", 0.75, "enabled", true));
    return new LocateRequestParts(task, route, checkpoint, detections);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> findCheckpoint(Map<String, Object> route, String checkpointName) {
    if (!hasText(checkpointName) || !(route.get("checkpoints") instanceof List<?> checkpoints)) {
      return null;
    }
    for (Object item : checkpoints) {
      if (item instanceof Map<?, ?> cp && checkpointName.equals(text(cp.get("name")))) {
        return (Map<String, Object>) cp;
      }
    }
    return null;
  }

  private Map<String, Object> findingToMap(LocateAnythingFinding finding) {
    Map<String, Object> item = map(
      "type", finding.type(),
      "prompt", finding.prompt(),
      "score", finding.score(),
      "bbox", finding.bbox(),
      "label", finding.label(),
      "imageUrl", finding.imageUrl()
    );
    if (finding.rawResult() != null) {
      item.put("rawResult", finding.rawResult());
    }
    return item;
  }

  private Map<String, Object> normalize(Map<?, ?> raw) {
    Map<String, Object> item = new LinkedHashMap<>();
    raw.forEach((key, value) -> item.put(String.valueOf(key), value));
    return item;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String firstText(Object... values) {
    for (Object value : values) {
      String text = text(value);
      if (hasText(text)) {
        return text;
      }
    }
    return "";
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> item = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      item.put(values[i].toString(), values[i + 1]);
    }
    return item;
  }

  private record LocateRequestParts(
    Map<String, Object> task,
    Map<String, Object> route,
    Map<String, Object> checkpoint,
    List<Map<String, Object>> detections
  ) {
  }
}
