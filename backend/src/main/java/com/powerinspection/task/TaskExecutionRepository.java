package com.powerinspection.task;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskExecutionRepository extends JpaRepository<TaskExecutionEntity, String> {
  Optional<TaskExecutionEntity> findByExecutionId(String executionId);
  Optional<TaskExecutionEntity> findByStartRequestId(String startRequestId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from TaskExecutionEntity e where e.taskId = :taskId")
  Optional<TaskExecutionEntity> findByTaskIdForStart(@Param("taskId") String taskId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from TaskExecutionEntity e where e.executionId = :executionId")
  Optional<TaskExecutionEntity> findByExecutionIdForUpdate(@Param("executionId") String executionId);

  List<TaskExecutionEntity> findByStatusIn(Collection<String> statuses);
  List<TaskExecutionEntity> findByRobotIdAndStatusIn(String robotId, Collection<String> statuses);
  java.util.Optional<TaskExecutionEntity> findFirstByRobotIdAndStatusInOrderByUpdatedAtDesc(
      String robotId, Collection<String> statuses);
}
