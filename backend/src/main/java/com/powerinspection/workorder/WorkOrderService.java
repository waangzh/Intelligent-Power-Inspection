package com.powerinspection.workorder;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.user.UserEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkOrderService {
  private final DataStoreService dataStore;
  private final NotificationService notificationService;

  public WorkOrderService(DataStoreService dataStore, NotificationService notificationService) {
    this.dataStore = dataStore;
    this.notificationService = notificationService;
  }

  public synchronized Map<String, Object> createFromAlarm(
      String alarmId,
      String source,
      UserEntity operator,
      String assigneeName,
      Map<String, Object> overrides) {
    Map<String, Object> existing = findByAlarmId(alarmId);
    if (existing != null) {
      markAlarmConverted(alarmId, existing, firstText(existing.get("source"), source));
      return existing;
    }

    Map<String, Object> alarm = dataStore.get(DataCategory.ALARM, alarmId);
    Map<String, Object> values = overrides == null ? Map.of() : overrides;
    String now = Instant.now().toString();
    String message = text(alarm.get("message"));
    String title = firstText(values.get("title"), "告警处置：" + abbreviate(message, 24));
    String description = firstText(values.get("description"), message);
    String locationDescription = firstText(values.get("locationDescription"), locationFromAlarm(alarm));
    String priority = firstText(values.get("priority"), priorityFor(text(alarm.get("severity"))));
    boolean automatic = "AUTO".equals(source);

    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", "wo_alarm_" + alarmId);
    order.put("title", title);
    order.put("description", description);
    if (!locationDescription.isBlank()) {
      order.put("locationDescription", locationDescription);
    }
    order.put("alarmId", alarmId);
    order.put("source", source);
    order.put("status", "PENDING");
    order.put("priority", priority);
    if (!automatic) {
      order.put("assigneeName", firstText(assigneeName, values.get("assigneeName"), operator == null ? null : operator.getDisplayName()));
    }
    order.put("createdById", automatic ? "system" : operator.getId());
    order.put("createdByName", automatic ? "系统自动" : operator.getDisplayName());
    order.put("createdAt", now);
    order.put("updatedAt", now);

    Map<String, Object> saved = dataStore.upsert(DataCategory.WORK_ORDER, order);
    markAlarmConverted(alarmId, saved, source);
    if (automatic) {
      notificationService.pushToAll("WORKORDER", "告警已自动转工单", title, "/workorders");
    } else {
      notificationService.push(operator.getId(), "WORKORDER", "工单已创建", title, "/workorders");
    }
    return saved;
  }

  public Map<String, Object> findByAlarmId(String alarmId) {
    return dataStore.list(DataCategory.WORK_ORDER).stream()
      .filter(order -> alarmId.equals(text(order.get("alarmId"))))
      .findFirst()
      .orElse(null);
  }

  private void markAlarmConverted(String alarmId, Map<String, Object> order, String source) {
    Map<String, Object> alarm = dataStore.find(DataCategory.ALARM, alarmId);
    if (alarm == null) {
      return;
    }
    String mode = firstText(
      alarm.get("workOrderModeApplied"),
      "AUTO".equals(source) ? "AUTO" : "MANUAL"
    );
    dataStore.patch(DataCategory.ALARM, alarmId, map(
      "workOrderModeApplied", mode,
      "workOrderConversionSource", source,
      "workOrderConversionStatus", "SUCCEEDED",
      "workOrderConversionError", null,
      "workOrderConversionFailedAt", null,
      "workOrderId", order.get("id"),
      "convertedAt", firstText(alarm.get("convertedAt"), Instant.now().toString())
    ));
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
