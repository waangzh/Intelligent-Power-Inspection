package com.powerinspection.alarm;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.workorder.WorkOrderService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlarmService {
  private final DataStoreService dataStore;
  private final AlarmWorkOrderPolicyService policyService;
  private final WorkOrderService workOrderService;
  private final NotificationService notificationService;
  private final SimpMessagingTemplate messagingTemplate;

  public AlarmService(
      DataStoreService dataStore,
      AlarmWorkOrderPolicyService policyService,
      WorkOrderService workOrderService,
      NotificationService notificationService,
      SimpMessagingTemplate messagingTemplate) {
    this.dataStore = dataStore;
    this.policyService = policyService;
    this.workOrderService = workOrderService;
    this.notificationService = notificationService;
    this.messagingTemplate = messagingTemplate;
  }

  public Map<String, Object> create(Map<String, Object> alarm) {
    String mode = policyService.modeFor(text(alarm.get("severity")));
    alarm.putIfAbsent("acknowledged", false);
    alarm.put("workOrderModeApplied", mode);
    alarm.put("workOrderConversionStatus", "AUTO".equals(mode) ? "PROCESSING" : "WAITING_MANUAL");
    Map<String, Object> saved = dataStore.upsert(DataCategory.ALARM, alarm);
    if ("AUTO".equals(mode)) {
      saved = autoConvert(text(saved.get("id")));
    }
    publish(saved);
    notificationService.pushToAll("ALARM", "新告警", text(saved.get("message")), "/alarms");
    return saved;
  }

  public Map<String, Object> retryAutoConversion(String alarmId) {
    dataStore.patch(DataCategory.ALARM, alarmId, map(
      "workOrderModeApplied", "AUTO",
      "workOrderConversionStatus", "PROCESSING"
    ));
    Map<String, Object> saved = autoConvert(alarmId);
    publish(saved);
    return saved;
  }

  private Map<String, Object> autoConvert(String alarmId) {
    try {
      workOrderService.createFromAlarm(alarmId, "AUTO", null, null, null);
      return dataStore.get(DataCategory.ALARM, alarmId);
    } catch (Exception ex) {
      return dataStore.patch(DataCategory.ALARM, alarmId, map(
        "workOrderConversionStatus", "FAILED",
        "workOrderConversionError", ex.getMessage() == null ? "自动转工单失败" : ex.getMessage(),
        "workOrderConversionFailedAt", Instant.now().toString()
      ));
    }
  }

  private void publish(Map<String, Object> alarm) {
    messagingTemplate.convertAndSend("/topic/alarms", alarm);
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
