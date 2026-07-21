ALTER TABLE robots ADD COLUMN local_confirm_start_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE robot_heartbeat_status ADD COLUMN reported_remote_immediate_start BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE robot_heartbeat_status ADD COLUMN reported_local_confirm_start BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE robot_heartbeat_status ADD COLUMN local_confirm_protocol_version VARCHAR(32);
ALTER TABLE robot_heartbeat_status ADD COLUMN local_confirm_start_ready BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE robot_heartbeat_status ADD COLUMN local_confirm_start_error VARCHAR(128);
ALTER TABLE robot_heartbeat_status ADD COLUMN capability_reported_at TIMESTAMP NULL;

CREATE TABLE robot_local_confirm_policy_audit (
  id VARCHAR(64) PRIMARY KEY,
  robot_id VARCHAR(64) NOT NULL,
  operator_id VARCHAR(64) NOT NULL,
  operator_name VARCHAR(120) NOT NULL,
  previous_enabled BOOLEAN NOT NULL,
  new_enabled BOOLEAN NOT NULL,
  changed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_robot_local_confirm_policy_audit_robot_changed
  ON robot_local_confirm_policy_audit(robot_id, changed_at);
