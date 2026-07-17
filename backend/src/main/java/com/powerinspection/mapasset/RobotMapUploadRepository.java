package com.powerinspection.mapasset;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RobotMapUploadRepository extends JpaRepository<RobotMapUploadEntity, Long> {
  Optional<RobotMapUploadEntity> findByRobotIdAndIdempotencyKey(String robotId, String idempotencyKey);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("update RobotMapUploadEntity upload set upload.status = 'PROCESSING', upload.updatedAt = :updatedAt "
    + "where upload.id = :id and upload.status = 'FAILED'")
  int claimFailed(@Param("id") Long id, @Param("updatedAt") String updatedAt);
}
