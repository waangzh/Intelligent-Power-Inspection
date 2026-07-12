package com.powerinspection.route;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteDeploymentRepository extends JpaRepository<RouteDeploymentEntity, String> {
  Optional<RouteDeploymentEntity> findByRequestId(String requestId);
}
