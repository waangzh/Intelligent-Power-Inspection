package com.powerinspection.robot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotTelemetryRepository extends JpaRepository<RobotTelemetryEntity, String> {
}
