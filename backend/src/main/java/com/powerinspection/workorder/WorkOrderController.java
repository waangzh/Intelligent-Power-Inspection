package com.powerinspection.workorder;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import com.powerinspection.user.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {
  private final DataStoreService dataStore;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;
  private final NotificationService notificationService;

  public WorkOrderController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser, NotificationService notificationService) {
    this.dataStore = dataStore;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.notificationService = notificationService;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> orders() {
    permissionService.require(currentUser.get(), Permission.WORKORDER_VIEW);
    return ApiResponse.ok(dataStore.list(DataCategory.WORK_ORDER));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> order(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_VIEW);
    return ApiResponse.ok(dataStore.get(DataCategory.WORK_ORDER, id));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_CREATE);
    body.putIfAbsent("id", Ids.next("wo"));
    body.putIfAbsent("status", "PENDING");
    body.putIfAbsent("createdAt", Instant.now().toString());
    body.put("updatedAt", Instant.now().toString());
    return ApiResponse.ok(dataStore.upsert(DataCategory.WORK_ORDER, body));
  }

  @PostMapping("/from-alarm/{alarmId}")
  public ApiResponse<Map<String, Object>> createFromAlarm(@PathVariable String alarmId, @RequestBody(required = false) Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_CREATE);
    dataStore.list(DataCategory.WORK_ORDER).stream()
      .filter(order -> alarmId.equals(String.valueOf(order.get("alarmId"))))
      .findFirst()
      .ifPresent(order -> {
        throw ApiException.badRequest("该告警已有关联工单");
      });
    Map<String, Object> alarm = dataStore.get(DataCategory.ALARM, alarmId);
    UserEntity user = currentUser.get();
    String severity = String.valueOf(alarm.get("severity"));
    String priority = "CRITICAL".equals(severity) ? "URGENT" : "HIGH".equals(severity) ? "HIGH" : "MEDIUM";
    String now = Instant.now().toString();
    Map<String, Object> order = new java.util.LinkedHashMap<>();
    order.put("id", Ids.next("wo"));
    order.put("title", "告警处置：" + String.valueOf(alarm.get("message")).substring(0, Math.min(24, String.valueOf(alarm.get("message")).length())));
    order.put("description", alarm.get("message"));
    order.put("alarmId", alarmId);
    order.put("status", "PENDING");
    order.put("priority", priority);
    order.put("assigneeName", body != null && body.get("assigneeName") != null ? body.get("assigneeName") : user.getDisplayName());
    order.put("createdById", user.getId());
    order.put("createdByName", user.getDisplayName());
    order.put("createdAt", now);
    order.put("updatedAt", now);
    Map<String, Object> saved = dataStore.upsert(DataCategory.WORK_ORDER, order);
    notificationService.push(user.getId(), "WORKORDER", "工单已创建", String.valueOf(order.get("title")), "/workorders");
    return ApiResponse.ok(saved);
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.requireAny(currentUser.get(), Permission.WORKORDER_PROCESS, Permission.WORKORDER_ASSIGN);
    body.put("updatedAt", Instant.now().toString());
    return ApiResponse.ok(dataStore.patch(DataCategory.WORK_ORDER, id, body));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_CREATE);
    dataStore.delete(DataCategory.WORK_ORDER, id);
    return ApiResponse.ok();
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<Map<String, Object>> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
    String status = String.valueOf(body.get("status"));
    if ("CLOSED".equals(status) || "CANCELLED".equals(status)) {
      permissionService.require(currentUser.get(), Permission.WORKORDER_REVIEW);
    } else {
      permissionService.require(currentUser.get(), Permission.WORKORDER_PROCESS);
    }
    Map<String, Object> patch = new java.util.LinkedHashMap<>();
    patch.put("status", status);
    patch.put("updatedAt", Instant.now().toString());
    if (body.get("resolution") != null) {
      patch.put("resolution", body.get("resolution"));
    }
    if ("CLOSED".equals(status)) {
      patch.put("closedAt", Instant.now().toString());
    }
    return ApiResponse.ok(dataStore.patch(DataCategory.WORK_ORDER, id, patch));
  }

  @PatchMapping("/{id}/assign")
  public ApiResponse<Map<String, Object>> assign(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_ASSIGN);
    return ApiResponse.ok(dataStore.patch(DataCategory.WORK_ORDER, id, Map.of("assigneeName", body.get("assigneeName"), "updatedAt", Instant.now().toString())));
  }
}
