package com.powerinspection.notification;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.security.CurrentUser;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final DataStoreService dataStore;
  private final CurrentUser currentUser;

  public NotificationController(DataStoreService dataStore, CurrentUser currentUser) {
    this.dataStore = dataStore;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> notifications() {
    String userId = currentUser.get().getId();
    return ApiResponse.ok(dataStore.list(DataCategory.NOTIFICATION).stream()
      .filter(item -> userId.equals(String.valueOf(item.get("userId"))) || "*".equals(String.valueOf(item.get("userId"))))
      .toList());
  }

  @PatchMapping("/{id}/read")
  public ApiResponse<Map<String, Object>> markRead(@PathVariable String id) {
    return ApiResponse.ok(dataStore.patch(DataCategory.NOTIFICATION, id, Map.of("read", true)));
  }

  @PatchMapping("/read-all")
  public ApiResponse<List<Map<String, Object>>> markAllRead() {
    String userId = currentUser.get().getId();
    dataStore.list(DataCategory.NOTIFICATION).stream()
      .filter(item -> userId.equals(String.valueOf(item.get("userId"))) || "*".equals(String.valueOf(item.get("userId"))))
      .forEach(item -> dataStore.patch(DataCategory.NOTIFICATION, String.valueOf(item.get("id")), Map.of("read", true)));
    return notifications();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> remove(@PathVariable String id) {
    dataStore.delete(DataCategory.NOTIFICATION, id);
    return ApiResponse.ok();
  }
}
