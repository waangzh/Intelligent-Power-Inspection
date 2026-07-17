-- Core domain tables: operational entities leave the JSON bag.
-- Extensible AI/device/agent payloads stay in LONGTEXT columns.

CREATE TABLE sites (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  address VARCHAR(255),
  description VARCHAR(500),
  center_lat DOUBLE,
  center_lng DOUBLE,
  status VARCHAR(32),
  device_map_uploaded BOOLEAN NOT NULL DEFAULT FALSE,
  lingbot_map_id VARCHAR(128),
  extra_json LONGTEXT,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE routes (
  id VARCHAR(64) PRIMARY KEY,
  site_id VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  map_id VARCHAR(64),
  status VARCHAR(32),
  executor_json LONGTEXT,
  checkpoints_json LONGTEXT,
  extra_json LONGTEXT,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_routes_site_updated ON routes(site_id, updated_at);

CREATE TABLE robots (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  model VARCHAR(120),
  serial_no VARCHAR(120),
  site_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  position_lat DOUBLE,
  position_lng DOUBLE,
  extra_json LONGTEXT,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_robots_site_status ON robots(site_id, status);

CREATE TABLE robot_telemetry (
  robot_id VARCHAR(64) PRIMARY KEY,
  patrol_state VARCHAR(64),
  system_mode VARCHAR(64),
  mapping_status VARCHAR(64),
  nav2_status VARCHAR(64),
  can_status VARCHAR(64),
  zlac_status VARCHAR(64),
  pose_x DOUBLE,
  pose_y DOUBLE,
  pose_yaw DOUBLE,
  last_odom_age_sec DOUBLE,
  last_scan_age_sec DOUBLE,
  payload_json LONGTEXT,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE inspection_tasks (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  site_id VARCHAR(64),
  route_id VARCHAR(64) NOT NULL,
  robot_id VARCHAR(64) NOT NULL,
  route_revision_id VARCHAR(64),
  execution_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  progress INT NOT NULL DEFAULT 0,
  current_checkpoint_seq INT NOT NULL DEFAULT 0,
  started_at VARCHAR(40),
  completed_at VARCHAR(40),
  extra_json LONGTEXT,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_inspection_tasks_robot_status ON inspection_tasks(robot_id, status);
CREATE INDEX idx_inspection_tasks_status_updated ON inspection_tasks(status, updated_at);
CREATE INDEX idx_inspection_tasks_site_updated ON inspection_tasks(site_id, updated_at);

CREATE TABLE task_events (
  id VARCHAR(64) PRIMARY KEY,
  task_id VARCHAR(64) NOT NULL,
  type VARCHAR(64) NOT NULL,
  message VARCHAR(500),
  checkpoint_name VARCHAR(160),
  image_url VARCHAR(500),
  payload_json LONGTEXT,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_task_events_task_created ON task_events(task_id, created_at);

CREATE TABLE alarms (
  id VARCHAR(64) PRIMARY KEY,
  site_id VARCHAR(64),
  route_id VARCHAR(64),
  robot_id VARCHAR(64),
  task_id VARCHAR(64),
  type VARCHAR(64),
  severity VARCHAR(32) NOT NULL,
  status VARCHAR(32),
  message VARCHAR(500) NOT NULL,
  route_name VARCHAR(160),
  checkpoint_name VARCHAR(160),
  image_url VARCHAR(500),
  acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
  work_order_id VARCHAR(64),
  work_order_mode_applied VARCHAR(32),
  work_order_conversion_source VARCHAR(32),
  work_order_conversion_status VARCHAR(32),
  work_order_conversion_error VARCHAR(500),
  work_order_conversion_failed_at VARCHAR(40),
  converted_at VARCHAR(40),
  extra_json LONGTEXT,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_alarms_severity_ack_updated ON alarms(severity, acknowledged, updated_at);
CREATE INDEX idx_alarms_task_updated ON alarms(task_id, updated_at);
CREATE INDEX idx_alarms_site_updated ON alarms(site_id, updated_at);

CREATE TABLE work_orders (
  id VARCHAR(64) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  description LONGTEXT,
  location_description VARCHAR(255),
  alarm_id VARCHAR(64),
  task_id VARCHAR(64),
  source VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  priority VARCHAR(32) NOT NULL,
  assignee_id VARCHAR(64),
  assignee_name VARCHAR(120),
  created_by_id VARCHAR(64) NOT NULL,
  created_by_name VARCHAR(120) NOT NULL,
  agent_action_id VARCHAR(64),
  agent_idempotency_key VARCHAR(128),
  claimed_at VARCHAR(40),
  closed_at VARCHAR(40),
  resolution LONGTEXT,
  review_json LONGTEXT,
  extra_json LONGTEXT,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_work_orders_alarm UNIQUE (alarm_id)
);

CREATE INDEX idx_work_orders_status_updated ON work_orders(status, updated_at);
CREATE INDEX idx_work_orders_assignee ON work_orders(assignee_id, status);

CREATE TABLE work_order_transitions (
  id VARCHAR(64) PRIMARY KEY,
  work_order_id VARCHAR(64) NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  actor_id VARCHAR(64),
  remark LONGTEXT,
  created_at VARCHAR(40) NOT NULL,
  CONSTRAINT fk_work_order_transitions_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id)
);

CREATE INDEX idx_work_order_transitions_order_created ON work_order_transitions(work_order_id, created_at);

CREATE TABLE notifications (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  type VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  link VARCHAR(255),
  read_flag BOOLEAN NOT NULL DEFAULT FALSE,
  extra_json LONGTEXT,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notifications_user_read_created ON notifications(user_id, read_flag, created_at);
