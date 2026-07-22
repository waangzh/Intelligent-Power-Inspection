package com.powerinspection.robot;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RobotLocationHistoryRepository extends JpaRepository<RobotLocationHistoryEntity, Long> {
  boolean existsByRobotIdAndObservedAt(String robotId, Instant observedAt);

  @Query("""
      select h from RobotLocationHistoryEntity h
      where h.robotId = :robotId
        and h.observedAt >= :start
        and h.observedAt <= :end
        and (:executionId is null or h.executionId = :executionId)
      order by h.observedAt asc
      """)
  List<RobotLocationHistoryEntity> findTrack(
      @Param("robotId") String robotId,
      @Param("start") Instant start,
      @Param("end") Instant end,
      @Param("executionId") String executionId,
      Pageable pageable);

  RobotLocationHistoryEntity findTopByRobotIdOrderByObservedAtDesc(String robotId);

  boolean existsByRobotIdAndExecutionIdAndRobotStateIn(
      String robotId, String executionId, List<String> robotStates);

  long deleteByObservedAtBefore(Instant cutoff);
}
