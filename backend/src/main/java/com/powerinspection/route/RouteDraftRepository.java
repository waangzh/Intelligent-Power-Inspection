package com.powerinspection.route;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteDraftRepository extends JpaRepository<RouteDraftEntity, String> {
  Optional<RouteDraftEntity> findByRouteId(String routeId);
  boolean existsByMapAssetId(String mapAssetId);
}
