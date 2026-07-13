package com.powerinspection.task;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, String> {
  Optional<TaskExecutionEntity> findByExecutionId(String executionId);
}
