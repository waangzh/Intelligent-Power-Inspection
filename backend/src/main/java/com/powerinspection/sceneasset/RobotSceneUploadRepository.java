package com.powerinspection.sceneasset;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RobotSceneUploadRepository extends JpaRepository<RobotSceneUploadEntity, Long> {
  Optional<RobotSceneUploadEntity> findByRobotIdAndIdempotencyKey(String robotId, String idempotencyKey);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Transactional
  @Query("update RobotSceneUploadEntity upload set upload.status = 'PROCESSING', upload.updatedAt = :updatedAt "
    + "where upload.id = :id and (upload.status = 'FAILED' or "
    + "(upload.status = 'PROCESSING' and upload.updatedAt < :cutoff))")
  int claimRetryable(@Param("id") Long id, @Param("cutoff") String cutoff, @Param("updatedAt") String updatedAt);

  @Transactional
  void deleteBySceneAssetId(String sceneAssetId);
}
