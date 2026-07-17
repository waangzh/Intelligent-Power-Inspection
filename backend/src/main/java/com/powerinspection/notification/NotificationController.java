package com.powerinspection.notification;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.ListQuery;
import com.powerinspection.common.PageResult;
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
  public ApiResponse<PageResult<Map<String, Object>>> notifications(ListQuery query) {
    String userId = currentUser.get().getId();
    return ApiResponse.ok(dataStore.page(
      DataCategory.NOTIFICATION, query.getPage(), query.getSize(), query.getSort(), query.getDirection(),
      query.getUpdatedAfter(), query.getQ(),
      Map.of(
        "userId", userId + ",*",
        "type", query.getType() == null ? "" : query.getType(),
        "read", query.getRead() == null ? "" : query.getRead()
      )
    ));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> notification(@PathVariable String id) {
    return ApiResponse.ok(owned(id));
  }

  @PatchMapping("/{id}/read")
  public ApiResponse<Map<String, Object>> markRead(@PathVariable String id) {
    ownedForMutation(id);
    return ApiResponse.ok(dataStore.patch(DataCategory.NOTIFICATION, id, Map.of("read", true)));
  }

  @PatchMapping("/read-all")
  public ApiResponse<List<Map<String, Object>>> markAllRead() {
    String userId = currentUser.get().getId();
    dataStore.list(DataCategory.NOTIFICATION).stream()
      .filter(item -> userId.equals(String.valueOf(item.get("userId"))))
      .forEach(item -> dataStore.patch(DataCategory.NOTIFICATION, String.valueOf(item.get("id")), Map.of("read", true)));
    return ApiResponse.ok(dataStore.list(DataCategory.NOTIFICATION).stream()
      .filter(item -> userId.equals(String.valueOf(item.get("userId"))) || "*".equals(String.valueOf(item.get("userId"))))
      .toList());
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> remove(@PathVariable String id) {
    ownedForMutation(id);
    dataStore.delete(DataCategory.NOTIFICATION, id);
    return ApiResponse.ok();
  }

  private Map<String, Object> owned(String id) {
    Map<String, Object> item = dataStore.get(DataCategory.NOTIFICATION, id);
    String owner = String.valueOf(item.get("userId"));
    if (!currentUser.get().getId().equals(owner) && !"*".equals(owner)) {
      throw ApiException.forbidden("无权限访问该通知");
    }
    return item;
  }

  private Map<String, Object> ownedForMutation(String id) {
    Map<String, Object> item = owned(id);
    if ("*".equals(String.valueOf(item.get("userId")))) {
      throw ApiException.forbidden("广播通知不能由单个用户修改");
    }
    return item;
  }
}
