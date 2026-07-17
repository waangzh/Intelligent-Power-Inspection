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
import java.util.Set;
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
  private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

  /** 允许的状态流转及触发该流转所需的最低权限；PENDING -> PROCESSING 必须通过 /claim 完成，此处不登记。 */
  private static final Map<String, Permission> STATUS_TRANSITIONS = Map.of(
    "PROCESSING->REVIEW", Permission.WORKORDER_PROCESS,
    "REVIEW->CLOSED", Permission.WORKORDER_REVIEW,
    "REVIEW->PROCESSING", Permission.WORKORDER_REVIEW,
    "PENDING->CANCELLED", Permission.WORKORDER_REVIEW,
    "PROCESSING->CANCELLED", Permission.WORKORDER_REVIEW
  );

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
    UserEntity user = currentUser.get();
    permissionService.require(user, Permission.WORKORDER_VIEW);
    if (user.getRole() != UserRole.DISPATCHER) {
      return ApiResponse.ok(dataStore.page(
        DataCategory.WORK_ORDER, query.getPage(), query.getSize(), query.getSort(), query.getDirection(),
        query.getUpdatedAfter(), query.getQ(), query.filters("status", "siteId", "type")
      ));
    }
    Map<String, String> filters = new LinkedHashMap<>(query.filters("status", "siteId", "type"));
    filters.put("_viewerId", user.getId());
    filters.put("_viewerName", user.getDisplayName());
    return ApiResponse.ok(dataStore.page(
      DataCategory.WORK_ORDER, query.getPage(), query.getSize(), query.getSort(), query.getDirection(),
      query.getUpdatedAfter(), query.getQ(), filters
    ));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> order(@PathVariable String id) {
    UserEntity user = currentUser.get();
    permissionService.require(user, Permission.WORKORDER_VIEW);
    Map<String, Object> order = dataStore.get(DataCategory.WORK_ORDER, id);
    if (!canView(order, user)) {
      throw ApiException.forbidden("无权查看该工单");
    }
    return ApiResponse.ok(order);
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    UserEntity user = currentUser.get();
    permissionService.require(user, Permission.WORKORDER_CREATE);
    // 仅接受这些字段，其余字段（如 status、assigneeId、createdById 等）必须由服务端赋值，避免客户端批量赋值伪造身份或状态。
    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", Ids.next("wo"));
    order.put("title", requiredText(body.get("title"), "请填写工单标题", 100));
    order.put("description", optionalText(body.get("description"), 1000, "工单描述不能超过 1000 个字符"));
    order.put("priority", normalizePriority(body.get("priority")));
    String locationDescription = optionalText(body.get("locationDescription"), 200, "具体地点不能超过 200 个字符");
    if (!locationDescription.isBlank()) {
      order.put("locationDescription", locationDescription);
    }
    order.put("status", "PENDING");
    order.put("createdById", user.getId());
    order.put("createdByName", user.getDisplayName());
    String now = Instant.now().toString();
    order.put("createdAt", now);
    order.put("updatedAt", now);
    Map<String, Object> saved = dataStore.upsert(DataCategory.WORK_ORDER, order);
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
    UserEntity user = currentUser.get();
    permissionService.require(user, Permission.WORKORDER_PROCESS);
    Map<String, Object> order = dataStore.get(DataCategory.WORK_ORDER, id);
    requireAssignee(order, user);
    // 仅允许修改工单本身的描述信息；状态、负责人、创建人、来源、告警关联等字段一律不可通过该接口变更。
    Map<String, Object> patch = new LinkedHashMap<>();
    if (body.containsKey("title")) {
      patch.put("title", requiredText(body.get("title"), "请填写工单标题", 100));
    }
    if (body.containsKey("description")) {
      patch.put("description", optionalText(body.get("description"), 1000, "工单描述不能超过 1000 个字符"));
    }
    if (body.containsKey("priority")) {
      patch.put("priority", normalizePriority(body.get("priority")));
    }
    if (body.containsKey("locationDescription")) {
      patch.put("locationDescription", requiredText(body.get("locationDescription"), "具体地点不能为空", 200));
    }
    if (patch.isEmpty()) {
      throw ApiException.badRequest("没有可更新的字段");
    }
    patch.put("updatedAt", Instant.now().toString());
    return ApiResponse.ok(dataStore.patch(DataCategory.WORK_ORDER, id, patch));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.WORKORDER_CREATE);
    dataStore.delete(DataCategory.WORK_ORDER, id);
    return ApiResponse.ok();
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<Map<String, Object>> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> body) {
    UserEntity user = currentUser.get();
    Map<String, Object> order = dataStore.get(DataCategory.WORK_ORDER, id);
    String from = text(order.get("status"));
    String status = text(body.get("status"));
    if (!isSupportedStatus(status)) {
      throw ApiException.badRequest("不支持的工单状态");
    }
    // PENDING -> PROCESSING 只能通过 /claim 完成，确保负责人字段一定会被写入，不允许在此接口被绕过。
    String transitionKey = from + "->" + status;
    Permission requiredPermission = STATUS_TRANSITIONS.get(transitionKey);
    if (requiredPermission == null) {
      throw ApiException.badRequest("工单状态流转不合法");
    }
    permissionService.require(user, requiredPermission);
    if ("PROCESSING->REVIEW".equals(transitionKey)) {
      // 只有接单的调度员本人才能提交复核，避免其他调度员越权处理不属于自己的工单。
      requireAssignee(order, user);
    }
    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("status", status);
    patch.put("updatedAt", Instant.now().toString());
    if ("REVIEW".equals(status)) {
      Map<String, Object> review = normalizeReview(body.get("review"));
      patch.put("review", review);
      patch.put("resolution", review.get("handlingMeasures"));
    } else if (body.get("resolution") != null) {
      patch.put("resolution", text(body.get("resolution")));
    }
    if ("CLOSED".equals(status)) {
      patch.put("closedAt", Instant.now().toString());
    }
    if ("CANCELLED".equals(status)) {
      patch.put("cancelledAt", Instant.now().toString());
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

  private boolean canView(Map<String, Object> order, UserEntity user) {
    if (user.getRole() != UserRole.DISPATCHER) {
      return true;
    }
    String assigneeId = text(order.get("assigneeId"));
    if (user.getId().equals(assigneeId)) {
      return true;
    }
    String assigneeName = text(order.get("assigneeName"));
    return (assigneeId == null || assigneeId.isBlank())
      && (user.getDisplayName().equals(assigneeName) || isUnassigned(order));
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

  private boolean isSupportedStatus(String status) {
    return "PENDING".equals(status) || "PROCESSING".equals(status) || "REVIEW".equals(status)
      || "CLOSED".equals(status) || "CANCELLED".equals(status);
  }

  private String normalizePriority(Object value) {
    String priority = text(value);
    if (priority == null || priority.isBlank()) {
      return "MEDIUM";
    }
    priority = priority.trim().toUpperCase();
    if (!PRIORITIES.contains(priority)) {
      throw ApiException.badRequest("不支持的优先级");
    }
    return priority;
  }

  /** 只有该工单的接单调度员本人可以继续修改或推进工单，防止其他调度员越权操作。 */
  private void requireAssignee(Map<String, Object> order, UserEntity user) {
    Object assigneeId = order.get("assigneeId");
    if (assigneeId == null || !String.valueOf(assigneeId).equals(user.getId())) {
      throw ApiException.forbidden("仅接单调度员可操作该工单");
    }
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
