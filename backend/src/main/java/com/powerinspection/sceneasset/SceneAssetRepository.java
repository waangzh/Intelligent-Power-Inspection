package com.powerinspection.sceneasset;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SceneAssetRepository extends JpaRepository<SceneAssetEntity, String> {
  @Query("select asset from SceneAssetEntity asset where "
    + "(:source is null or asset.source = :source) and "
    + "(:status is null or asset.status = :status) and "
    + "(:siteId is null or asset.siteId = :siteId) and "
    + "(:robotId is null or asset.sourceRobotId = :robotId) and "
    + "(:assetKind is null or asset.assetKind = :assetKind) order by asset.createdAt desc")
  List<SceneAssetEntity> search(@Param("source") String source, @Param("status") String status,
      @Param("siteId") String siteId, @Param("robotId") String robotId,
      @Param("assetKind") String assetKind);
}
