package com.powerinspection.data;

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

  public SeedDataInitializer(
    UserRepository userRepository,
    UserPreferenceRepository preferenceRepository,
    PasswordEncoder passwordEncoder,
    DataStoreService dataStore,
    AuthService authService,
    RobotProperties robotProperties
  ) {
    this.userRepository = userRepository;
    this.preferenceRepository = preferenceRepository;
    this.passwordEncoder = passwordEncoder;
    this.dataStore = dataStore;
    this.authService = authService;
    this.robotProperties = robotProperties;
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
      dataStore.upsert(DataCategory.WORK_ORDER, map("id", "wo_seed_1", "title", "主变区漏油异常处置", "description", "告警：检查点「主变 A 相」漏油检测异常，需现场复核", "status", "PROCESSING", "priority", "HIGH", "assigneeName", "张调度", "createdById", "user_admin", "createdByName", "系统管理员", "createdAt", "2026-06-10T08:00:00Z", "updatedAt", "2026-06-11T10:00:00Z"));
      dataStore.upsert(DataCategory.WORK_ORDER, map("id", "wo_seed_2", "title", "GIS 区未佩戴安全帽", "description", "路线行进中检测到作业人员未佩戴安全帽", "status", "PENDING", "priority", "URGENT", "createdById", "user_dispatcher", "createdByName", "张调度", "createdAt", "2026-06-12T14:30:00Z", "updatedAt", "2026-06-12T14:30:00Z"));
    }
    if (dataStore.list(DataCategory.NOTIFICATION).isEmpty()) {
      notification("ntf_seed_admin", "user_admin", "SYSTEM", "欢迎使用", "电力智能巡检平台已就绪，祝您工作顺利！", "/dashboard");
      notification("ntf_seed_dispatcher", "user_dispatcher", "SYSTEM", "欢迎使用", "电力智能巡检平台已就绪，祝您工作顺利！", "/dashboard");
      notification("ntf_seed_viewer", "user_viewer", "SYSTEM", "欢迎使用", "电力智能巡检平台已就绪，祝您工作顺利！", "/dashboard");
    }
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
    Map<String, Object> current = dataStore.find(DataCategory.ROBOT, robotId);
    if (current == null) {
      robot(robotId, "电力巡检机器人", "Jetson Orin + ZLAC8015D", "YLHB-001", "site_001", "ONLINE", null, "ROS2 Humble");
      return;
    }
    current.put("name", "电力巡检机器人");
    current.put("model", "Jetson Orin + ZLAC8015D");
    current.put("serialNo", "YLHB-001");
    current.put("siteId", "site_001");
    current.put("firmware", "ROS2 Humble");
    current.remove("battery");
    dataStore.upsert(DataCategory.ROBOT, current);
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
