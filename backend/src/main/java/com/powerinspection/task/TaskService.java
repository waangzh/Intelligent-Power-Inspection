package com.powerinspection.task;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.model.LocateAnythingRequest;
import com.powerinspection.model.ModelServiceException;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.robot.RobotGateway;
import com.powerinspection.robot.RobotProgressSnapshot;
import com.powerinspection.robot.RobotProperties;
import com.powerinspection.route.RouteExecutorSupport;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TaskService {
  private static final List<String> ACTIVE_STATUSES = List.of("DISPATCHED", "RUNNING", "PAUSED", "MANUAL_TAKEOVER");
  private static final List<String> ROUTE_ALARM_TYPES = List.of("PERSON", "HELMET", "FIRE", "OBSTACLE");
  private static final Map<String, String> SEVERITY = Map.of(
    "PERSON", "MEDIUM",
    "HELMET", "HIGH",
    "OBSTACLE", "MEDIUM",
    "FIRE", "CRITICAL",
    "SWITCH", "HIGH",
    "METER", "LOW",
    "OIL_LEAK", "HIGH",
    "FOREIGN_OBJECT", "MEDIUM"
  );
  private static final Map<String, String> LABELS = Map.of(
    "PERSON", "人员检测",
    "HELMET", "安全帽检测",
    "OBSTACLE", "障碍物检测",
    "FIRE", "火源/烟雾检测",
    "SWITCH", "开关/刀闸状态",
    "METER", "表计/指示灯",
    "OIL_LEAK", "漏油检测",
    "FOREIGN_OBJECT", "异物检测"
  );

  private final DataStoreService dataStore;
  private final NotificationService notificationService;
  private final SimpMessagingTemplate messagingTemplate;
  private final RobotGateway robotGateway;
  private final LocateAnythingGateway locateAnythingGateway;
  private final RobotProperties robotProperties;
  private final Random random = new Random();

  public TaskService(DataStoreService dataStore, NotificationService notificationService, SimpMessagingTemplate messagingTemplate, RobotGateway robotGateway, LocateAnythingGateway locateAnythingGateway, RobotProperties robotProperties) {
    this.dataStore = dataStore;
    this.notificationService = notificationService;
    this.messagingTemplate = messagingTemplate;
    this.robotGateway = robotGateway;
    this.locateAnythingGateway = locateAnythingGateway;
    this.robotProperties = robotProperties;
  }

  public List<Map<String, Object>> tasks() {
    return dataStore.list(DataCategory.TASK);
  }

  public Map<String, Object> task(String id) {
    return dataStore.get(DataCategory.TASK, id);
  }

  public List<Map<String, Object>> records() {
    return dataStore.list(DataCategory.RECORD);
  }

  public List<Map<String, Object>> events(String taskId) {
    return dataStore.list(DataCategory.EVENT).stream().filter(event -> taskId.equals(text(event.get("taskId")))).toList();
  }

  @Transactional
  public Map<String, Object> createTask(Map<String, Object> body) {
    body.putIfAbsent("id", Ids.next("task"));
    body.putIfAbsent("robotId", robotProperties.getRobotId());
    body.putIfAbsent("status", "CREATED");
    body.putIfAbsent("progress", 0);
    body.putIfAbsent("currentCheckpointSeq", 0);
    body.putIfAbsent("createdAt", Instant.now().toString());
    validateTaskBinding(body);
    return saveTask(body);
  }

  @Transactional
  public Map<String, Object> updateTask(String id, Map<String, Object> body) {
    Map<String, Object> current = dataStore.get(DataCategory.TASK, id);
    if (ACTIVE_STATUSES.contains(text(current.get("status"))) && (body.containsKey("routeId") || body.containsKey("robotId"))) {
      throw ApiException.badRequest("任务执行中不能更换路线或机器人");
    }
    Map<String, Object> task = dataStore.patch(DataCategory.TASK, id, body);
    validateTaskBinding(task);
    messagingTemplate.convertAndSend("/topic/tasks/" + id, task);
    messagingTemplate.convertAndSend("/topic/tasks", task);
    return task;
  }

  @Transactional
  public void deleteTask(String id) {
    dataStore.delete(DataCategory.TASK, id);
    dataStore.deleteWhere(DataCategory.EVENT, "taskId", id);
  }

  @Transactional
  public Map<String, Object> dispatch(String id) {
    Map<String, Object> task = dataStore.get(DataCategory.TASK, id);
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
    String status = text(task.get("status"));
    if ("PAUSED".equals(status) || "MANUAL_TAKEOVER".equals(status) || "DISPATCHED".equals(status)) {
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
    if ("COMPLETED".equals(task.get("status")) || "CANCELLED".equals(task.get("status"))) {
      throw ApiException.badRequest("已结束任务不能重复取消");
    }
    Map<String, Object> robot = requireRobot(task);
    robotGateway.cancelTask(robot, task);
    task.put("status", "CANCELLED");
    task.put("completedAt", Instant.now().toString());
    saveTask(task);
    updateRobot(text(task.get("robotId")), map("status", "ONLINE", "currentTaskId", null));
    return task;
  }

  @Scheduled(fixedRate = 1500)
  @Transactional
  public void tick() {
    if (robotProperties.isHttpMode()) {
      return;
    }
    for (Map<String, Object> task : dataStore.list(DataCategory.TASK)) {
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
    updateRobot(text(task.get("robotId")), map("position", position, "status", "BUSY", "currentTaskId", taskId));

    List<Map<String, Object>> checkpoints = RouteExecutorSupport.compatibleCheckpoints(route);
    int newSeq = progress.currentCheckpointSeq();
    int oldSeq = number(task.get("currentCheckpointSeq"));
    if (newSeq > oldSeq && newSeq <= checkpoints.size()) {
      Map<String, Object> cp = checkpoints.get(newSeq - 1);
      String cpName = text(cp.get("name"));
      addEvent(taskId, "VOICE", "已到达指定位置，开始检查", cpName, null);
      addEvent(taskId, "ARRIVE", "到达检查点「" + cpName + "」", cpName, null);
      addEvent(taskId, "INSPECT", "云台调整 P" + cp.get("pan") + "° T" + cp.get("tilt") + "°，采集图像中", cpName, "https://picsum.photos/seed/" + cp.get("id") + "/400/240");
      String detectionImageUrl = "https://picsum.photos/seed/" + cp.get("id") + "_det/400/240";
      addEvent(taskId, "DETECT", "调用 LocateAnything 执行检查点级检测", cpName, detectionImageUrl);
      detectCheckpoint(task, route, cp, detectionImageUrl);
    }

    task.put("progress", nextProgress);
    task.put("currentCheckpointSeq", newSeq);
    if (nextProgress >= 100) {
      complete(task, route, checkpoints.size());
    } else {
      if (random.nextDouble() < 0.12) {
        maybeRouteAlarm(task, route);
      }
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
    long alarmCount = dataStore.list(DataCategory.ALARM).stream().filter(alarm -> taskId.equals(text(alarm.get("taskId")))).count();
    String routeName = text(route.getOrDefault("name", "-"));
    String robotName = robot == null ? "-" : text(robot.getOrDefault("name", "-"));
    String siteName = site == null ? "未知站点" : text(site.getOrDefault("name", "未知站点"));
    dataStore.upsert(DataCategory.RECORD, map(
      "id", Ids.next("record"),
      "taskId", taskId,
      "taskName", text(task.get("name")),
      "routeName", routeName,
      "robotName", robotName,
      "alarmCount", alarmCount,
      "checkpointCount", checkpointCount,
      "duration", "1 分钟",
      "summary", "完成 " + siteName + " 巡检，共 " + checkpointCount + " 个检查点，触发 " + alarmCount + " 条告警",
      "completedAt", now,
      "createdAt", now
    ));
  }

  private Map<String, Object> saveTask(Map<String, Object> task) {
    Map<String, Object> saved = dataStore.upsert(DataCategory.TASK, task);
    messagingTemplate.convertAndSend("/topic/tasks/" + saved.get("id"), saved);
    messagingTemplate.convertAndSend("/topic/tasks", saved);
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
    messagingTemplate.convertAndSend("/topic/robots/" + robotId, robot);
    messagingTemplate.convertAndSend("/topic/robots", robot);
  }

  private void addEvent(String taskId, String type, String message, String checkpointName, String imageUrl) {
    Map<String, Object> event = map("id", Ids.next("evt"), "taskId", taskId, "type", type, "message", message, "createdAt", Instant.now().toString());
    if (checkpointName != null) {
      event.put("checkpointName", checkpointName);
    }
    if (imageUrl != null) {
      event.put("imageUrl", imageUrl);
    }
    dataStore.upsert(DataCategory.EVENT, event);
    messagingTemplate.convertAndSend("/topic/tasks/" + taskId + "/events", event);
    messagingTemplate.convertAndSend("/topic/task-events", event);
  }

  private void maybeRouteAlarm(Map<String, Object> task, Map<String, Object> route) {
    String type = ROUTE_ALARM_TYPES.get(random.nextInt(ROUTE_ALARM_TYPES.size()));
    String message = switch (type) {
      case "PERSON" -> "路线行进中检测到未授权人员";
      case "HELMET" -> "检测到作业人员未佩戴安全帽";
      case "OBSTACLE" -> "前方检测到障碍物，机器人已减速避障";
      default -> "路线视野内检测到疑似火源/烟雾";
    };
    createAlarm(text(task.get("id")), text(route.get("name")), null, type, message, "https://picsum.photos/seed/" + System.currentTimeMillis() + "/400/240");
  }

  private void detectCheckpoint(Map<String, Object> task, Map<String, Object> route, Map<String, Object> cp, String imageUrl) {
    try {
      List<LocateAnythingFinding> findings = locateAnythingGateway.detectCheckpoint(
        new LocateAnythingRequest(task, route, cp, imageUrl, checkpointDetections(cp))
      );
      for (LocateAnythingFinding finding : findings) {
        createCheckpointAlarm(task, route, cp, finding);
      }
    } catch (ModelServiceException ex) {
      addEvent(text(task.get("id")), "DETECT_FAILED", "LocateAnything 检测失败：" + ex.getMessage(), text(cp.get("name")), imageUrl);
    }
  }

  private void createCheckpointAlarm(Map<String, Object> task, Map<String, Object> route, Map<String, Object> cp, LocateAnythingFinding finding) {
    String type = finding.type();
    String prompt = finding.prompt();
    String message = "检查点「" + cp.get("name") + "」" + LABELS.getOrDefault(type, type) + "异常" + (prompt == null ? "" : "（LocateAnything: " + prompt + "）");
    createAlarm(text(task.get("id")), text(route.get("name")), text(cp.get("name")), type, message, finding.imageUrl(), findingToMap(finding));
  }

  private void createAlarm(String taskId, String routeName, String checkpointName, String type, String message, String imageUrl) {
    createAlarm(taskId, routeName, checkpointName, type, message, imageUrl, null);
  }

  private void createAlarm(String taskId, String routeName, String checkpointName, String type, String message, String imageUrl, Map<String, Object> finding) {
    Map<String, Object> alarm = map("id", Ids.next("alarm"), "taskId", taskId, "routeName", routeName, "type", type, "severity", SEVERITY.getOrDefault(type, "MEDIUM"), "message", message, "imageUrl", imageUrl, "acknowledged", false, "createdAt", Instant.now().toString());
    if (checkpointName != null) {
      alarm.put("checkpointName", checkpointName);
    }
    if (finding != null) {
      alarm.put("finding", finding);
    }
    dataStore.upsert(DataCategory.ALARM, alarm);
    notificationService.pushToAll("ALARM", "新告警", message, "/alarms");
    messagingTemplate.convertAndSend("/topic/alarms", alarm);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> checkpointDetections(Map<String, Object> cp) {
    if (cp.get("detections") instanceof List<?> detections) {
      return (List<Map<String, Object>>) detections;
    }
    return List.of();
  }

  private Map<String, Object> findingToMap(LocateAnythingFinding finding) {
    Map<String, Object> item = map(
      "type", finding.type(),
      "prompt", finding.prompt(),
      "score", finding.score(),
      "bbox", finding.bbox(),
      "label", finding.label(),
      "imageUrl", finding.imageUrl()
    );
    if (finding.rawResult() != null) {
      item.put("rawResult", finding.rawResult());
    }
    return item;
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
    boolean occupied = dataStore.list(DataCategory.TASK).stream()
      .anyMatch(task -> taskId.equals(text(task.get("id"))) ? false
        : text(robot.get("id")).equals(text(task.get("robotId"))) && ACTIVE_STATUSES.contains(text(task.get("status"))));
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
    if (routeSiteId != null && robotSiteId != null && !routeSiteId.equals(robotSiteId)) {
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
