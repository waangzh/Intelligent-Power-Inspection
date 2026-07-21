package com.powerinspection.robot;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotLocalConfirmPolicyAuditRepository
    extends JpaRepository<RobotLocalConfirmPolicyAuditEntity, String> {
  List<RobotLocalConfirmPolicyAuditEntity> findByRobotIdOrderByChangedAtDesc(String robotId);
}
