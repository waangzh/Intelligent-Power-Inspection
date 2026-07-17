package com.powerinspection.task;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionTaskRepository extends JpaRepository<InspectionTaskEntity, String> {
}
