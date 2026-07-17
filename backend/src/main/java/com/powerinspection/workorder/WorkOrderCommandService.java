package com.powerinspection.workorder;

import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import com.powerinspection.user.UserRole;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkOrderCommandService {
  private final DataStoreService dataStore;
  private final NotificationService notificationService;
  private final UserRepository userRepository;
  private final WorkOrderRepository workOrderRepository;
  private final WorkOrderTransitionRepository transitionRepository;

  public WorkOrderCommandService(
      DataStoreService dataStore,
      NotificationService notificationService,
      UserRepository userRepository,
      WorkOrderRepository workOrderRepository,
      WorkOrderTransitionRepository transitionRepository) {
    this.dataStore = dataStore;
    this.notificationService = notificationService;
    this.userRepository = userRepository;
    this.workOrderRepository = workOrderRepository;
    this.transitionRepository = transitionRepository;
  }

  @Transactional
  public synchronized Map<String, Object> createFromAlarm(
      String alarmId,
      ConversionSource source,
      UserEntity currentUser,
      CreateWorkOrderFromAlarmRequest request) {
    if (alarmId == null || alarmId.isBlank()) {
      throw ApiException.badRequest("缺少告警 ID");
    }
    ConversionSource conversionSource = source == null ? ConversionSource.MANUAL : source;
    CreateWorkOrderFromAlarmRequest safeRequest = request == null ? new CreateWorkOrderFromAlarmRequest() : request;

    Map<String, Object> existing = findByAlarmId(alarmId);
    if (existing != null) {
      markAlarmConverted(alarmId, existing, conversionSource);
      return existing;
    }

    Map<String, Object> alarm = dataStore.get(DataCategory.ALARM, alarmId);
    Map<String, Object> values = safeRequest.overrides();
    String now = Instant.now().toString();
    String message = text(alarm.get("message"));
    String title = firstText(values.get("title"), "告警处置：" + abbreviate(message, 24));
    String description = firstText(values.get("description"), message);
    String locationDescription = firstText(values.get("locationDescription"), locationFromAlarm(alarm));
    String priority = firstText(values.get("priority"), priorityFor(text(alarm.get("severity"))));
    boolean automatic = conversionSource == ConversionSource.AUTO;

    if (!automatic && currentUser == null) {
      throw ApiException.badRequest("人工/Agent 转工单必须提供当前用户");
    }

    Map<String, Object> order = new LinkedHashMap<>();
    String orderId = "wo_alarm_" + alarmId;
    order.put("id", orderId);
    order.put("title", title);
    order.put("description", description);
    if (!locationDescription.isBlank()) {
      order.put("locationDescription", locationDescription);
    }
    order.put("alarmId", alarmId);
    order.put("source", conversionSource.name());
    order.put("status", "PENDING");
    order.put("priority", priority);
    if (conversionSource == ConversionSource.AGENT) {
      order.put("assigneeName", firstText(
        safeRequest.getAssigneeName(),
        values.get("assigneeName"),
        currentUser == null ? null : currentUser.getDisplayName()
      ));
    }
    putIfPresent(order, "taskId", firstText(safeRequest.getTaskId(), values.get("taskId"), alarm.get("taskId")));
    putIfPresent(order, "agentActionId", safeRequest.getAgentActionId());
    putIfPresent(order, "agentIdempotencyKey", safeRequest.getAgentIdempotencyKey());
    order.put("createdById", automatic ? "system" : currentUser.getId());
    order.put("createdByName", automatic ? "系统自动" : currentUser.getDisplayName());
    order.put("createdAt", now);
    order.put("updatedAt", now);

    Map<String, Object> saved = persistWorkOrder(order);
    recordTransition(orderId, null, "PENDING", conversionSource.name(), automatic ? "system" : currentUser.getId(), "从告警创建工单");
    markAlarmConverted(alarmId, saved, conversionSource);
    notifyCreated(saved, conversionSource, currentUser);
    return saved;
  }

  public Map<String, Object> findByAlarmId(String alarmId) {
    return workOrderRepository.findByAlarmId(alarmId)
      .map(WorkOrderEntity::toMap)
      .orElseGet(() -> dataStore.list(DataCategory.WORK_ORDER).stream()
        .filter(order -> alarmId.equals(text(order.get("alarmId"))))
        .findFirst()
        .orElse(null));
  }

  private Map<String, Object> persistWorkOrder(Map<String, Object> order) {
    return dataStore.upsert(DataCategory.WORK_ORDER, order);
  }

  private void recordTransition(
      String workOrderId,
      String fromStatus,
      String toStatus,
      String source,
      String actorId,
      String remark) {
    WorkOrderTransitionEntity transition = new WorkOrderTransitionEntity();
    transition.setId("wot_" + workOrderId + "_" + System.currentTimeMillis());
    transition.setWorkOrderId(workOrderId);
    transition.setFromStatus(fromStatus);
    transition.setToStatus(toStatus);
    transition.setSource(source);
    transition.setActorId(actorId);
    transition.setRemark(remark);
    transition.setCreatedAt(Instant.now().toString());
    transitionRepository.save(transition);
  }

  private void notifyCreated(Map<String, Object> order, ConversionSource source, UserEntity operator) {
    String title = String.valueOf(order.get("title"));
    switch (source) {
      case AUTO -> notificationService.pushToAll("WORKORDER", "告警已自动转工单", title, "/workorders");
      case MANUAL -> userRepository.findByRoleAndEnabledTrue(UserRole.DISPATCHER)
        .forEach(dispatcher -> notificationService.push(
          dispatcher.getId(), "WORKORDER", "新工单待接单", title, "/workorders"
        ));
      case AGENT -> {
        if (operator != null) {
          notificationService.push(operator.getId(), "WORKORDER", "工单已创建", title, "/workorders");
        }
      }
    }
  }

  private void markAlarmConverted(String alarmId, Map<String, Object> order, ConversionSource source) {
    Map<String, Object> alarm = dataStore.find(DataCategory.ALARM, alarmId);
    if (alarm == null) {
      return;
    }
    String mode = firstText(
      alarm.get("workOrderModeApplied"),
      source == ConversionSource.AUTO ? "AUTO" : "MANUAL"
    );
    Map<String, Object> patch = map(
      "workOrderModeApplied", mode,
      "workOrderConversionSource", source.name(),
      "workOrderConversionStatus", "SUCCEEDED",
      "workOrderConversionError", null,
      "workOrderConversionFailedAt", null,
      "workOrderId", order.get("id"),
      "convertedAt", firstText(alarm.get("convertedAt"), Instant.now().toString())
    );
    dataStore.patch(DataCategory.ALARM, alarmId, patch);
  }

  private String priorityFor(String severity) {
    return switch (severity == null ? "" : severity) {
      case "CRITICAL" -> "URGENT";
      case "HIGH" -> "HIGH";
      case "LOW" -> "LOW";
      default -> "MEDIUM";
    };
  }

  private String locationFromAlarm(Map<String, Object> alarm) {
    String routeName = text(alarm.get("routeName"));
    String checkpointName = text(alarm.get("checkpointName"));
    if (routeName == null || routeName.isBlank()) {
      return checkpointName == null ? "" : checkpointName;
    }
    return checkpointName == null || checkpointName.isBlank() ? routeName : routeName + " / " + checkpointName;
  }

  private String abbreviate(String value, int max) {
    String safe = value == null ? "巡检异常" : value;
    return safe.length() <= max ? safe : safe.substring(0, max);
  }

  private void putIfPresent(Map<String, Object> target, String key, String value) {
    if (value != null && !value.isBlank()) {
      target.put(key, value);
    }
  }

  private String firstText(Object... values) {
    for (Object value : values) {
      String text = text(value);
      if (text != null && !text.isBlank()) {
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
}
