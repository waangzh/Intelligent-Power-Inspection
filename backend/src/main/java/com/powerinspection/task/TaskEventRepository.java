package com.powerinspection.task;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskEventRepository extends JpaRepository<TaskEventEntity, String> {
}
