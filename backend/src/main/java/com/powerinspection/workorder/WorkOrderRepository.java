package com.powerinspection.workorder;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrderEntity, String> {
  Optional<WorkOrderEntity> findByAlarmId(String alarmId);

  boolean existsByAlarmId(String alarmId);
}
