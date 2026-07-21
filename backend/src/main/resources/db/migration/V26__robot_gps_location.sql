ALTER TABLE robot_telemetry ADD COLUMN gps_valid BOOLEAN NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_stale BOOLEAN NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_latitude DOUBLE NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_longitude DOUBLE NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_altitude DOUBLE NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_quality INT NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_fix_type VARCHAR(32) NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_satellites INT NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_hdop DOUBLE NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_differential_age DOUBLE NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_base_station_id VARCHAR(64) NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_observed_at TIMESTAMP NULL;
ALTER TABLE robot_telemetry ADD COLUMN gps_received_at TIMESTAMP NULL;

CREATE TABLE robot_location_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    robot_id VARCHAR(128) NOT NULL,
    execution_id VARCHAR(128) NULL,
    task_id VARCHAR(128) NULL,
    observed_at TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    altitude DOUBLE NULL,
    quality INT NULL,
    fix_type VARCHAR(32) NULL,
    satellites INT NULL,
    hdop DOUBLE NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'GNSS',
    PRIMARY KEY (id),
    INDEX idx_robot_location_robot_time (robot_id, observed_at),
    INDEX idx_robot_location_execution_time (execution_id, observed_at)
);

CREATE UNIQUE INDEX uq_robot_location_observed ON robot_location_history(robot_id, observed_at);
