package com.powerinspection.task;

import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskExecutionControlCommandRepository extends JpaRepository<TaskExecutionControlCommandEntity, String> {
  Optional<TaskExecutionControlCommandEntity> findByRequestId(String requestId);
  Optional<TaskExecutionControlCommandEntity> findByExecutionIdAndRequestId(String executionId, String requestId);
  Optional<TaskExecutionControlCommandEntity> findFirstByExecutionIdOrderByRequestedAtDesc(String executionId);
  Optional<TaskExecutionControlCommandEntity> findFirstByExecutionIdAndStatusInOrderByRequestedAtDesc(String executionId, Collection<String> statuses);
}
