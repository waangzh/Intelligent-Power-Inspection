package com.powerinspection.task;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.route.RouteRevisionEntity;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TaskExecutionService {
  private final TaskExecutionRepository repository;

  public TaskExecutionService(TaskExecutionRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public TaskExecutionEntity bind(Map<String, Object> task, RouteRevisionEntity revision) {
    String taskId = String.valueOf(task.get("id"));
    TaskExecutionEntity existing = repository.findById(taskId).orElse(null);
    if (existing != null) {
      if (!revision.getId().equals(existing.getRouteRevisionId())) {
        throw ApiException.conflict("任务已绑定其他路线修订");
      }
      return existing;
    }
    String now = Instant.now().toString();
    TaskExecutionEntity execution = new TaskExecutionEntity();
    execution.setTaskId(taskId);
    execution.setExecutionId(Ids.next("exec"));
    execution.setRouteRevisionId(revision.getId());
    execution.setRobotId(String.valueOf(task.get("robotId")));
    execution.setRouteContentSha256(revision.getContentSha256());
    execution.setMapImageSha256(revision.getMapImageSha256());
    execution.setStatus("CREATED");
    execution.setLastRobotSequence(0);
    execution.setCreatedAt(now);
    execution.setUpdatedAt(now);
    return repository.save(execution);
  }

  @Transactional
  public void delete(String taskId) {
    repository.deleteById(taskId);
  }

  public void requireDeletable(String taskId) {
    repository
        .findById(taskId)
        .ifPresent(
            execution -> {
              if (!TaskExecutionStatus.CREATED.name().equals(execution.getStatus())
                  && !TaskExecutionStatus.TERMINAL.contains(execution.getStatus())) {
                throw ApiException.badRequest("任务执行中不能删除");
              }
            });
  }
}
