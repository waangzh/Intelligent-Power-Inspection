CREATE TABLE task_execution_control_commands (
  id VARCHAR(100) PRIMARY KEY,
  task_id VARCHAR(100) NOT NULL,
  execution_id VARCHAR(100) NOT NULL,
  robot_id VARCHAR(100) NOT NULL,
  deployment_id VARCHAR(100) NOT NULL,
  action VARCHAR(32) NOT NULL,
  request_id VARCHAR(160) NOT NULL,
  request_fingerprint VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  command_id VARCHAR(128),
  prior_execution_status VARCHAR(32) NOT NULL,
  takeover_reason VARCHAR(500),
  requested_by_id VARCHAR(100) NOT NULL,
  requested_by_name VARCHAR(100) NOT NULL,
  requested_at VARCHAR(40) NOT NULL,
  last_attempt_at VARCHAR(40),
  acked_at VARCHAR(40),
  confirmed_at VARCHAR(40),
  recovery_action VARCHAR(64),
  result_code VARCHAR(64),
  result_message VARCHAR(500),
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_task_execution_control_request UNIQUE (request_id)
);

CREATE INDEX idx_task_execution_control_execution_status ON task_execution_control_commands(execution_id, status, requested_at);
