package com.powerinspection.notification;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.ListQuery;
import com.powerinspection.common.PageResult;
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
  private final NotificationAccessService notificationAccessService;
  private final CurrentUser currentUser;

  public NotificationController(
      NotificationAccessService notificationAccessService,
      CurrentUser currentUser) {
    this.notificationAccessService = notificationAccessService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ApiResponse<PageResult<Map<String, Object>>> notifications(ListQuery query) {
    return ApiResponse.ok(notificationAccessService.page(currentUser.get().getId(), query));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> notification(@PathVariable String id) {
    return ApiResponse.ok(notificationAccessService.get(id, currentUser.get().getId()));
  }

  @PatchMapping("/{id}/read")
  public ApiResponse<Map<String, Object>> markRead(@PathVariable String id) {
    return ApiResponse.ok(notificationAccessService.markRead(id, currentUser.get().getId()));
  }

  @PatchMapping("/read-all")
  public ApiResponse<List<Map<String, Object>>> markAllRead() {
    return ApiResponse.ok(notificationAccessService.markAllRead(currentUser.get().getId()));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> remove(@PathVariable String id) {
    notificationAccessService.remove(id, currentUser.get().getId());
    return ApiResponse.ok();
  }
}
