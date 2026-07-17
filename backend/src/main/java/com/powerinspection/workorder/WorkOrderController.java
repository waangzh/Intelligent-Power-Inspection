package com.powerinspection.workorder;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.Ids;
import com.powerinspection.common.ListQuery;
import com.powerinspection.common.PageResult;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import com.powerinspection.user.UserRole;
import java.time.Instant;
import java.util.LinkedHashMap;
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
  private final UserRepository userRepository;
  private final WorkOrderCommandService workOrderCommandService;

  public WorkOrderController(
    DataStoreService dataStore,
    PermissionService permissionService,
    CurrentUser currentUser,
    NotificationService notificationService,
    UserRepository userRepository,
    WorkOrderCommandService workOrderCommandService
  ) {
    this.dataStore = dataStore;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.notificationService = notificationService;
    this.userRepository = userRepository;
    this.workOrderCommandService = workOrderCommandService;
  }

  @GetMapping
  public ApiResponse<PageResult<Map<String, Object>>> orders(ListQuery query) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_VIEW);
    return ApiResponse.ok(dataStore.page(
      DataCategory.WORK_ORDER, query.getPage(), query.getSize(), query.getSort(), query.getDirection(),
      query.getUpdatedAfter(), query.getQ(), query.filters("status", "siteId", "type")
    ));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> order(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_VIEW);
    return ApiResponse.ok(dataStore.get(DataCategory.WORK_ORDER, id));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_CREATE);
    normalizeLocationDescription(body);
    body.putIfAbsent("id", Ids.next("wo"));
    body.putIfAbsent("status", "PENDING");
    body.putIfAbsent("createdAt", Instant.now().toString());
    body.put("updatedAt", Instant.now().toString());
    Map<String, Object> saved = dataStore.upsert(DataCategory.WORK_ORDER, body);
    notifyDispatchersNewWorkOrder(saved);
    return ApiResponse.ok(saved);
  }

  @PostMapping("/from-alarm/{alarmId}")
  public ApiResponse<Map<String, Object>> createFromAlarm(@PathVariable String alarmId, @RequestBody(required = false) Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_CREATE);
    return ApiResponse.ok(workOrderCommandService.createFromAlarm(
      alarmId,
      ConversionSource.MANUAL,
      currentUser.get(),
      CreateWorkOrderFromAlarmRequest.fromMap(body)
    ));
  }

  @PostMapping("/{id}/claim")
  public ApiResponse<Map<String, Object>> claim(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_PROCESS);
    UserEntity user = currentUser.get();
    Map<String, Object> order = dataStore.get(DataCategory.WORK_ORDER, id);
    if (!"PENDING".equals(String.valueOf(order.get("status")))) {
      throw ApiException.badRequest("仅待处理工单可接单");
    }
    if (!isUnassigned(order)) {
      throw ApiException.badRequest("工单已被其他调度员接单");
    }
    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("assigneeId", user.getId());
    patch.put("assigneeName", user.getDisplayName());
    patch.put("status", "PROCESSING");
    patch.put("claimedAt", Instant.now().toString());
    patch.put("updatedAt", Instant.now().toString());
    return ApiResponse.ok(dataStore.patch(DataCategory.WORK_ORDER, id, patch));
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_PROCESS);
    if (body.containsKey("status") || body.containsKey("review")) {
      throw ApiException.badRequest("工单状态和复核记录请通过状态接口提交");
    }
    normalizeLocationDescription(body);
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
    Map<String, Object> order = dataStore.get(DataCategory.WORK_ORDER, id);
    String status = text(body.get("status"));
    if (!isSupportedStatus(status)) {
      throw ApiException.badRequest("不支持的工单状态");
    }
    if (!isAllowedTransition(text(order.get("status")), status)) {
      throw ApiException.badRequest("工单状态流转不合法");
    }
    if ("CLOSED".equals(status) || "CANCELLED".equals(status)) {
      permissionService.require(currentUser.get(), Permission.WORKORDER_REVIEW);
    } else {
      permissionService.require(currentUser.get(), Permission.WORKORDER_PROCESS);
    }
    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("status", status);
    patch.put("updatedAt", Instant.now().toString());
    if ("REVIEW".equals(status)) {
      Map<String, Object> review = normalizeReview(body.get("review"));
      patch.put("review", review);
      patch.put("resolution", review.get("handlingMeasures"));
    } else if (body.get("resolution") != null) {
      patch.put("resolution", body.get("resolution"));
    }
    if ("CLOSED".equals(status)) {
      patch.put("closedAt", Instant.now().toString());
    }
    return ApiResponse.ok(dataStore.patch(DataCategory.WORK_ORDER, id, patch));
  }

  private void notifyDispatchersNewWorkOrder(Map<String, Object> order) {
    String title = String.valueOf(order.get("title"));
    userRepository.findByRoleAndEnabledTrue(UserRole.DISPATCHER)
      .forEach(dispatcher -> notificationService.push(
        dispatcher.getId(),
        "WORKORDER",
        "新工单待接单",
        title,
        "/workorders"
      ));
  }

  private boolean isUnassigned(Map<String, Object> order) {
    Object assigneeId = order.get("assigneeId");
    if (assigneeId != null && !String.valueOf(assigneeId).isBlank()) {
      return false;
    }
    String assigneeName = order.get("assigneeName") != null ? String.valueOf(order.get("assigneeName")).trim() : "";
    if (assigneeName.isEmpty()) {
      return true;
    }
    return assigneeName.equals(String.valueOf(order.get("createdByName")));
  }

  private Map<String, Object> normalizeReview(Object rawReview) {
    if (!(rawReview instanceof Map<?, ?> review)) {
      throw ApiException.badRequest("提交复核时必须填写复核记录");
    }
    String conclusion = requiredText(review.get("conclusion"), "请选择复核结论", 32);
    if (!("RESOLVED".equals(conclusion) || "PARTIALLY_RESOLVED".equals(conclusion)
        || "UNRESOLVED".equals(conclusion) || "FALSE_ALARM".equals(conclusion))) {
      throw ApiException.badRequest("复核结论不合法");
    }
    String onsiteFinding = requiredText(review.get("onsiteFinding"), "请填写现场检查情况", 500);
    String handlingMeasures = requiredText(review.get("handlingMeasures"), "请填写处理措施与验证结果", 500);
    if (onsiteFinding.length() < 10 || handlingMeasures.length() < 10) {
      throw ApiException.badRequest("现场检查情况和处理措施至少填写 10 个字符");
    }
    String followUpPlan = optionalText(review.get("followUpPlan"), 500, "遗留风险与后续计划不能超过 500 个字符");
    if (("PARTIALLY_RESOLVED".equals(conclusion) || "UNRESOLVED".equals(conclusion)) && followUpPlan.isBlank()) {
      throw ApiException.badRequest("部分消缺或未消缺时必须填写遗留风险与后续计划");
    }

    Map<String, Object> normalized = new LinkedHashMap<>();
    normalized.put("conclusion", conclusion);
    normalized.put("onsiteFinding", onsiteFinding);
    normalized.put("handlingMeasures", handlingMeasures);
    if (!followUpPlan.isBlank()) {
      normalized.put("followUpPlan", followUpPlan);
    }
    normalized.put("submittedById", currentUser.get().getId());
    normalized.put("submittedByName", currentUser.get().getDisplayName());
    normalized.put("submittedAt", Instant.now().toString());
    return normalized;
  }

  private void normalizeLocationDescription(Map<String, Object> body) {
    if (!body.containsKey("locationDescription")) {
      return;
    }
    body.put("locationDescription", requiredText(body.get("locationDescription"), "具体地点不能为空", 200));
  }

  private boolean isSupportedStatus(String status) {
    return "PENDING".equals(status) || "PROCESSING".equals(status) || "REVIEW".equals(status)
      || "CLOSED".equals(status) || "CANCELLED".equals(status);
  }

  private boolean isAllowedTransition(String from, String to) {
    return ("PENDING".equals(from) && "PROCESSING".equals(to))
      || ("PROCESSING".equals(from) && "REVIEW".equals(to))
      || ("REVIEW".equals(from) && "CLOSED".equals(to));
  }

  private String requiredText(Object value, String message, int maxLength) {
    String normalized = optionalText(value, maxLength, message + "不能超过 " + maxLength + " 个字符");
    if (normalized.isBlank()) {
      throw ApiException.badRequest(message);
    }
    return normalized;
  }

  private String optionalText(Object value, int maxLength, String tooLongMessage) {
    String valueText = text(value);
    String normalized = valueText == null ? "" : valueText.trim();
    if (normalized.length() > maxLength) {
      throw ApiException.badRequest(tooLongMessage);
    }
    return normalized;
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }
}
