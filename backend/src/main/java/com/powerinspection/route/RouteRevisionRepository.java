package com.powerinspection.route;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRevisionRepository extends JpaRepository<RouteRevisionEntity, String> {
  List<RouteRevisionEntity> findByRouteIdOrderByRevisionNoDesc(String routeId);

  Optional<RouteRevisionEntity> findByRouteIdAndContentSha256(String routeId, String contentSha256);

  Optional<RouteRevisionEntity> findTopByRouteIdOrderByRevisionNoDesc(String routeId);

  boolean existsByMapAssetId(String mapAssetId);
}
