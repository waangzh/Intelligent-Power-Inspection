package com.powerinspection.data;

import com.powerinspection.alarm.AlarmService;
import com.powerinspection.auth.AuthService;
import com.powerinspection.common.Ids;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserPreferenceEntity;
import com.powerinspection.user.UserPreferenceRepository;
import com.powerinspection.user.UserRepository;
import com.powerinspection.user.UserRole;
import com.powerinspection.robot.RobotProperties;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SeedDataInitializer implements ApplicationRunner {
  private final UserRepository userRepository;
  private final UserPreferenceRepository preferenceRepository;
  private final PasswordEncoder passwordEncoder;
  private final DataStoreService dataStore;
  private final AuthService authService;
  private final RobotProperties robotProperties;
  private final AlarmService alarmService;

  public SeedDataInitializer(
    UserRepository userRepository,
    UserPreferenceRepository preferenceRepository,
    PasswordEncoder passwordEncoder,
    DataStoreService dataStore,
    AuthService authService,
    RobotProperties robotProperties,
    AlarmService alarmService
  ) {
    this.userRepository = userRepository;
    this.preferenceRepository = preferenceRepository;
    this.passwordEncoder = passwordEncoder;
    this.dataStore = dataStore;
    this.authService = authService;
    this.robotProperties = robotProperties;
    this.alarmService = alarmService;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    seedUsers();
    seedBusinessData();
  }

  private void seedUsers() {
    seedUser("user_admin", "admin", "Admin@123", "系统管理员", UserRole.ADMIN, "13800000001", "负责平台运维与权限管理");
    seedUser("user_dispatcher", "dispatcher", "Disp@123", "张调度", UserRole.DISPATCHER, "13800000002", "日常巡检任务调度与告警处置");
    seedUser("user_viewer", "viewer", "View@123", "李观察", UserRole.VIEWER, "13800000003", "只读查看监控与巡检记录");
  }

  private void seedUser(String id, String username, String password, String displayName, UserRole role, String phone, String bio) {
    UserEntity user = userRepository.findById(id).orElseGet(UserEntity::new);
    if (user.getId() == null) {
      user.setId(id);
      user.setCreatedAt("2026-01-01T00:00:00Z");
      user.setPasswordHash(passwordEncoder.encode(password));
    }
    user.setUsername(username);
    user.setDisplayName(displayName);
    user.setRole(role);
    user.setPhone(phone);
    user.setBio(bio);
    user.setAvatarUrl("https://api.dicebear.com/9.x/initials/svg?seed=" + displayName + "-" + id);
    user.setEnabled(true);
    userRepository.save(user);

    if (!preferenceRepository.existsById(id)) {
      UserPreferenceEntity preference = new UserPreferenceEntity();
      preference.setUserId(id);
      preferenceRepository.save(preference);
    }
  }

  private void seedBusinessData() {
    if (dataStore.list(DataCategory.SITE).isEmpty()) {
      site("site_001", "城东 220kV 变电站", "浙江省杭州市余杭区", "主变 2 台，户外 GIS 设备区", 30.2741, 120.1551, "2026-01-15T08:00:00Z");
      site("site_002", "城西 110kV 变电站", "浙江省杭州市西湖区", "室内开关室 + 室外电容器组", 30.2599, 120.12, "2026-02-01T08:00:00Z");
      site("site_003", "城南 500kV 变电站", "浙江省杭州市萧山区", "特高压枢纽站，户外设备规模大", 30.185, 120.265, "2026-03-10T08:00:00Z");
    }
    if (dataStore.list(DataCategory.AREA).isEmpty()) {
      area("area_001", "site_001", "主变区域", List.of(latLng(30.2745, 120.1545), latLng(30.2745, 120.1558), latLng(30.2738, 120.1558), latLng(30.2738, 120.1545)));
      area("area_002", "site_001", "GIS 设备区", List.of(latLng(30.2737, 120.1546), latLng(30.2737, 120.1556), latLng(30.2732, 120.1556), latLng(30.2732, 120.1546)));
      area("area_003", "site_002", "开关室", List.of(latLng(30.2602, 120.1195), latLng(30.2602, 120.1205), latLng(30.2596, 120.1205), latLng(30.2596, 120.1195)));
    }
    ensureSingleRobot();
    if (dataStore.list(DataCategory.DETECTION_TEMPLATE).isEmpty()) {
      template("tpl_route_001", "路线标准检测", "ROUTE", List.of("PERSON", "HELMET", "OBSTACLE", "FIRE"), "行进过程中持续检测人员、安全帽、障碍物与火源", map(), "2026-01-10T08:00:00Z");
      template("tpl_cp_001", "刀闸开关检测", "CHECKPOINT", List.of("SWITCH", "METER"), "检查点刀闸分合状态与表计读数", map("SWITCH", "红色刀闸开关", "METER", "压力表读数区域"), "2026-01-10T08:00:00Z");
      template("tpl_cp_002", "设备渗漏检测", "CHECKPOINT", List.of("OIL_LEAK", "FOREIGN_OBJECT", "FIRE"), "变压器及 GIS 渗漏、异物与烟火", map("OIL_LEAK", "设备底部渗油区域", "FOREIGN_OBJECT", "绝缘子表面异物"), "2026-02-15T08:00:00Z");
    }
    if (dataStore.list(DataCategory.ROUTE).isEmpty()) {
      routeDemo();
    }
    if (dataStore.list(DataCategory.ALARM).isEmpty()) {
      alarm("alarm_seed_001", "task_demo", "主变区例行巡检", null, "HELMET", "HIGH", "检测到作业人员未佩戴安全帽", "https://picsum.photos/seed/alarm1/400/240", false);
      alarm("alarm_seed_002", "task_demo", "主变区例行巡检", "GIS 刀闸", "SWITCH", "HIGH", "检查点「GIS 刀闸」开关/刀闸状态异常（LocateAnything: 红色刀闸开关）", "https://picsum.photos/seed/alarm2/400/240", true);
      alarm("alarm_seed_003", "task_demo", "电容器组巡检", null, "FIRE", "CRITICAL", "路线视野内检测到疑似火源/烟雾", "https://picsum.photos/seed/alarm3/400/240", false);
    }
    if (dataStore.list(DataCategory.RECORD).isEmpty()) {
      record("record_seed_001", "task_hist_001", "主变区夜间巡检", "主变区例行巡检", "电力巡检机器人", 2, 3, "28 分钟", "完成城东 220kV 变电站巡检，共 3 个检查点，触发 2 条告警");
      record("record_seed_002", "task_hist_002", "GIS 设备专项巡检", "GIS 专项路线", "电力巡检机器人", 0, 5, "35 分钟", "完成城东 220kV 变电站巡检，共 5 个检查点，无异常告警");
    }
    if (dataStore.list(DataCategory.WORK_ORDER).isEmpty()) {
      dataStore.upsert(DataCategory.WORK_ORDER, map("id", "wo_seed_1", "title", "主变区漏油异常处置", "description", "告警：检查点「主变 A 相」漏油检测异常，需现场复核", "status", "PROCESSING", "priority", "HIGH", "assigneeId", "user_dispatcher", "assigneeName", "张调度", "createdById", "user_admin", "createdByName", "系统管理员", "createdAt", "2026-06-10T08:00:00Z", "updatedAt", "2026-06-11T10:00:00Z"));
      dataStore.upsert(DataCategory.WORK_ORDER, map("id", "wo_seed_2", "title", "GIS 区未佩戴安全帽", "description", "路线行进中检测到作业人员未佩戴安全帽", "status", "PENDING", "priority", "URGENT", "createdById", "user_dispatcher", "createdByName", "张调度", "createdAt", "2026-06-12T14:30:00Z", "updatedAt", "2026-06-12T14:30:00Z"));
    }
    if (dataStore.list(DataCategory.NOTIFICATION).isEmpty()) {
      notification("ntf_seed_admin", "user_admin", "SYSTEM", "欢迎使用", "电力智能巡检平台已就绪，祝您工作顺利！", "/dashboard");
      notification("ntf_seed_dispatcher", "user_dispatcher", "SYSTEM", "欢迎使用", "电力智能巡检平台已就绪，祝您工作顺利！", "/dashboard");
      notification("ntf_seed_viewer", "user_viewer", "SYSTEM", "欢迎使用", "电力智能巡检平台已就绪，祝您工作顺利！", "/dashboard");
    }
    seedDemoScenarios();
  }

  /**
   * Adds a complete, non-destructive demonstration scenario.  Every record has a stable id and
   * is inserted only when absent, so restarting the service neither duplicates nor overwrites
   * operational data created during a demo.
   */
  private void seedDemoScenarios() {
    seedDemoAreas();
    seedDemoRobots();
    seedDemoRoutes();
    seedDemoTasksAndEvents();
    seedDemoAlarms();
    seedDemoRecords();
    seedDemoWorkOrders();
    seedDemoNotifications();
    seedDemoAgentSession();
    ensureSingleRobot();
  }

  private void seedDemoAreas() {
    insertIfMissing(DataCategory.AREA, map(
      "id", "area_demo_004", "siteId", "site_002", "name", "户外电容器组",
      "polygon", List.of(latLng(30.2595, 120.1194), latLng(30.2595, 120.1200), latLng(30.2590, 120.1200), latLng(30.2590, 120.1194)),
      "createdAt", daysAgo(20)
    ));
    insertIfMissing(DataCategory.AREA, map(
      "id", "area_demo_005", "siteId", "site_003", "name", "500kV 主变区",
      "polygon", List.of(latLng(30.1854, 120.2644), latLng(30.1854, 120.2656), latLng(30.1846, 120.2656), latLng(30.1846, 120.2644)),
      "createdAt", daysAgo(18)
    ));
    insertIfMissing(DataCategory.AREA, map(
      "id", "area_demo_006", "siteId", "site_003", "name", "500kV GIS 区",
      "polygon", List.of(latLng(30.1845, 120.2645), latLng(30.1845, 120.2655), latLng(30.1839, 120.2655), latLng(30.1839, 120.2645)),
      "createdAt", daysAgo(18)
    ));
  }

  private void seedDemoRobots() {
    insertIfMissing(DataCategory.ROBOT, demoRobot("robot_demo_004", "巡检机器人 D4", "Unitree B2-W", "UT-B2W-2025-014", "site_002", "BUSY", 68, latLng(30.2598, 120.1198), "task_demo_active"));
    insertIfMissing(DataCategory.ROBOT, demoRobot("robot_demo_005", "巡检机器人 E5", "DeepRobotics X30", "DR-X30-2025-021", "site_003", "BUSY", 76, latLng(30.1851, 120.2650), "task_demo_paused"));
    insertIfMissing(DataCategory.ROBOT, demoRobot("robot_demo_006", "巡检机器人 F6", "Unitree Go2 Pro", "UT-G2P-2025-032", "site_003", "OFFLINE", 12, null, null));
    insertIfMissing(DataCategory.ROBOT, demoRobot("robot_demo_007", "巡检机器人 G7", "DeepRobotics Lite3", "DR-L3-2026-006", "site_003", "ONLINE", 93, latLng(30.1849, 120.2649), null));
  }

  private Map<String, Object> demoRobot(String id, String name, String model, String serialNo, String siteId, String status, int battery, Map<String, Object> position, String currentTaskId) {
    Map<String, Object> item = map(
      "id", id, "name", name, "model", model, "serialNo", serialNo, "siteId", siteId,
      "status", status, "battery", battery, "firmware", "v3.2.0", "lastOnlineAt", minutesAgo(status.equals("OFFLINE") ? 180 : 2),
      "createdAt", daysAgo(30)
    );
    if (position != null) item.put("position", position);
    if (currentTaskId != null) item.put("currentTaskId", currentTaskId);
    return item;
  }

  private void seedDemoRoutes() {
    List<Map<String, Object>> switchyardPath = List.of(
      latLng(30.2599, 120.1198), latLng(30.2601, 120.1201), latLng(30.2598, 120.1204), latLng(30.2594, 120.1202)
    );
    insertIfMissing(DataCategory.ROUTE, demoRoute(
      "route_demo_002", "site_002", "城西开关室日常巡检", "覆盖开关室、母线与户外电容器组", switchyardPath,
      List.of(
        demoCheckpoint("cp_demo_101", "route_demo_002", "10kV 开关柜 A 段", 1, switchyardPath.get(1), 30, -15, "SWITCH", "METER"),
        demoCheckpoint("cp_demo_102", "route_demo_002", "母线桥接区", 2, switchyardPath.get(2), 75, -20, "FIRE", "FOREIGN_OBJECT"),
        demoCheckpoint("cp_demo_103", "route_demo_002", "2# 电容器组", 3, switchyardPath.get(3), 120, -25, "OIL_LEAK", "METER")
      ),
      daysAgo(15)
    ));

    List<Map<String, Object>> outdoorPath = List.of(
      latLng(30.1850, 120.2650), latLng(30.1853, 120.2654), latLng(30.1848, 120.2657), latLng(30.1843, 120.2652)
    );
    insertIfMissing(DataCategory.ROUTE, demoRoute(
      "route_demo_003", "site_003", "城南 500kV 户外设备巡检", "主变、避雷器与 GIS 设备红外巡检", outdoorPath,
      List.of(
        demoCheckpoint("cp_demo_201", "route_demo_003", "1# 主变 A 相", 1, outdoorPath.get(1), 45, -18, "OIL_LEAK", "FIRE"),
        demoCheckpoint("cp_demo_202", "route_demo_003", "500kV 避雷器", 2, outdoorPath.get(2), 90, -12, "METER", "FOREIGN_OBJECT"),
        demoCheckpoint("cp_demo_203", "route_demo_003", "GIS 出线间隔", 3, outdoorPath.get(3), 130, -22, "SWITCH", "FIRE")
      ),
      daysAgo(12)
    ));
  }

  private Map<String, Object> demoRoute(String id, String siteId, String name, String description, List<Map<String, Object>> path, List<Map<String, Object>> checkpoints, String createdAt) {
    return map(
      "id", id, "siteId", siteId, "name", name, "description", description, "path", path,
      "mapMode", "2d", "routeDetections", detectionItems("PERSON", "HELMET", "OBSTACLE", "FIRE"),
      "checkpoints", checkpoints, "createdAt", createdAt
    );
  }

  private Map<String, Object> demoCheckpoint(String id, String routeId, String name, int seq, Map<String, Object> position, int pan, int tilt, String... detections) {
    return map(
      "id", id, "routeId", routeId, "name", name, "seq", seq, "position", position,
      "pan", pan, "tilt", tilt, "dwellSeconds", 25, "detections", detectionItems(detections)
    );
  }

  private void seedDemoTasksAndEvents() {
    insertIfMissing(DataCategory.TASK, map(
      "id", "task_demo_active", "name", "开关室异常复核巡检", "routeId", "route_demo_002", "robotId", "robot_demo_004",
      "status", "MANUAL_TAKEOVER", "progress", 68, "currentCheckpointSeq", 2, "startedAt", minutesAgo(42),
      "createdAt", minutesAgo(55), "note", "母线桥接区检测到人员作业，调度员已人工接管机器人"
    ));
    insertIfMissing(DataCategory.TASK, map(
      "id", "task_demo_paused", "name", "500kV 主变红外测温巡检", "routeId", "route_demo_003", "robotId", "robot_demo_005",
      "status", "PAUSED", "progress", 43, "currentCheckpointSeq", 1, "startedAt", minutesAgo(78),
      "createdAt", minutesAgo(95), "note", "等待现场值班员确认主变 A 相温升告警"
    ));
    insertIfMissing(DataCategory.TASK, map(
      "id", "task_demo_planned", "name", "城东主变区晚间例行巡检", "routeId", "route_demo_001", "robotId", "robot_001",
      "status", "CREATED", "progress", 0, "currentCheckpointSeq", 0, "createdAt", minutesAgo(18)
    ));
    insertIfMissing(DataCategory.TASK, map(
      "id", "task_demo_cancelled", "name", "城西电容器组专项巡检", "routeId", "route_demo_002", "robotId", "robot_003",
      "status", "CANCELLED", "progress", 15, "currentCheckpointSeq", 0, "createdAt", daysAgo(1), "completedAt", hoursAgo(22),
      "note", "受雷雨天气影响取消，已改期"
    ));

    event("evt_demo_001", "task_demo_active", "DISPATCH", "任务已下发至巡检机器人 D4", null, null, minutesAgo(42));
    event("evt_demo_002", "task_demo_active", "ARRIVE", "已到达检查点“10kV 开关柜 A 段”", "10kV 开关柜 A 段", null, minutesAgo(36));
    event("evt_demo_003", "task_demo_active", "DETECT", "已完成开关柜状态与仪表读数识别", "10kV 开关柜 A 段", "https://picsum.photos/seed/demo-switchgear/400/240", minutesAgo(33));
    event("evt_demo_004", "task_demo_active", "ARRIVE", "已到达检查点“母线桥接区”", "母线桥接区", null, minutesAgo(25));
    event("evt_demo_005", "task_demo_active", "ALARM", "检测到现场作业人员，任务转人工接管", "母线桥接区", "https://picsum.photos/seed/demo-helmet/400/240", minutesAgo(22));
    event("evt_demo_006", "task_demo_paused", "PAUSE", "等待现场值班员确认主变 A 相温升情况", "1# 主变 A 相", null, minutesAgo(16));
  }

  private void event(String id, String taskId, String type, String message, String checkpointName, String imageUrl, String createdAt) {
    Map<String, Object> item = map("id", id, "taskId", taskId, "type", type, "message", message, "createdAt", createdAt);
    if (checkpointName != null) item.put("checkpointName", checkpointName);
    if (imageUrl != null) item.put("imageUrl", imageUrl);
    insertIfMissing(DataCategory.EVENT, item);
  }

  private void seedDemoAlarms() {
    currentDemoAlarm("alarm_demo_001", "task_demo_active", "城西开关室日常巡检", "母线桥接区", "HELMET", "HIGH", "检测到作业人员未佩戴安全帽，已转人工接管", "https://picsum.photos/seed/demo-helmet/400/240", false, minutesAgo(22));
    currentDemoAlarm("alarm_demo_002", "task_demo_paused", "城南 500kV 户外设备巡检", "1# 主变 A 相", "FIRE", "CRITICAL", "红外画面出现疑似局部过热热点，需立即复核", "https://picsum.photos/seed/demo-hotspot/400/240", false, minutesAgo(16));
    currentDemoAlarm("alarm_demo_003", "task_demo_paused", "城南 500kV 户外设备巡检", "1# 主变 A 相", "OIL_LEAK", "HIGH", "主变底部疑似存在渗油痕迹", "https://picsum.photos/seed/demo-oil-leak/400/240", false, hoursAgo(5));
    currentDemoAlarm("alarm_demo_004", "task_demo_cancelled", "城西开关室日常巡检", "2# 电容器组", "METER", "LOW", "压力表读数处于预警区间，建议下次巡检复核", "https://picsum.photos/seed/demo-meter/400/240", true, daysAgo(1));
    currentDemoAlarm("alarm_demo_policy_critical", "task_demo_paused", "城南 500kV 户外设备巡检", "1# 主变 A 相", "FIRE", "CRITICAL", "【测试】CRITICAL 告警，应自动创建 URGENT 工单", "https://picsum.photos/seed/policy-critical/400/240", false, minutesAgo(1));
    currentDemoAlarm("alarm_demo_policy_high", "task_demo_active", "城西开关室日常巡检", "母线桥接区", "HELMET", "HIGH", "【测试】HIGH 告警，等待人工转工单", "https://picsum.photos/seed/policy-high/400/240", false, minutesAgo(2));
    currentDemoAlarm("alarm_demo_policy_low", "task_demo_cancelled", "城西开关室日常巡检", "2# 电容器组", "METER", "LOW", "【测试】LOW 告警，等待人工转工单", "https://picsum.photos/seed/policy-low/400/240", false, minutesAgo(3));
    historicalDemoAlarm("alarm_demo_005", "task_hist_demo_003", "城东主变区例行巡检", "GIS 刀闸", "SWITCH", "HIGH", "GIS 刀闸状态与计划状态不一致", "https://picsum.photos/seed/demo-switch/400/240", true, daysAgo(2));
    historicalDemoAlarm("alarm_demo_006", "task_hist_demo_004", "城南 500kV 户外设备巡检", null, "OBSTACLE", "MEDIUM", "机器人前方发现临时施工围栏，已减速绕行", "https://picsum.photos/seed/demo-obstacle/400/240", true, daysAgo(3));
    historicalDemoAlarm("alarm_demo_007", "task_hist_demo_005", "城西开关室日常巡检", "母线桥接区", "FOREIGN_OBJECT", "MEDIUM", "发现绝缘子附近悬挂异物", "https://picsum.photos/seed/demo-foreign-object/400/240", false, daysAgo(4));
    historicalDemoAlarm("alarm_demo_008", "task_hist_demo_006", "城东主变区例行巡检", null, "PERSON", "MEDIUM", "检测到未授权人员进入设备区", "https://picsum.photos/seed/demo-person/400/240", true, daysAgo(5));
    historicalDemoAlarm("alarm_demo_009", "task_hist_demo_007", "城南 500kV 户外设备巡检", "500kV 避雷器", "METER", "LOW", "避雷器泄漏电流读数轻微波动", "https://picsum.photos/seed/demo-arrester/400/240", true, daysAgo(6));
    historicalDemoAlarm("alarm_demo_policy_history_pending", "task_hist_demo_008", "城东主变区例行巡检", "GIS 刀闸", "SWITCH", "MEDIUM", "【测试】历史告警，无关联工单", "https://picsum.photos/seed/policy-history-pending/400/240", true, daysAgo(7));
    historicalDemoAlarm("alarm_demo_policy_history", "task_hist_demo_008", "城东主变区例行巡检", "GIS 刀闸", "SWITCH", "MEDIUM", "【测试】历史告警，未应用转工单规则", "https://picsum.photos/seed/policy-history/400/240", true, daysAgo(7));
  }

  private void currentDemoAlarm(String id, String taskId, String routeName, String checkpointName, String type, String severity, String message, String imageUrl, boolean acknowledged, String createdAt) {
    if (!dataStore.exists(DataCategory.ALARM, id)) {
      alarmService.create(demoAlarmItem(id, taskId, routeName, checkpointName, type, severity, message, imageUrl, acknowledged, createdAt));
    }
  }

  private void historicalDemoAlarm(String id, String taskId, String routeName, String checkpointName, String type, String severity, String message, String imageUrl, boolean acknowledged, String createdAt) {
    insertIfMissing(DataCategory.ALARM, demoAlarmItem(id, taskId, routeName, checkpointName, type, severity, message, imageUrl, acknowledged, createdAt));
  }

  private Map<String, Object> demoAlarmItem(String id, String taskId, String routeName, String checkpointName, String type, String severity, String message, String imageUrl, boolean acknowledged, String createdAt) {
    Map<String, Object> item = map(
      "id", id, "taskId", taskId, "routeName", routeName, "type", type, "severity", severity,
      "message", message, "imageUrl", imageUrl, "acknowledged", acknowledged, "createdAt", createdAt
    );
    if (checkpointName != null) item.put("checkpointName", checkpointName);
    return item;
  }

  private void seedDemoRecords() {
    demoRecord("record_demo_001", "task_hist_demo_001", "城东主变区例行巡检", "Main transformer patrol", "巡检机器人 A1", 1, 3, "31 分钟", "完成主变区全路线巡检，设备运行正常。", daysAgo(1));
    demoRecord("record_demo_002", "task_hist_demo_002", "城西开关室日常巡检", "城西开关室日常巡检", "巡检机器人 C3", 0, 3, "26 分钟", "完成开关室与电容器组巡检，未发现新增异常。", daysAgo(2));
    demoRecord("record_demo_003", "task_hist_demo_003", "城东 GIS 专项巡检", "Main transformer patrol", "巡检机器人 B2", 1, 3, "38 分钟", "发现 GIS 刀闸状态异常，已生成复核告警。", daysAgo(3));
    demoRecord("record_demo_004", "task_hist_demo_004", "500kV 户外设备巡检", "城南 500kV 户外设备巡检", "巡检机器人 E5", 1, 3, "42 分钟", "因施工围栏绕行，已完成设备区巡检。", daysAgo(4));
    demoRecord("record_demo_005", "task_hist_demo_005", "开关室夜间巡检", "城西开关室日常巡检", "巡检机器人 D4", 1, 3, "29 分钟", "识别到绝缘子附近异物，已通知现场清理。", daysAgo(5));
    demoRecord("record_demo_006", "task_hist_demo_006", "城东安全专项巡检", "Main transformer patrol", "巡检机器人 A1", 1, 3, "33 分钟", "发现未授权人员进入设备区，安保已处置。", daysAgo(6));
    demoRecord("record_demo_007", "task_hist_demo_007", "避雷器例行巡检", "城南 500kV 户外设备巡检", "巡检机器人 G7", 1, 3, "36 分钟", "避雷器读数轻微波动，已纳入趋势观察。", daysAgo(7));
  }

  private void demoRecord(String id, String taskId, String taskName, String routeName, String robotName, int alarmCount, int checkpointCount, String duration, String summary, String completedAt) {
    insertIfMissing(DataCategory.RECORD, map(
      "id", id, "taskId", taskId, "taskName", taskName, "routeName", routeName, "robotName", robotName,
      "alarmCount", alarmCount, "checkpointCount", checkpointCount, "duration", duration, "summary", summary,
      "completedAt", completedAt, "createdAt", completedAt
    ));
  }

  private void seedDemoWorkOrders() {
    insertIfMissing(DataCategory.WORK_ORDER, map(
      "id", "wo_demo_001", "alarmId", "alarm_demo_002", "title", "主变 A 相过热热点紧急复核", "description", "立即安排现场人员复核红外热点及温升数据。", "locationDescription", "城南 500kV 变电站 / 户外主变区 / 1# 主变 A 相 / 接线区域",
      "status", "PROCESSING", "priority", "URGENT", "assigneeId", "user_dispatcher", "assigneeName", "张调度",
      "createdById", "user_admin", "createdByName", "系统管理员", "createdAt", minutesAgo(14), "updatedAt", minutesAgo(6)
    ));
    insertIfMissing(DataCategory.WORK_ORDER, map(
      "id", "wo_demo_002", "alarmId", "alarm_demo_003", "title", "主变渗油痕迹现场检查", "description", "检查主变底部油位、密封件与地面油迹。", "locationDescription", "城南 500kV 变电站 / 户外主变区 / 1# 主变 A 相 / 本体底部",
      "source", "MANUAL", "status", "REVIEW", "priority", "HIGH", "assigneeName", "王运维", "createdById", "user_dispatcher", "createdByName", "张调度",
      "resolution", "现场未发现持续渗漏，已清洁油迹并安排 24 小时复测。", "review", map("conclusion", "PARTIALLY_RESOLVED", "onsiteFinding", "复核主变本体底部、油位计和密封件，未发现持续渗漏。", "handlingMeasures", "已清洁历史油迹并完成油位复测，当前读数正常。", "followUpPlan", "安排 24 小时后复测油位与地面油迹。", "submittedById", "user_dispatcher", "submittedByName", "王运维", "submittedAt", hoursAgo(2)), "createdAt", hoursAgo(5), "updatedAt", hoursAgo(2)
    ));
    insertIfMissing(DataCategory.WORK_ORDER, map(
      "id", "wo_demo_003", "alarmId", "alarm_demo_005", "title", "GIS 刀闸状态核验", "description", "核对调度指令、刀闸位置与二次信号。", "locationDescription", "城东 GIS 设备区 / GIS 刀闸 / 二次端子箱",
      "status", "CLOSED", "priority", "HIGH", "assigneeName", "陈检修", "createdById", "user_dispatcher", "createdByName", "张调度",
      "resolution", "二次辅助接点误报，已完成更换并验证。", "createdAt", daysAgo(2), "updatedAt", daysAgo(1), "closedAt", daysAgo(1)
    ));
    insertIfMissing(DataCategory.WORK_ORDER, map(
      "id", "wo_demo_004", "alarmId", "alarm_demo_007", "title", "绝缘子异物清理", "description", "安排现场人员清理绝缘子附近悬挂异物。", "locationDescription", "城西 110kV 变电站 / 开关室 / 母线桥接区 / 绝缘子附近",
      "status", "PENDING", "priority", "MEDIUM", "assigneeName", "李运维", "createdById", "user_dispatcher", "createdByName", "张调度",
      "createdAt", daysAgo(4), "updatedAt", daysAgo(4)
    ));
    insertIfMissing(DataCategory.WORK_ORDER, map(
      "id", "wo_demo_policy_history", "alarmId", "alarm_demo_policy_history", "title", "历史告警工单", "description", "【测试】用于验证缺少转单来源时的“已有工单”展示。",
      "status", "CLOSED", "priority", "MEDIUM", "assigneeName", "陈检修", "createdById", "user_dispatcher", "createdByName", "张调度",
      "createdAt", daysAgo(7), "updatedAt", daysAgo(6), "closedAt", daysAgo(6)
    ));
  }

  private void seedDemoNotifications() {
    demoNotification("ntf_demo_001", "user_dispatcher", "ALARM", "主变热点待复核", "城南 500kV 站 1# 主变 A 相出现疑似过热热点。", "/alarms", false, minutesAgo(15));
    demoNotification("ntf_demo_002", "user_dispatcher", "WORKORDER", "紧急工单已自动创建", "工单 wo_alarm_alarm_demo_002 已由系统自动创建，待调度员处理。", "/workorders", false, minutesAgo(12));
    demoNotification("ntf_demo_003", "user_admin", "SYSTEM", "今日巡检运行概览", "当前 2 项任务处于人工干预状态，请关注处置进展。", "/dashboard", false, minutesAgo(10));
    demoNotification("ntf_demo_004", "user_dispatcher", "TASK", "夜间例行巡检待下发", "城东主变区晚间例行巡检已创建。", "/tasks", true, minutesAgo(18));
  }

  private void demoNotification(String id, String userId, String type, String title, String content, String link, boolean read, String createdAt) {
    insertIfMissing(DataCategory.NOTIFICATION, map(
      "id", id, "userId", userId, "type", type, "title", title, "content", content,
      "read", read, "link", link, "createdAt", createdAt
    ));
  }

  private void seedDemoAgentSession() {
    String sessionId = "agent_session_demo_001";
    String runId = "agent_run_demo_001";
    insertIfMissing(DataCategory.AGENT_SESSION, map(
      "id", sessionId, "title", "告警处置：主变 A 相过热热点", "inputType", "ALARM", "taskId", "task_demo_paused",
      "alarmId", "alarm_demo_002", "prompt", "优先判断是否需要安排现场紧急复核。", "status", "SUCCEEDED",
      "createdById", "user_dispatcher", "createdAt", hoursAgo(3), "updatedAt", hoursAgo(2)
    ));
    insertIfMissing(DataCategory.AGENT_RUN, map(
      "id", runId, "sessionId", sessionId, "status", "SUCCEEDED", "startedAt", hoursAgo(3), "completedAt", hoursAgo(2),
      "steps", List.of(
        agentStep("agent_step_demo_001", runId, sessionId, "RUN_STARTED", "Agent 开始巡检处置分析", hoursAgo(3)),
        agentStep("agent_step_demo_002", runId, sessionId, "TOOL_COMPLETED", "已完成任务、告警与工单上下文查询", hoursAgo(3)),
        agentStep("agent_step_demo_003", runId, sessionId, "LLM_ANALYZED", "已生成缺陷研判：主变局部过热风险", hoursAgo(2)),
        agentStep("agent_step_demo_004", runId, sessionId, "RUN_SUCCEEDED", "Agent 分析完成，等待人工确认建议动作", hoursAgo(2))
      ),
      "summary", map(
        "defectLevel", "CRITICAL", "cause", "红外画面显示主变 A 相接线区域温度异常升高，存在局部过热风险。",
        "recommendedActions", List.of("立即安排现场红外复测", "核查负荷电流与接线端子紧固状态", "复测前保持该区域重点监视"),
        "citations", List.of("告警截图", "巡检任务上下文", "红外复核结果"), "confidence", 0.91
      )
    ));
    insertIfMissing(DataCategory.AGENT_EVIDENCE, map(
      "id", "agent_ev_demo_001", "sessionId", sessionId, "runId", runId, "type", "ALARM", "sourceId", "alarm_demo_002",
      "title", "告警证据", "content", "主变 A 相出现疑似局部过热热点，告警等级为 CRITICAL。",
      "imageUrl", "https://picsum.photos/seed/demo-hotspot/400/240", "payload", map("alarmId", "alarm_demo_002"), "createdAt", hoursAgo(3)
    ));
    insertIfMissing(DataCategory.AGENT_EVIDENCE, map(
      "id", "agent_ev_demo_002", "sessionId", sessionId, "runId", runId, "type", "TASK", "sourceId", "task_demo_paused",
      "title", "任务上下文", "content", "任务已在 1# 主变 A 相检查点暂停，等待现场值班员确认。",
      "payload", map("taskId", "task_demo_paused"), "createdAt", hoursAgo(3)
    ));
    insertIfMissing(DataCategory.AGENT_EVIDENCE, map(
      "id", "agent_ev_demo_003", "sessionId", sessionId, "runId", runId, "type", "LOCATE_ANYTHING", "sourceId", "alarm_demo_002",
      "title", "LocateAnything 复核", "content", "热点区域置信度 0.91，建议紧急现场复核。",
      "imageUrl", "https://picsum.photos/seed/demo-hotspot/400/240", "payload", map("score", 0.91), "createdAt", hoursAgo(2)
    ));
    insertIfMissing(DataCategory.AGENT_ACTION, map(
      "id", "agent_act_demo_001", "sessionId", sessionId, "runId", runId, "type", "PUSH_NOTIFICATION", "status", "PENDING",
      "title", "推送主变热点处置通知", "description", "向调度员推送 Agent 研判摘要，等待人工确认。",
      "payload", map("userId", "user_dispatcher", "type", "AGENT", "title", "主变热点处置建议", "content", "请立即安排现场红外复测。", "link", "/agents"),
      "createdAt", hoursAgo(2), "updatedAt", hoursAgo(2)
    ));
  }

  private Map<String, Object> agentStep(String id, String runId, String sessionId, String type, String message, String createdAt) {
    return map("id", id, "runId", runId, "sessionId", sessionId, "type", type, "message", message, "payload", map(), "createdAt", createdAt);
  }

  private void insertIfMissing(String category, Map<String, Object> item) {
    String id = String.valueOf(item.get("id"));
    if (!dataStore.exists(category, id)) {
      dataStore.upsert(category, item);
    }
  }

  private String daysAgo(int days) {
    return Instant.now().minusSeconds(days * 86400L).toString();
  }

  private String hoursAgo(int hours) {
    return Instant.now().minusSeconds(hours * 3600L).toString();
  }

  private String minutesAgo(int minutes) {
    return Instant.now().minusSeconds(minutes * 60L).toString();
  }

  private void site(String id, String name, String address, String description, double lat, double lng, String createdAt) {
    Map<String, Object> item = map("id", id, "name", name, "address", address, "description", description, "center", latLng(lat, lng), "createdAt", createdAt);
    dataStore.upsert(DataCategory.SITE, item);
  }

  private void area(String id, String siteId, String name, List<Map<String, Object>> polygon) {
    dataStore.upsert(DataCategory.AREA, map("id", id, "siteId", siteId, "name", name, "polygon", polygon, "createdAt", "2026-01-15T08:00:00Z"));
  }

  private void ensureSingleRobot() {
    String robotId = robotProperties.getRobotId();
    for (Map<String, Object> existing : dataStore.list(DataCategory.ROBOT)) {
      if (!robotId.equals(String.valueOf(existing.get("id")))) {
        dataStore.delete(DataCategory.ROBOT, String.valueOf(existing.get("id")));
      }
    }
    robot(robotId, "电力巡检机器人", "Jetson Orin + ZLAC8015D", "YLHB-001", "site_001", "ONLINE", null, "ROS2 Humble");
  }

  private void robot(String id, String name, String model, String serialNo, String siteId, String status, Map<String, Object> position, String firmware) {
    Map<String, Object> item = map("id", id, "name", name, "model", model, "serialNo", serialNo, "siteId", siteId, "status", status, "firmware", firmware, "lastOnlineAt", Instant.now().toString(), "createdAt", Instant.now().toString());
    if (position != null) {
      item.put("position", position);
    }
    dataStore.upsert(DataCategory.ROBOT, item);
  }

  private void template(String id, String name, String scope, List<String> types, String description, Map<String, Object> prompts, String createdAt) {
    dataStore.upsert(DataCategory.DETECTION_TEMPLATE, map("id", id, "name", name, "scope", scope, "types", types, "description", description, "prompts", prompts, "createdAt", createdAt));
  }

  private void routeDemo() {
    Map<String, Object> c = latLng(30.2741, 120.1551);
    double d = 0.0003;
    List<Map<String, Object>> path = List.of(
      c,
      latLng(30.2741 + d, 120.1551 + d * 0.5),
      latLng(30.2741 + d * 0.5, 120.1551 + d),
      latLng(30.2741 - d * 0.3, 120.1551 + d * 0.8)
    );
    List<Map<String, Object>> cps = List.of(
      map("id", "cp_demo_001", "routeId", "route_demo_001", "name", "1# 主变", "seq", 1, "position", path.get(1), "pan", 45, "tilt", -20, "dwellSeconds", 25, "detections", detectionItems("SWITCH", "METER", "OIL_LEAK", "FIRE", "FOREIGN_OBJECT")),
      map("id", "cp_demo_002", "routeId", "route_demo_001", "name", "GIS 刀闸", "seq", 2, "position", path.get(2), "pan", 90, "tilt", -15, "dwellSeconds", 30, "detections", detectionItems("SWITCH", "METER", "OIL_LEAK", "FIRE", "FOREIGN_OBJECT")),
      map("id", "cp_demo_003", "routeId", "route_demo_001", "name", "电容器组", "seq", 3, "position", path.get(3), "pan", 120, "tilt", -25, "dwellSeconds", 20, "detections", detectionItems("SWITCH", "METER", "OIL_LEAK", "FIRE", "FOREIGN_OBJECT"))
    );
    Map<String, Object> executorJson = rosRouteDemo();
    dataStore.upsert(DataCategory.ROUTE, map("id", "route_demo_001", "siteId", "site_001", "name", "Main transformer patrol", "description", "ROS demo route", "path", path, "mapMode", "ros2d", "routeDetections", detectionItems("PERSON", "HELMET", "OBSTACLE", "FIRE"), "checkpoints", cps, "executorJson", executorJson, "rosRoute", executorJson, "createdAt", "2026-03-01T08:00:00Z"));
  }

  private Map<String, Object> rosRouteDemo() {
    return map(
      "version", 2,
      "frame_id", "map",
      "active_route_id", "route_patrol_001",
      "start_pose", map(
        "name", "Initial pose",
        "pose", map("x", 0.5, "y", -0.5, "yaw", 0.2),
        "publish_initial_pose", true,
        "covariance", map("x", 0.25, "y", 0.25, "yaw", 0.0685)
      ),
      "targets", List.of(
        map("id", "target_001", "name", "Target 1", "pose", map("x", 2.5, "y", 0.8, "yaw", 1.2), "task_duration_sec", 25),
        map("id", "target_002", "name", "Target 2", "pose", map("x", 4.2, "y", -0.6, "yaw", 0.5), "task_duration_sec", 30),
        map("id", "target_003", "name", "Target 3", "pose", map("x", 1.8, "y", 1.5, "yaw", -0.8), "task_duration_sec", 20)
      ),
      "routes", List.of(map(
        "id", "route_patrol_001",
        "name", "Main patrol route",
        "target_ids", List.of("target_001", "target_002", "target_003"),
        "return_to_start", true,
        "loop", map("enabled", false, "wait_sec", 600, "max_cycles", 0),
        "goal_timeout_sec", 120,
        "max_retries_per_checkpoint", 1,
        "failure_policy", "abort_and_return_home"
      )),
      "schedules", List.of()
    );
  }
  private void alarm(String id, String taskId, String routeName, String checkpointName, String type, String severity, String message, String imageUrl, boolean acknowledged) {
    Map<String, Object> item = map("id", id, "taskId", taskId, "routeName", routeName, "type", type, "severity", severity, "message", message, "imageUrl", imageUrl, "acknowledged", acknowledged, "createdAt", Instant.now().minusSeconds(3600).toString());
    if (checkpointName != null) {
      item.put("checkpointName", checkpointName);
    }
    dataStore.upsert(DataCategory.ALARM, item);
  }

  private void record(String id, String taskId, String taskName, String routeName, String robotName, int alarmCount, int checkpointCount, String duration, String summary) {
    dataStore.upsert(DataCategory.RECORD, map("id", id, "taskId", taskId, "taskName", taskName, "routeName", routeName, "robotName", robotName, "alarmCount", alarmCount, "checkpointCount", checkpointCount, "duration", duration, "summary", summary, "completedAt", Instant.now().minusSeconds(172800).toString(), "createdAt", Instant.now().minusSeconds(172800).toString()));
  }

  private void notification(String id, String userId, String type, String title, String content, String link) {
    dataStore.upsert(DataCategory.NOTIFICATION, map("id", id, "userId", userId, "type", type, "title", title, "content", content, "read", false, "link", link, "createdAt", Instant.now().toString()));
  }

  private List<Map<String, Object>> detectionItems(String... types) {
    return List.of(types).stream().map(type -> {
      Map<String, Object> item = map("type", type, "enabled", true, "threshold", 0.75);
      if ("SWITCH".equals(type)) {
        item.put("prompt", "红色刀闸开关");
      } else if ("OIL_LEAK".equals(type)) {
        item.put("prompt", "设备底部渗油区域");
      } else if ("METER".equals(type)) {
        item.put("prompt", "压力表读数区域");
      }
      return item;
    }).toList();
  }

  private Map<String, Object> latLng(double lat, double lng) {
    return map("lat", lat, "lng", lng);
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> item = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      item.put(values[i].toString(), values[i + 1]);
    }
    return item;
  }
}
