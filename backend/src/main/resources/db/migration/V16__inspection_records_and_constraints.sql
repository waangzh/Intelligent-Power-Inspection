CREATE TABLE inspection_records (
  id VARCHAR(64) PRIMARY KEY,
  task_id VARCHAR(64),
  site_id VARCHAR(64),
  route_id VARCHAR(64),
  robot_id VARCHAR(64),
  task_name VARCHAR(160),
  route_name VARCHAR(160),
  robot_name VARCHAR(160),
  alarm_count INT NOT NULL DEFAULT 0,
  checkpoint_count INT NOT NULL DEFAULT 0,
  duration VARCHAR(64),
  summary VARCHAR(1000),
  completed_at VARCHAR(40),
  extra_json LONGTEXT,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_inspection_records_task ON inspection_records(task_id);
CREATE INDEX idx_inspection_records_site_updated ON inspection_records(site_id, updated_at);
CREATE INDEX idx_inspection_records_completed ON inspection_records(completed_at);

-- Enforce one active task per robot at application level; index accelerates checks.
CREATE INDEX idx_inspection_tasks_robot_active ON inspection_tasks(robot_id, status);

CREATE INDEX idx_work_orders_alarm ON work_orders(alarm_id);
CREATE INDEX idx_alarms_work_order ON alarms(work_order_id);
