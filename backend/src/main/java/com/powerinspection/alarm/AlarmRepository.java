package com.powerinspection.alarm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlarmRepository extends JpaRepository<AlarmEntity, String>, JpaSpecificationExecutor<AlarmEntity> {
}
