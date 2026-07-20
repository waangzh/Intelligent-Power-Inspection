ALTER TABLE task_executions ADD COLUMN start_mode VARCHAR(32) NOT NULL DEFAULT 'REMOTE_IMMEDIATE';
ALTER TABLE task_executions ADD COLUMN operator_id VARCHAR(100);
ALTER TABLE task_executions ADD COLUMN start_requested_at VARCHAR(40);
ALTER TABLE task_executions ADD COLUMN robot_ready_at VARCHAR(40);
ALTER TABLE task_executions ADD COLUMN local_confirmed_at VARCHAR(40);
ALTER TABLE task_executions ADD COLUMN started_at VARCHAR(40);

CREATE INDEX idx_task_executions_start_mode_status ON task_executions(start_mode, status);
