package com.powerinspection.workorder;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderTransitionRepository extends JpaRepository<WorkOrderTransitionEntity, String> {
  List<WorkOrderTransitionEntity> findByWorkOrderIdOrderByCreatedAtAsc(String workOrderId);
}
