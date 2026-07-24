package com.powerinspection.alarm;

import com.powerinspection.common.ResourceChangeEvent;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.workorder.ConversionSource;
import com.powerinspection.workorder.CreateWorkOrderFromAlarmRequest;
import com.powerinspection.workorder.WorkOrderCommandService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlarmService {
  private final DataStoreService dataStore;
  private final AlarmWorkOrderPolicyService policyService;
  private final WorkOrderCommandService workOrderCommandService;
  private final NotificationService notificationService;
  private final SimpMessagingTemplate messagingTemplate;

  public AlarmService(
      DataStoreService dataStore,
      AlarmWorkOrderPolicyService policyService,
      WorkOrderCommandService workOrderCommandService,
      NotificationService notificationService,
      SimpMessagingTemplate messagingTemplate) {
    this.dataStore = dataStore;
    this.policyService = policyService;
    this.workOrderCommandService = workOrderCommandService;
    this.notificationService = notificationService;
    this.messagingTemplate = messagingTemplate;
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> alarm) {
    String mode = policyService.modeFor(text(alarm.get("severity")));
    alarm.putIfAbsent("acknowledged", false);
    alarm.put("workOrderModeApplied", mode);
    alarm.put("workOrderConversionStatus", "AUTO".equals(mode) ? "PROCESSING" : "WAITING_MANUAL");
    Map<String, Object> saved = persist(alarm);
    if ("AUTO".equals(mode)) {
      saved = autoConvert(text(saved.get("id")));
    }
    publish(saved);
    notificationService.pushToAll("ALARM", "新告警", text(saved.get("message")), "/alarms");
    return saved;
  }

  @Transactional
  public Map<String, Object> retryAutoConversion(String alarmId) {
    persist(patchMap(alarmId, map(
      "workOrderModeApplied", "AUTO",
      "workOrderConversionStatus", "PROCESSING"
    )));
    Map<String, Object> saved = autoConvert(alarmId);
    publish(saved);
    return saved;
  }

  @Transactional
  public Map<String, Object> acknowledge(String alarmId) {
    Map<String, Object> saved = dataStore.patch(DataCategory.ALARM, alarmId, Map.of("acknowledged", true));
    publish(saved);
    return saved;
  }

  private Map<String, Object> autoConvert(String alarmId) {
    try {
      workOrderCommandService.createFromAlarm(alarmId, ConversionSource.AUTO, null, new CreateWorkOrderFromAlarmRequest());
      return dataStore.get(DataCategory.ALARM, alarmId);
    } catch (Exception ex) {
      return persist(patchMap(alarmId, map(
        "workOrderConversionStatus", "FAILED",
        "workOrderConversionError", ex.getMessage() == null ? "自动转工单失败" : ex.getMessage(),
        "workOrderConversionFailedAt", Instant.now().toString()
      )));
    }
  }

  private Map<String, Object> persist(Map<String, Object> alarm) {
    alarm.put("updatedAt", Instant.now().toString());
    alarm.putIfAbsent("createdAt", Instant.now().toString());
    return dataStore.upsert(DataCategory.ALARM, alarm);
  }

  private Map<String, Object> patchMap(String alarmId, Map<String, Object> patch) {
    Map<String, Object> current = dataStore.get(DataCategory.ALARM, alarmId);
    current.putAll(patch);
    current.put("id", alarmId);
    return current;
  }

  private void publish(Map<String, Object> alarm) {
    messagingTemplate.convertAndSend("/topic/alarms", ResourceChangeEvent.updated("alarm", alarm.get("id")));
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
