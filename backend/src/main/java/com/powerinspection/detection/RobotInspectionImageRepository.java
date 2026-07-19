package com.powerinspection.detection;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RobotInspectionImageRepository extends JpaRepository<RobotInspectionImageEntity, String> {
  Optional<RobotInspectionImageEntity> findByRobotIdAndIdempotencyKey(String robotId, String idempotencyKey);

  @Query("select image from RobotInspectionImageEntity image where "
    + "(:taskId is null or image.taskId = :taskId) and "
    + "(:checkpointId is null or image.checkpointId = :checkpointId) and "
    + "(:robotId is null or image.robotId = :robotId) and "
    + "(:capturedFrom is null or image.capturedAt >= :capturedFrom) and "
    + "(:capturedTo is null or image.capturedAt <= :capturedTo)")
  Page<RobotInspectionImageEntity> search(
    @Param("taskId") String taskId,
    @Param("checkpointId") String checkpointId,
    @Param("robotId") String robotId,
    @Param("capturedFrom") String capturedFrom,
    @Param("capturedTo") String capturedTo,
    Pageable pageable);

  List<RobotInspectionImageEntity> findByStorageKeyIsNotNullAndCapturedAtLessThan(String capturedAt);
}
