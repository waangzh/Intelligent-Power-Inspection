package com.powerinspection.record;

import com.powerinspection.common.Ids;
import com.powerinspection.common.PageResult;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InspectionRecordService {
  private final DataStoreService dataStore;

  public InspectionRecordService(DataStoreService dataStore) {
    this.dataStore = dataStore;
  }

  @Transactional
  public Map<String, Object> createForCompletedTask(
      Map<String, Object> task,
      Map<String, Object> route,
      int checkpointCount,
      String startedAt,
      String completedAt,
      String executionId) {
    String taskId = text(task.get("id"));
    PageResult<Map<String, Object>> existing =
        dataStore.page(
            DataCategory.RECORD,
            0,
            1,
            "createdAt",
            "desc",
            null,
            null,
            Map.of("taskId", taskId));
    if (!existing.items().isEmpty()) {
      return existing.items().get(0);
    }

    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, text(task.get("robotId")));
    String siteId = text(route.get("siteId"));
    Map<String, Object> site = siteId == null ? null : dataStore.find(DataCategory.SITE, siteId);
    long alarmCount =
        dataStore.count(DataCategory.ALARM, null, null, Map.of("taskId", taskId));
    String routeName = fallback(route.get("name"), "-");
    String robotName = robot == null ? "-" : fallback(robot.get("name"), "-");
    String siteName = site == null ? "未知站点" : fallback(site.get("name"), "未知站点");

    Map<String, Object> record = new LinkedHashMap<>();
    record.put("id", recordId(executionId));
    record.put("taskId", taskId);
    record.put("siteId", siteId);
    record.put("routeId", text(route.get("id")));
    record.put("robotId", text(task.get("robotId")));
    record.put("taskName", fallback(task.get("name"), taskId));
    record.put("routeName", routeName);
    record.put("robotName", robotName);
    record.put("alarmCount", alarmCount);
    record.put("checkpointCount", checkpointCount);
    record.put("duration", duration(startedAt, completedAt));
    record.put(
        "summary",
        "完成 " + siteName + " 巡检，共 " + checkpointCount + " 个检查点，触发 " + alarmCount + " 条告警");
    record.put("completedAt", completedAt);
    record.put("createdAt", completedAt);
    if (executionId != null && !executionId.isBlank()) {
      record.put("executionId", executionId);
    }
    return dataStore.upsert(DataCategory.RECORD, record);
  }

  private static String recordId(String executionId) {
    return executionId == null || executionId.isBlank()
        ? Ids.next("record")
        : "record_" + executionId;
  }

  private static String duration(String startedAt, String completedAt) {
    try {
      long seconds = Math.max(0, Duration.between(Instant.parse(startedAt), Instant.parse(completedAt)).getSeconds());
      long minutes = seconds / 60;
      if (seconds > 0 && minutes == 0) return "不足 1 分钟";
      long hours = minutes / 60;
      long remainingMinutes = minutes % 60;
      return hours > 0 ? hours + " 小时 " + remainingMinutes + " 分钟" : minutes + " 分钟";
    } catch (Exception ignored) {
      return "-";
    }
  }

  private static String fallback(Object value, String fallback) {
    String text = text(value);
    return text == null || text.isBlank() ? fallback : text;
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }
}
