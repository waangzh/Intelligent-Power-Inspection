package com.powerinspection.alarm;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {
  private final DataStoreService dataStore;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public AlarmController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser) {
    this.dataStore = dataStore;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> alarms() {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(dataStore.list(DataCategory.ALARM));
  }

  @PostMapping("/{id}/ack")
  public ApiResponse<Map<String, Object>> acknowledge(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.ALARM_ACK);
    return ApiResponse.ok(dataStore.patch(DataCategory.ALARM, id, Map.of("acknowledged", true)));
  }

  @PostMapping("/ack-all")
  public ApiResponse<List<Map<String, Object>>> acknowledgeAll() {
    permissionService.require(currentUser.get(), Permission.ALARM_ACK);
    dataStore.list(DataCategory.ALARM).forEach(alarm -> dataStore.patch(DataCategory.ALARM, String.valueOf(alarm.get("id")), Map.of("acknowledged", true)));
    return ApiResponse.ok(dataStore.list(DataCategory.ALARM));
  }
}
