package com.powerinspection.robot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RobotRepository extends JpaRepository<RobotEntity, String> {
}
