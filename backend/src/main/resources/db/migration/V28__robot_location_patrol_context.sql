ALTER TABLE robot_telemetry ADD COLUMN last_execution_id VARCHAR(128) NULL;
ALTER TABLE robot_location_history ADD COLUMN route_id VARCHAR(128) NULL;
ALTER TABLE robot_location_history ADD COLUMN target_id VARCHAR(128) NULL;
ALTER TABLE robot_location_history ADD COLUMN cycle_index INT NULL;
ALTER TABLE robot_location_history ADD COLUMN robot_state VARCHAR(32) NULL;
ALTER TABLE robot_location_history ADD COLUMN navigation_phase VARCHAR(32) NULL;
