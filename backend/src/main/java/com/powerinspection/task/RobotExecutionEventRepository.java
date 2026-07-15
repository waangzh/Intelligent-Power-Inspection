package com.powerinspection.task;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotExecutionEventRepository extends JpaRepository<RobotExecutionEventEntity, String> {
  Optional<RobotExecutionEventEntity> findByRobotIdAndSequence(String robotId, long sequence);
  Optional<RobotExecutionEventEntity> findByEventId(String eventId);
}
