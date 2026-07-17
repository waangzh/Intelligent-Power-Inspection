package com.powerinspection.dashboard;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.PageResult;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
  private final DataStoreService dataStore;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public DashboardController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser) {
    this.dataStore = dataStore;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping("/overview")
  public ApiResponse<Map<String, Object>> overview() {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    Map<String, Object> result = new LinkedHashMap<>();
    long siteCount = count(DataCategory.SITE, Map.of());
    long routeCount = count(DataCategory.ROUTE, Map.of());
    long robotCount = count(DataCategory.ROBOT, Map.of());
    long offlineRobots = count(DataCategory.ROBOT, Map.of("status", "OFFLINE"));
    long taskCount = count(DataCategory.TASK, Map.of());
    long completedTasks = count(DataCategory.TASK, Map.of("status", "COMPLETED"));
    long activeTasks = count(DataCategory.TASK, Map.of("status", "DISPATCHED,RUNNING,PAUSED,MANUAL_TAKEOVER"));
    long alarmCount = count(DataCategory.ALARM, Map.of());
    long acknowledgedAlarms = count(DataCategory.ALARM, Map.of("acknowledged", "true"));
    result.put("counts", Map.of(
      "sites", siteCount, "routes", routeCount, "robots", robotCount,
      "onlineRobots", Math.max(0, robotCount - offlineRobots),
      "tasks", taskCount, "completedTasks", completedTasks, "activeTasks", activeTasks,
      "alarms", alarmCount, "unacknowledgedAlarms", Math.max(0, alarmCount - acknowledgedAlarms)
    ));
    result.put("rates", Map.of(
      "robotOnline", percentage(robotCount - offlineRobots, robotCount),
      "taskCompletion", percentage(completedTasks, taskCount),
      "alarmHandled", alarmCount == 0 ? 100 : percentage(acknowledgedAlarms, alarmCount)
    ));
    result.put("alarmSeverity", Map.of(
      "CRITICAL", count(DataCategory.ALARM, Map.of("severity", "CRITICAL")),
      "HIGH", count(DataCategory.ALARM, Map.of("severity", "HIGH")),
      "MEDIUM", count(DataCategory.ALARM, Map.of("severity", "MEDIUM")),
      "LOW", count(DataCategory.ALARM, Map.of("severity", "LOW"))
    ));
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    List<Long> weekly = java.util.stream.IntStream.range(0, 7).mapToObj(offset -> {
      LocalDate day = today.minusDays(6L - offset);
      return dataStore.count(
        DataCategory.ALARM,
        day.atStartOfDay().toInstant(ZoneOffset.UTC).toString(),
        day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString(),
        Map.of()
      );
    }).toList();
    result.put("weeklyAlarmCounts", weekly);
    result.put("recentAlarms", page(DataCategory.ALARM, 5, Map.of()).items());
    result.put("activeTaskItems", page(DataCategory.TASK, 10, Map.of("status", "DISPATCHED,RUNNING,PAUSED,MANUAL_TAKEOVER")).items());
    result.put("robotItems", page(DataCategory.ROBOT, 20, Map.of()).items());
    result.put("siteItems", page(DataCategory.SITE, 50, Map.of()).items());
    return ApiResponse.ok(result);
  }

  private long count(String category, Map<String, String> filters) {
    return dataStore.count(category, null, null, filters);
  }

  private PageResult<Map<String, Object>> page(String category, int size, Map<String, String> filters) {
    return dataStore.page(category, 0, size, "updatedAt", "desc", null, null, filters);
  }

  private int percentage(long numerator, long denominator) {
    return denominator == 0 ? 0 : (int) Math.round(numerator * 100.0 / denominator);
  }
}
