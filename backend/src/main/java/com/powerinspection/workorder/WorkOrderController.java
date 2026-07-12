package com.powerinspection.workorder;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
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
  private final WorkOrderService workOrderService;

  public WorkOrderController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser, WorkOrderService workOrderService) {
    this.dataStore = dataStore;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.workOrderService = workOrderService;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> orders() {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    return ApiResponse.ok(dataStore.list(DataCategory.WORK_ORDER));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> order(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    return ApiResponse.ok(dataStore.get(DataCategory.WORK_ORDER, id));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    body.putIfAbsent("id", Ids.next("wo"));
    body.putIfAbsent("status", "PENDING");
    body.putIfAbsent("createdAt", Instant.now().toString());
    body.put("updatedAt", Instant.now().toString());
    return ApiResponse.ok(dataStore.upsert(DataCategory.WORK_ORDER, body));
  }

  @PostMapping("/from-alarm/{alarmId}")
  public ApiResponse<Map<String, Object>> createFromAlarm(@PathVariable String alarmId, @RequestBody(required = false) Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    String assigneeName = body == null || body.get("assigneeName") == null ? null : body.get("assigneeName").toString();
    return ApiResponse.ok(workOrderService.createFromAlarm(alarmId, "MANUAL", currentUser.get(), assigneeName, null));
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    body.put("updatedAt", Instant.now().toString());
    return ApiResponse.ok(dataStore.patch(DataCategory.WORK_ORDER, id, body));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    dataStore.delete(DataCategory.WORK_ORDER, id);
    return ApiResponse.ok();
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<Map<String, Object>> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    String status = String.valueOf(body.get("status"));
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
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    return ApiResponse.ok(dataStore.patch(DataCategory.WORK_ORDER, id, Map.of("assigneeName", body.get("assigneeName"), "updatedAt", Instant.now().toString())));
  }
}
