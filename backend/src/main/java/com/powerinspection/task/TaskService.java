package com.powerinspection.task;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.common.ListQuery;
import com.powerinspection.common.PageResult;
import com.powerinspection.common.ResourceChangeEvent;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.detection.DetectionRunService;
import com.powerinspection.model.DetectionItems;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.model.LocateAnythingRequest;
import com.powerinspection.model.LocateAnythingResult;
import com.powerinspection.model.ModelServiceException;
import com.powerinspection.robot.RobotGateway;
import com.powerinspection.robot.RobotInspectionImage;
import com.powerinspection.robot.RobotProgressSnapshot;
import com.powerinspection.robot.RobotProperties;
import com.powerinspection.route.RouteExecutorSupport;
import com.powerinspection.route.RouteRevisionEntity;
import com.powerinspection.route.RouteRevisionService;
import com.powerinspection.user.UserEntity;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
  private static final List<String> ACTIVE_STATUSES =
      List.of("DISPATCHED", "RUNNING", "PAUSED", "MANUAL_TAKEOVER");
  private static final Set<String> DELETABLE_STATUSES =
      Set.of("CREATED", "COMPLETED", "CANCELLED", "ESTOPPED");
  private static final Set<String> SERVER_MANAGED_FIELDS =
      Set.of(
          "status",
          "progress",
          "currentCheckpointSeq",
          "startedAt",
          "completedAt",
          "createdAt",
          "updatedAt",
          "activeRobotKey",
          "executionId",
          "routeContentSha256",
          "mapImageSha256",
          "siteId",
          "emergencyStopReason",
          "emergencyStoppedBy",
          "deploymentId");
  private static final Set<String> CLIENT_CREATABLE_FIELDS =
      Set.of("id", "name", "routeId", "robotId", "routeRevisionId", "note");
  private static final Set<String> REVISION_MANAGED_FIELDS =
      Set.of(
          "routeRevisionId",
          "routeId",
          "robotId",
          "executionId",
          "routeContentSha256",
          "mapImageSha256",
          "status");
  private final DataStoreService dataStore;
  private final SimpMessagingTemplate messagingTemplate;
  private final RobotGateway robotGateway;
  private final LocateAnythingGateway locateAnythingGateway;
  private final RouteRevisionService routeRevisionService;
  private final TaskExecutionService taskExecutionService;
  private final DetectionRunService detectionRunService;
  private final RobotProperties robotProperties;

  public TaskService(
      DataStoreService dataStore,
      SimpMessagingTemplate messagingTemplate,
      RobotGateway robotGateway,
      LocateAnythingGateway locateAnythingGateway,
      RouteRevisionService routeRevisionService,
      TaskExecutionService taskExecutionService,
      RobotProperties robotProperties,
      DetectionRunService detectionRunService) {
    this.dataStore = dataStore;
    this.messagingTemplate = messagingTemplate;
    this.robotGateway = robotGateway;
    this.locateAnythingGateway = locateAnythingGateway;
    this.routeRevisionService = routeRevisionService;
    this.taskExecutionService = taskExecutionService;
    this.robotProperties = robotProperties;
    this.detectionRunService = detectionRunService;
  }

  public List<Map<String, Object>> tasks() {
    return dataStore.list(DataCategory.TASK);
  }

  public PageResult<Map<String, Object>> tasks(ListQuery query) {
    return dataStore.page(
        DataCategory.TASK,
        query.getPage(),
        query.getSize(),
        query.getSort(),
        query.getDirection(),
        query.getUpdatedAfter(),
        query.getQ(),
        query.filters("siteId", "routeId", "robotId", "status"));
  }

  public Map<String, Object> task(String id) {
    return dataStore.get(DataCategory.TASK, id);
  }

  public List<Map<String, Object>> records() {
    return dataStore.list(DataCategory.RECORD);
  }

  public List<Map<String, Object>> events(String taskId) {
    return dataStore.list(DataCategory.EVENT).stream()
        .filter(event -> taskId.equals(text(event.get("taskId"))))
        .toList();
  }

  public PageResult<Map<String, Object>> events(String taskId, ListQuery query) {
    query.setTaskId(taskId);
    return dataStore.page(
        DataCategory.EVENT,
        query.getPage(),
        query.getSize(),
        query.getSort(),
        query.getDirection(),
        query.getUpdatedAfter(),
        query.getQ(),
        query.filters("taskId", "type"));
  }

  public Map<String, Object> event(String eventId) {
    return dataStore.get(DataCategory.EVENT, eventId);
  }

  @Transactional
  public Map<String, Object> createTask(Map<String, Object> body) {
    Map<String, Object> payload = sanitizeCreatePayload(body);
    if (text(payload.get("name")) == null || text(payload.get("name")).isBlank()) {
      throw ApiException.badRequest("请填写任务名称");
    }
    String taskId = text(payload.get("id"));
    if (taskId == null || taskId.isBlank()) {
      payload.put("id", Ids.next("task"));
    } else {
      requireFreshTaskId(taskId);
      payload.put("id", taskId);
    }
    payload.put("status", "CREATED");
    payload.put("progress", 0);
    payload.put("currentCheckpointSeq", 0);
    payload.put("createdAt", Instant.now().toString());
    RouteRevisionEntity revision = attachRouteRevision(payload);
    validateTaskBinding(payload);
    if (revision != null) {
      TaskExecutionEntity execution = taskExecutionService.bind(payload, revision);
      payload.put("executionId", execution.getExecutionId());
      payload.put("routeContentSha256", execution.getRouteContentSha256());
      payload.put("mapImageSha256", execution.getMapImageSha256());
    }
    return saveTask(payload);
  }

  @Transactional
  public Map<String, Object> updateTask(String id, Map<String, Object> body) {
    Map<String, Object> current = dataStore.get(DataCategory.TASK, id);
    Map<String, Object> patch = sanitizeUpdatePayload(current, body);
    if (ACTIVE_STATUSES.contains(text(current.get("status")))
        && (patch.containsKey("routeId") || patch.containsKey("robotId"))) {
      throw ApiException.badRequest("任务执行中不能更换路线或机器人");
    }
    Map<String, Object> task = dataStore.patch(DataCategory.TASK, id, patch);
    validateTaskBinding(task);
    publishChange("task", id, "/topic/tasks/" + id, "/topic/tasks");
    return task;
  }

  @Transactional
  public void deleteTask(String id) {
    Map<String, Object> task = dataStore.get(DataCategory.TASK, id);
    requireDeletable(task);
    dataStore.deleteWhere(DataCategory.EVENT, "taskId", id);
    taskExecutionService.delete(id);
    dataStore.delete(DataCategory.TASK, id);
  }

  @Transactional
  public Map<String, Object> dispatch(String id) {
    Map<String, Object> task = dataStore.get(DataCategory.TASK, id);
    requireSimulationTask(task);
    requireStatus(task, "CREATED");
    Map<String, Object> route = requireRoute(task);
    Map<String, Object> robot = requireRobot(task);
    validateRouteReady(route);
    validateRobotCanRun(robot, id);
    validateRouteAndRobotSite(route, robot);
    robotGateway.dispatchTask(robot, task, route);
    task.put("status", "DISPATCHED");
    task.putIfAbsent("startedAt", Instant.now().toString());
    saveTask(task);
    updateRobot(text(task.get("robotId")), map("status", "BUSY", "currentTaskId", id));
    addEvent(id, "DISPATCH", "任务已下发至机器人", null, null);
    return task;
  }

  @Transactional
  public Map<String, Object> pause(String id) {
    Map<String, Object> task = dataStore.get(DataCategory.TASK, id);
    requireSimulationTask(task);
    requireStatus(task, "RUNNING");
    Map<String, Object> robot = requireRobot(task);
    robotGateway.pauseTask(robot, task);
    task.put("status", "PAUSED");
    saveTask(task);
    addEvent(id, "PAUSE", "任务已暂停", null, null);
    return task;
  }

  @Transactional
  public Map<String, Object> resume(String id) {
    Map<String, Object> task = dataStore.get(DataCategory.TASK, id);
    requireSimulationTask(task);
    String status = text(task.get("status"));
    if ("PAUSED".equals(status)
        || "MANUAL_TAKEOVER".equals(status)
        || "DISPATCHED".equals(status)) {
      Map<String, Object> robot = requireRobot(task);
      if ("OFFLINE".equals(text(robot.get("status")))) {
        throw ApiException.badRequest("机器人离线，无法恢复任务");
      }
      robotGateway.resumeTask(robot, task);
      task.put("status", "RUNNING");
      task.putIfAbsent("startedAt", Instant.now().toString());
      saveTask(task);
      updateRobot(text(task.get("robotId")), map("status", "BUSY", "currentTaskId", id));
      addEvent(id, "RESUME", "任务开始执行，路线级检测已启动", null, null);
    } else {
      throw ApiException.badRequest("当前状态不能恢复任务");
    }
    return task;
  }

  @Transactional
  public Map<String, Object> takeover(String id) {
    Map<String, Object> task = dataStore.get(DataCategory.TASK, id);
    requireSimulationTask(task);
    requireStatus(task, "RUNNING");
    Map<String, Object> robot = requireRobot(task);
    robotGateway.takeoverTask(robot, task);
    task.put("status", "MANUAL_TAKEOVER");
    saveTask(task);
    addEvent(id, "PAUSE", "调度员已人工接管机器人", null, null);
    return task;
  }

  @Transactional
  public Map<String, Object> cancel(String id) {
    Map<String, Object> task = dataStore.get(DataCategory.TASK, id);
    requireSimulationTask(task);
    if ("COMPLETED".equals(task.get("status"))
        || "CANCELLED".equals(task.get("status"))
        || "ESTOPPED".equals(task.get("status"))) {
      throw ApiException.badRequest("已结束任务不能重复取消");
    }
    Map<String, Object> robot = requireRobot(task);
    robotGateway.cancelTask(robot, task);
    task.put("status", "CANCELLED");
    task.put("completedAt", Instant.now().toString());
    saveTask(task);
    updateRobot(text(task.get("robotId")), map("status", "ONLINE", "currentTaskId", null));
    addEvent(id, "PAUSE", "任务已取消", null, null);
    return task;
  }

  @Transactional
  public Map<String, Object> emergencyStop(String id, String reason, UserEntity operator) {
    Map<String, Object> task = dataStore.get(DataCategory.TASK, id);
    requireSimulationTask(task);
    String status = text(task.get("status"));
    if ("COMPLETED".equals(status) || "CANCELLED".equals(status) || "ESTOPPED".equals(status)) {
      throw ApiException.badRequest("已结束任务不能远程急停");
    }
    if (!List.of("DISPATCHED", "RUNNING", "PAUSED", "MANUAL_TAKEOVER", "STARTING").contains(status)
        && !"CREATED".equals(status)) {
      throw ApiException.badRequest("当前状态不允许远程急停");
    }
    Map<String, Object> robot = requireRobot(task);
    robotGateway.emergencyStopTask(robot, task);
    String operatorName =
        operator == null
            ? "系统"
            : (operator.getDisplayName() == null || operator.getDisplayName().isBlank()
                ? operator.getUsername()
                : operator.getDisplayName());
    task.put("status", "ESTOPPED");
    task.put("completedAt", Instant.now().toString());
    task.put("emergencyStopReason", reason);
    task.put("emergencyStoppedBy", operatorName);
    saveTask(task);
    updateRobot(text(task.get("robotId")), map("status", "ONLINE", "currentTaskId", null));
    addEvent(id, "ESTOP", "远程急停已执行。原因：" + reason + "（操作人：" + operatorName + "）", null, null);
    return task;
  }

  public void recordAuditEvent(String taskId, String type, String message) {
    addEvent(taskId, type, message, null, null);
  }

  @Scheduled(fixedRate = 1500)
  @Transactional
  public void tick() {
    for (Map<String, Object> task : dataStore.list(DataCategory.TASK)) {
      if (hasRouteRevision(task)) {
        continue;
      }
      String status = text(task.get("status"));
      if ("DISPATCHED".equals(status)) {
        resume(text(task.get("id")));
      } else if ("RUNNING".equals(status)) {
        advance(task);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void advance(Map<String, Object> task) {
    String taskId = text(task.get("id"));
    Map<String, Object> route = dataStore.find(DataCategory.ROUTE, text(task.get("routeId")));
    if (route == null) {
      return;
    }
    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, text(task.get("robotId")));
    if (robot == null) {
      return;
    }
    RobotProgressSnapshot progress = robotGateway.advanceTask(robot, task, route);
    int nextProgress = progress.progress();
    Map<String, Object> position = progress.position();
    updateRobot(
        text(task.get("robotId")),
        map("position", position, "status", "BUSY", "currentTaskId", taskId));

    List<Map<String, Object>> checkpoints = RouteExecutorSupport.compatibleCheckpoints(route);
    int newSeq = progress.currentCheckpointSeq();
    int oldSeq = number(task.get("currentCheckpointSeq"));
    if (newSeq > oldSeq && newSeq <= checkpoints.size()) {
      Map<String, Object> cp = checkpoints.get(newSeq - 1);
      String cpName = text(cp.get("name"));
      addEvent(taskId, "VOICE", "已到达指定位置，开始检查", cpName, null);
      addEvent(taskId, "ARRIVE", "到达检查点「" + cpName + "」", cpName, null);
      RobotInspectionImage inspectionImage = progress.inspectionImage();
      if (inspectionImage == null) {
        addEvent(taskId, "DETECT_SKIPPED", "机器人未返回真实巡检图像，已跳过视觉检测", cpName, null);
      } else {
        addEvent(
            taskId,
            "INSPECT",
            "云台调整 P" + cp.get("pan") + "° T" + cp.get("tilt") + "°，已采集真实图像",
            cpName,
            inspectionImage.url());
        addEvent(taskId, "DETECT", "调用 LocateAnything 执行检查点级检测", cpName, inspectionImage.url());
        detectCheckpoint(task, route, cp, inspectionImage);
      }
    }

    task.put("progress", nextProgress);
    task.put("currentCheckpointSeq", newSeq);
    if (nextProgress >= 100) {
      complete(task, route, checkpoints.size());
    } else {
      saveTask(task);
    }
  }

  private void complete(Map<String, Object> task, Map<String, Object> route, int checkpointCount) {
    String now = Instant.now().toString();
    String taskId = text(task.get("id"));
    task.put("status", "COMPLETED");
    task.put("progress", 100);
    task.put("completedAt", now);
    saveTask(task);
    updateRobot(text(task.get("robotId")), map("status", "ONLINE", "currentTaskId", null));
    addEvent(taskId, "COMPLETE", "巡检任务已全部完成", null, null);

    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, text(task.get("robotId")));
    Map<String, Object> site = dataStore.find(DataCategory.SITE, text(route.get("siteId")));
    long alarmCount =
        dataStore.list(DataCategory.ALARM).stream()
            .filter(alarm -> taskId.equals(text(alarm.get("taskId"))))
            .count();
    String routeName = text(route.getOrDefault("name", "-"));
    String robotName = robot == null ? "-" : text(robot.getOrDefault("name", "-"));
    String siteName = site == null ? "未知站点" : text(site.getOrDefault("name", "未知站点"));
    dataStore.upsert(
        DataCategory.RECORD,
        map(
            "id",
            Ids.next("record"),
            "taskId",
            taskId,
            "siteId",
            text(route.get("siteId")),
            "routeId",
            text(route.get("id")),
            "robotId",
            text(task.get("robotId")),
            "taskName",
            text(task.get("name")),
            "routeName",
            routeName,
            "robotName",
            robotName,
            "alarmCount",
            alarmCount,
            "checkpointCount",
            checkpointCount,
            "duration",
            "1 分钟",
            "summary",
            "完成 " + siteName + " 巡检，共 " + checkpointCount + " 个检查点，触发 " + alarmCount + " 条告警",
            "completedAt",
            now,
            "createdAt",
            now));
  }

  private Map<String, Object> saveTask(Map<String, Object> task) {
    Map<String, Object> saved = dataStore.upsert(DataCategory.TASK, task);
    publishChange("task", saved.get("id"), "/topic/tasks/" + saved.get("id"), "/topic/tasks");
    return saved;
  }

  private void updateRobot(String robotId, Map<String, Object> patch) {
    if (robotId == null) {
      return;
    }
    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, robotId);
    if (robot == null) {
      return;
    }
    robot.putAll(patch);
    robot.put("id", robotId);
    dataStore.upsert(DataCategory.ROBOT, robot);
    publishChange("robot", robotId, "/topic/robots/" + robotId, "/topic/robots");
  }

  private void addEvent(
      String taskId, String type, String message, String checkpointName, String imageUrl) {
    Map<String, Object> event =
        map(
            "id",
            Ids.next("evt"),
            "taskId",
            taskId,
            "type",
            type,
            "message",
            message,
            "createdAt",
            Instant.now().toString());
    if (checkpointName != null) {
      event.put("checkpointName", checkpointName);
    }
    if (imageUrl != null) {
      event.put("imageUrl", imageUrl);
    }
    dataStore.upsert(DataCategory.EVENT, event);
    publishChange("taskEvent", taskId, "/topic/tasks/" + taskId + "/events", "/topic/task-events");
  }

  private void detectCheckpoint(
      Map<String, Object> task,
      Map<String, Object> route,
      Map<String, Object> cp,
      RobotInspectionImage image) {
    try {
      List<Map<String, Object>> detections = checkpointDetections(cp);
      LocateAnythingResult result =
          locateAnythingGateway.detectCheckpoint(
              new LocateAnythingRequest(
                  task,
                  route,
                  cp,
                  image.url(),
                  image.width(),
                  image.height(),
                  detections));
      for (String warning : result.warnings()) {
        addEvent(
            text(task.get("id")),
            "DETECT_WARNING",
            "LocateAnything 警告：" + warning,
            text(cp.get("name")),
            image.url());
      }
      detectionRunService.recordTaskResult(
          task, route, cp, image, detections, result);
    } catch (ModelServiceException ex) {
      addEvent(
          text(task.get("id")),
          "DETECT_FAILED",
          "LocateAnything 检测失败：" + ex.getMessage(),
          text(cp.get("name")),
          image.url());
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> checkpointDetections(Map<String, Object> cp) {
    if (cp.get("detections") instanceof List<?> detections) {
      return DetectionItems.enabled((List<Map<String, Object>>) detections);
    }
    return List.of();
  }

  public Map<String, Object> activeTask() {
    return dataStore.list(DataCategory.TASK).stream()
        .filter(task -> ACTIVE_STATUSES.contains(text(task.get("status"))))
        .findFirst()
        .orElse(null);
  }

  private void validateTaskBinding(Map<String, Object> task) {
    Map<String, Object> route = requireRoute(task);
    Map<String, Object> robot = requireRobot(task);
    validateRouteAndRobotSite(route, robot);
    String routeSiteId = text(route.get("siteId"));
    if (routeSiteId != null && !routeSiteId.isBlank()) {
      task.put("siteId", routeSiteId);
    } else {
      task.remove("siteId");
    }
  }

  private void requireFreshTaskId(String taskId) {
    if (taskId.length() > 64 || !taskId.matches("[A-Za-z0-9_-]+")) {
      throw ApiException.badRequest("任务 id 不合法");
    }
    if (dataStore.exists(DataCategory.TASK, taskId)) {
      throw ApiException.conflict("任务已存在，不能通过创建接口覆盖");
    }
  }

  private RouteRevisionEntity attachRouteRevision(Map<String, Object> task) {
    String revisionId = text(task.get("routeRevisionId"));
    if (revisionId == null || revisionId.isBlank()) {
      if (robotProperties.isBridgeMode()) {
        throw ApiException.badRequest("Bridge 模式创建任务必须提供 routeRevisionId");
      }
      return null;
    }
    RouteRevisionEntity revision = routeRevisionService.require(revisionId);
    String requestedRouteId = text(task.get("routeId"));
    if (requestedRouteId != null && !requestedRouteId.equals(revision.getRouteId())) {
      throw ApiException.badRequest("routeId 与 routeRevisionId 不一致");
    }
    task.put("routeId", revision.getRouteId());
    task.put("routeContentSha256", revision.getContentSha256());
    task.put("mapImageSha256", revision.getMapImageSha256());
    return revision;
  }

  private Map<String, Object> sanitizeCreatePayload(Map<String, Object> body) {
    if (body == null || body.isEmpty()) {
      throw ApiException.badRequest("请提供任务创建参数");
    }
    rejectServerManagedFields(body, "创建");
    rejectUnknownFields(body, CLIENT_CREATABLE_FIELDS, "创建");
    Map<String, Object> payload = new LinkedHashMap<>();
    for (String field : CLIENT_CREATABLE_FIELDS) {
      if (body.containsKey(field)) {
        payload.put(field, body.get(field));
      }
    }
    return payload;
  }

  private Map<String, Object> sanitizeUpdatePayload(
      Map<String, Object> current, Map<String, Object> body) {
    if (body == null || body.isEmpty()) {
      throw ApiException.badRequest("请提供任务更新参数");
    }
    // version 仅用于乐观锁，不是业务状态字段。
    Map<String, Object> withoutVersion = new LinkedHashMap<>(body);
    Object version = withoutVersion.remove("version");
    rejectServerManagedFields(withoutVersion, "更新");
    if (hasRouteRevision(current)) {
      String managedField =
          withoutVersion.keySet().stream()
              .filter(REVISION_MANAGED_FIELDS::contains)
              .findFirst()
              .orElse(null);
      if (managedField != null) {
        throw ApiException.badRequest("已绑定路线修订的任务不能通过通用更新接口修改 " + managedField);
      }
    }
    Set<String> allowed = allowedUpdateFields(current);
    rejectUnknownFields(withoutVersion, allowed, "更新");
    Map<String, Object> patch = new LinkedHashMap<>();
    for (String field : allowed) {
      if (withoutVersion.containsKey(field)) {
        patch.put(field, withoutVersion.get(field));
      }
    }
    if (version != null) {
      patch.put("version", version);
    }
    return patch;
  }

  private Set<String> allowedUpdateFields(Map<String, Object> current) {
    Set<String> allowed = new java.util.LinkedHashSet<>(Set.of("name", "note"));
    if ("CREATED".equals(text(current.get("status"))) && !hasRouteRevision(current)) {
      allowed.add("routeId");
      allowed.add("robotId");
    }
    return allowed;
  }

  private void requireDeletable(Map<String, Object> task) {
    String status = text(task.get("status"));
    if (!DELETABLE_STATUSES.contains(status)) {
      throw ApiException.badRequest("当前状态不允许删除任务");
    }
    taskExecutionService.requireDeletable(text(task.get("id")));
  }

  private static void rejectServerManagedFields(Map<String, Object> body, String action) {
    String managedField =
        body.keySet().stream().filter(SERVER_MANAGED_FIELDS::contains).findFirst().orElse(null);
    if (managedField != null) {
      throw ApiException.badRequest(action + "任务不能指定服务端字段 " + managedField);
    }
  }

  private static void rejectUnknownFields(
      Map<String, Object> body, Set<String> allowed, String action) {
    String unknown =
        body.keySet().stream().filter(field -> !allowed.contains(field)).findFirst().orElse(null);
    if (unknown != null) {
      throw ApiException.badRequest(action + "任务不支持字段 " + unknown);
    }
  }

  private boolean hasRouteRevision(Map<String, Object> task) {
    String revisionId = text(task.get("routeRevisionId"));
    return revisionId != null && !revisionId.isBlank();
  }

  private void requireSimulationTask(Map<String, Object> task) {
    if (hasRouteRevision(task)) {
      throw ApiException.conflict("真实路线修订任务必须通过 Robot Bridge 接口控制，当前平台仅支持创建不可变任务绑定");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> requireRoute(Map<String, Object> task) {
    String routeId = text(task.get("routeId"));
    if (routeId == null || routeId.isBlank()) {
      throw ApiException.badRequest("请选择巡检路线");
    }
    Map<String, Object> route = dataStore.find(DataCategory.ROUTE, routeId);
    if (route == null) {
      throw ApiException.badRequest("巡检路线不存在");
    }
    return route;
  }

  private Map<String, Object> requireRobot(Map<String, Object> task) {
    String robotId = text(task.get("robotId"));
    if (robotId == null || robotId.isBlank()) {
      throw ApiException.badRequest("请选择机器人");
    }
    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, robotId);
    if (robot == null) {
      throw ApiException.badRequest("机器人不存在");
    }
    return robot;
  }

  @SuppressWarnings("unchecked")
  private void validateRobotCanRun(Map<String, Object> robot, String taskId) {
    String status = text(robot.get("status"));
    if (!"ONLINE".equals(status)) {
      throw ApiException.badRequest("机器人当前不可下发任务");
    }
    String currentTaskId = text(robot.get("currentTaskId"));
    if (currentTaskId != null && !currentTaskId.isBlank() && !taskId.equals(currentTaskId)) {
      throw ApiException.badRequest("机器人已有执行中的任务");
    }
    boolean occupied =
        dataStore.list(DataCategory.TASK).stream()
            .anyMatch(
                task ->
                    taskId.equals(text(task.get("id")))
                        ? false
                        : text(robot.get("id")).equals(text(task.get("robotId")))
                            && ACTIVE_STATUSES.contains(text(task.get("status"))));
    if (occupied) {
      throw ApiException.badRequest("机器人已有执行中的任务");
    }
  }

  private void validateRouteReady(Map<String, Object> route) {
    if (RouteExecutorSupport.hasExecutorTargets(route)) {
      return;
    }
    List<Map<String, Object>> path = RouteExecutorSupport.compatiblePath(route);
    if (path.isEmpty()) {
      throw ApiException.badRequest("Route has no path or executor targets");
    }
  }

  private void validateRouteAndRobotSite(Map<String, Object> route, Map<String, Object> robot) {
    String routeSiteId = text(route.get("siteId"));
    String robotSiteId = text(robot.get("siteId"));
    if (routeSiteId == null || robotSiteId == null || !routeSiteId.equals(robotSiteId)) {
      throw ApiException.badRequest("机器人与巡检路线不属于同一站点");
    }
  }

  private void requireStatus(Map<String, Object> task, String... allowedStatuses) {
    String status = text(task.get("status"));
    for (String allowed : allowedStatuses) {
      if (allowed.equals(status)) {
        return;
      }
    }
    throw ApiException.badRequest("当前任务状态不允许执行该操作");
  }

  private int number(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException ex) {
      throw ApiException.badRequest("数字格式错误");
    }
  }

  private void publishChange(
      String resource, Object resourceId, String detailTopic, String collectionTopic) {
    ResourceChangeEvent event = ResourceChangeEvent.updated(resource, resourceId);
    messagingTemplate.convertAndSend(detailTopic, event);
    messagingTemplate.convertAndSend(collectionTopic, event);
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> item = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      item.put(values[i].toString(), values[i + 1]);
    }
    return item;
  }
}
