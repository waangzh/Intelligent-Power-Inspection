package com.powerinspection.detection;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DetectionRunRepository extends JpaRepository<DetectionRunEntity, String> {
  List<DetectionRunEntity> findByStatus(String status);
  List<DetectionRunEntity> findByCompletedAtIsNotNullAndCompletedAtLessThan(String completedAt);

  @Query("select run from DetectionRunEntity run where "
    + "(:taskId is null or run.taskId = :taskId) and "
    + "(:imageId is null or run.imageId = :imageId) and "
    + "(:status is null or run.status = :status)")
  Page<DetectionRunEntity> search(@Param("taskId") String taskId, @Param("imageId") String imageId,
    @Param("status") String status, Pageable pageable);
}
