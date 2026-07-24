package com.powerinspection.task;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskExecutionRepositoryTests {
  @Autowired private TaskExecutionRepository repository;

  @Test
  void oneTaskCanRetainMultipleExecutionAttempts() {
    TaskExecutionEntity first = execution("task-retry-history", "exec-history-1", null, "2026-07-23T00:00:00Z");
    TaskExecutionEntity retry = execution("task-retry-history", "exec-history-2", first.getExecutionId(), "2026-07-23T00:01:00Z");

    repository.saveAndFlush(first);
    repository.saveAndFlush(retry);

    List<TaskExecutionEntity> attempts = repository.findByTaskIdOrderByCreatedAtDesc("task-retry-history");
    assertEquals(List.of("exec-history-2", "exec-history-1"), attempts.stream().map(TaskExecutionEntity::getExecutionId).toList());
    assertEquals("exec-history-1", attempts.get(0).getPreviousExecutionId());
  }

  private static TaskExecutionEntity execution(String taskId, String executionId, String previousExecutionId, String createdAt) {
    TaskExecutionEntity item = new TaskExecutionEntity();
    item.setTaskId(taskId);
    item.setExecutionId(executionId);
    item.setPreviousExecutionId(previousExecutionId);
    item.setRouteRevisionId("revision-history");
    item.setRobotId("robot-history");
    item.setRouteContentSha256("a".repeat(64));
    item.setMapImageSha256("b".repeat(64));
    item.setStatus(TaskExecutionStatus.START_FAILED.name());
    item.setCreatedAt(createdAt);
    item.setUpdatedAt(createdAt);
    return item;
  }
}
