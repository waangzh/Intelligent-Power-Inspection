package com.powerinspection.route;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RouteDeploymentRepository extends JpaRepository<RouteDeploymentEntity, String> {
  Optional<RouteDeploymentEntity> findByRequestId(String requestId);

  List<RouteDeploymentEntity> findByRouteRevisionIdOrderByCreatedAtDesc(String routeRevisionId);

  Optional<RouteDeploymentEntity> findFirstByRobotIdAndStateInOrderByCreatedAtDesc(String robotId, Collection<String> states);

  List<RouteDeploymentEntity> findByRobotIdAndRouteRevisionIdAndStateOrderByCreatedAtDesc(String robotId, String routeRevisionId, String state);

  List<RouteDeploymentEntity> findByState(String state);

  @Query("select d.id from RouteDeploymentEntity d where d.state = :pending or (d.state = :unknown and d.nextReconcileAt is not null and d.nextReconcileAt <= :now) order by d.createdAt asc")
  List<String> findEligibleIds(@Param("pending") String pending, @Param("unknown") String unknown, @Param("now") String now, Pageable pageable);

  @Modifying
  @Transactional
  @Query("update RouteDeploymentEntity d set d.state = :installing, d.attemptNo = d.attemptNo + 1, d.lastAttemptAt = :now, d.nextReconcileAt = null, d.errorCode = null, d.errorMessage = null, d.updatedAt = :now, d.version = d.version + 1 where d.id = :id and (d.state = :pending or (d.state = :unknown and d.nextReconcileAt is not null and d.nextReconcileAt <= :now))")
  int claimForInstall(@Param("id") String id, @Param("pending") String pending, @Param("unknown") String unknown,
      @Param("installing") String installing, @Param("now") String now);
}
