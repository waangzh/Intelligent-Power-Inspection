package com.powerinspection.task;

import com.powerinspection.common.ApiException;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import com.powerinspection.user.UserRole;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskEmergencyStopService {
  private final TaskExecutionLifecycleService executionLifecycleService;
  private final TaskExecutionControlService executionControlService;
  private final TaskService taskService;
  private final NotificationService notificationService;
  private final UserRepository userRepository;

  public TaskEmergencyStopService(
      TaskExecutionLifecycleService executionLifecycleService,
      TaskExecutionControlService executionControlService,
      TaskService taskService,
      NotificationService notificationService,
      UserRepository userRepository) {
    this.executionLifecycleService = executionLifecycleService;
    this.executionControlService = executionControlService;
    this.taskService = taskService;
    this.notificationService = notificationService;
    this.userRepository = userRepository;
  }

  @Transactional
  public Map<String, Object> emergencyStop(String taskId, String idempotencyKey, String reason, UserEntity operator) {
    String normalized = reason == null ? "" : reason.replaceAll("[\\r\\n]+", " ").trim();
    if (normalized.isBlank()) {
      throw ApiException.badRequest("远程急停必须提供原因");
    }
    if (normalized.length() > 500) {
      throw ApiException.badRequest("远程急停原因不能超过 500 个字符");
    }

    Map<String, Object> result;
    if (!executionLifecycleService.hasExecution(taskId)) {
      result = taskService.emergencyStop(taskId, normalized, operator);
    } else {
      result = executionControlService.request(
        taskId,
        TaskExecutionControlAction.ESTOP,
        idempotencyKey,
        normalized,
        operator
      );
      String operatorName = nonBlank(operator.getDisplayName()) ? operator.getDisplayName() : operator.getUsername();
      taskService.recordAuditEvent(
        taskId,
        "ESTOP",
        "已发起远程急停，等待机器人 ACK/确认。原因：" + normalized + "（操作人：" + operatorName + "）"
      );
    }
    notifyOperators(taskId, normalized, operator);
    return result;
  }

  private void notifyOperators(String taskId, String reason, UserEntity operator) {
    String operatorName = nonBlank(operator.getDisplayName()) ? operator.getDisplayName() : operator.getUsername();
    String title = "任务远程急停";
    String content = operatorName + " 对任务 " + taskId + " 发起远程急停：" + reason;
    String link = "/tasks/" + taskId;
    Set<String> notified = new LinkedHashSet<>();
    for (UserRole role : List.of(UserRole.DISPATCHER, UserRole.ADMIN)) {
      for (UserEntity user : userRepository.findByRoleAndEnabledTrue(role)) {
        if (!notified.add(user.getId())) {
          continue;
        }
        notificationService.push(user.getId(), "TASK", title, content, link);
      }
    }
  }

  private static boolean nonBlank(String value) {
    return value != null && !value.isBlank();
  }
}
