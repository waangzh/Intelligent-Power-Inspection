ALTER TABLE task_executions ADD COLUMN deployment_id VARCHAR(100);
ALTER TABLE task_executions ADD COLUMN executor_route_id VARCHAR(100);
ALTER TABLE task_executions ADD COLUMN start_request_id VARCHAR(160);
ALTER TABLE task_executions ADD COLUMN start_request_fingerprint VARCHAR(64);
ALTER TABLE task_executions ADD COLUMN start_command_id VARCHAR(128);
ALTER TABLE task_executions ADD COLUMN start_attempt_no INT NOT NULL DEFAULT 0;
ALTER TABLE task_executions ADD COLUMN last_start_attempt_at VARCHAR(40);
ALTER TABLE task_executions ADD COLUMN recovery_status VARCHAR(32);
ALTER TABLE task_executions ADD COLUMN current_target_id VARCHAR(100);
ALTER TABLE task_executions ADD COLUMN progress INT NOT NULL DEFAULT 0;
ALTER TABLE task_executions ADD COLUMN last_event_at VARCHAR(40);
ALTER TABLE task_executions ADD COLUMN manual_reconciliation_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE task_executions ADD CONSTRAINT uq_task_executions_start_request UNIQUE (start_request_id);
CREATE INDEX idx_task_executions_status_updated ON task_executions(status, updated_at);

CREATE TABLE robot_execution_events (
  id VARCHAR(100) PRIMARY KEY,
  robot_id VARCHAR(100) NOT NULL,
  execution_id VARCHAR(100) NOT NULL,
  deployment_id VARCHAR(100) NOT NULL,
  event_id VARCHAR(180) NOT NULL,
  sequence BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  occurred_at VARCHAR(40) NOT NULL,
  received_at VARCHAR(40) NOT NULL,
  payload_summary LONGTEXT,
  error_code VARCHAR(64),
  error_message VARCHAR(500),
  processing_result VARCHAR(64) NOT NULL,
  conflict_code VARCHAR(64),
  CONSTRAINT uq_robot_execution_events_event_id UNIQUE (event_id),
  CONSTRAINT uq_robot_execution_events_robot_sequence UNIQUE (robot_id, sequence)
);

CREATE INDEX idx_robot_execution_events_execution_sequence ON robot_execution_events(execution_id, sequence);
