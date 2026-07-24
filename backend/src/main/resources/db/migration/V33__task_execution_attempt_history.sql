ALTER TABLE task_executions DROP PRIMARY KEY;
ALTER TABLE task_executions ADD PRIMARY KEY (execution_id);
ALTER TABLE task_executions ADD COLUMN previous_execution_id VARCHAR(100);

CREATE INDEX idx_task_executions_task_created ON task_executions(task_id, created_at);

