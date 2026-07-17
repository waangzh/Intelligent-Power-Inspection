package com.powerinspection.workorder;

import com.powerinspection.user.UserEntity;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Compatibility facade. Prefer {@link WorkOrderCommandService} for new call sites.
 */
@Service
public class WorkOrderService {
  private final WorkOrderCommandService commandService;

  public WorkOrderService(WorkOrderCommandService commandService) {
    this.commandService = commandService;
  }

  public Map<String, Object> createFromAlarm(
      String alarmId,
      String source,
      UserEntity operator,
      String assigneeName,
      Map<String, Object> overrides) {
    CreateWorkOrderFromAlarmRequest request = CreateWorkOrderFromAlarmRequest.fromMap(overrides);
    if (assigneeName != null && !assigneeName.isBlank()) {
      request.setAssigneeName(assigneeName);
    }
    return commandService.createFromAlarm(alarmId, ConversionSource.from(source), operator, request);
  }

  public Map<String, Object> findByAlarmId(String alarmId) {
    return commandService.findByAlarmId(alarmId);
  }
}
