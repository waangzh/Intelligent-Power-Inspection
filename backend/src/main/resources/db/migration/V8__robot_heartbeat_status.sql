CREATE TABLE robot_heartbeat_status (
  robot_id VARCHAR(128) NOT NULL,
  connection_status VARCHAR(32) NOT NULL,
  offline_reason VARCHAR(64),
  last_heartbeat_at TIMESTAMP NULL,
  last_online_at TIMESTAMP NULL,
  status_updated_at TIMESTAMP NOT NULL,
  source_name VARCHAR(64) NOT NULL,
  bridge_configured BOOLEAN NULL,
  protocol_version VARCHAR(32),
  boot_id VARCHAR(128),
  software_version VARCHAR(128),
  robot_state VARCHAR(64),
  accepted_event_sequence BIGINT NOT NULL DEFAULT 0,
  diagnostic_summary VARCHAR(1000),
  PRIMARY KEY (robot_id)
);
