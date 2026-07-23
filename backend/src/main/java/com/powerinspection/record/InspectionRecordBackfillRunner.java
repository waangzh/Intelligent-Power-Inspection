package com.powerinspection.record;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.route.RouteExecutorSupport;
import com.powerinspection.task.TaskExecutionEntity;
import com.powerinspection.task.TaskExecutionRepository;
import com.powerinspection.task.TaskExecutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 服务启动时补齐历史完成任务的巡检报告，保证旧数据也能进入巡检记录页面。 */
@Component
@Order(3)
public class InspectionRecordBackfillRunner implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(InspectionRecordBackfillRunner.class);

  private final DataStoreService dataStore;
  private final TaskExecutionRepository executions;
  private final InspectionRecordService inspectionRecordService;

  public InspectionRecordBackfillRunner(
      DataStoreService dataStore,
      TaskExecutionRepository executions,
      InspectionRecordService inspectionRecordService) {
    this.dataStore = dataStore;
    this.executions = executions;
    this.inspectionRecordService = inspectionRecordService;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    int repaired = 0;
    for (TaskExecutionEntity execution :
        executions.findByStatusIn(List.of(TaskExecutionStatus.COMPLETED.name()))) {
      if (repairExecution(execution)) repaired++;
    }
    for (Map<String, Object> task : dataStore.list(DataCategory.TASK)) {
      if (!TaskExecutionStatus.COMPLETED.name().equals(task.get("status"))) continue;
      if (task.get("executionId") != null) continue;
      if (repairLegacyTask(task)) repaired++;
    }
    if (repaired > 0) log.info("event=inspection_record_backfill repaired={}", repaired);
  }

  private boolean repairExecution(TaskExecutionEntity execution) {
    Map<String, Object> task = dataStore.find(DataCategory.TASK, execution.getTaskId());
    if (task == null) return false;
    Map<String, Object> route = dataStore.find(DataCategory.ROUTE, text(task.get("routeId")));
    if (route == null) return false;
    String completedAt = first(execution.getLastEventAt(), text(task.get("completedAt")), execution.getUpdatedAt());
    task.put("status", TaskExecutionStatus.COMPLETED.name());
    task.put("progress", 100);
    task.put("completedAt", completedAt);
    if (execution.getStartedAt() != null) task.put("startedAt", execution.getStartedAt());
    dataStore.upsert(DataCategory.TASK, task);
    inspectionRecordService.createForCompletedTask(
        task,
        route,
        RouteExecutorSupport.checkpointCount(route),
        execution.getStartedAt(),
        completedAt,
        execution.getExecutionId());
    return true;
  }

  private boolean repairLegacyTask(Map<String, Object> task) {
    Map<String, Object> route = dataStore.find(DataCategory.ROUTE, text(task.get("routeId")));
    if (route == null) return false;
    String completedAt = first(text(task.get("completedAt")), text(task.get("updatedAt")), Instant.now().toString());
    inspectionRecordService.createForCompletedTask(
        task,
        route,
        RouteExecutorSupport.checkpointCount(route),
        text(task.get("startedAt")),
        completedAt,
        text(task.get("executionId")));
    return true;
  }

  private static String first(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return Instant.now().toString();
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }
}
