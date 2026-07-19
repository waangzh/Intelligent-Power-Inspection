CREATE TABLE robot_inspection_images (
  id VARCHAR(64) PRIMARY KEY,
  source VARCHAR(32) NOT NULL,
  robot_id VARCHAR(100) NOT NULL,
  task_id VARCHAR(100) NOT NULL,
  execution_id VARCHAR(100),
  route_id VARCHAR(100) NOT NULL,
  route_revision_id VARCHAR(100),
  checkpoint_id VARCHAR(100) NOT NULL,
  checkpoint_name VARCHAR(160) NOT NULL,
  captured_at VARCHAR(40) NOT NULL,
  content_type VARCHAR(64) NOT NULL,
  extension VARCHAR(10) NOT NULL,
  width INT,
  height INT,
  size_bytes BIGINT NOT NULL,
  sha256 VARCHAR(64) NOT NULL,
  storage_key VARCHAR(500),
  status VARCHAR(32) NOT NULL,
  idempotency_key VARCHAR(160),
  created_by VARCHAR(100),
  original_purged_at VARCHAR(40),
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_robot_inspection_image_upload UNIQUE (robot_id, idempotency_key)
);

CREATE INDEX idx_robot_inspection_task_checkpoint ON robot_inspection_images(task_id, checkpoint_id, captured_at);
CREATE INDEX idx_robot_inspection_robot_captured ON robot_inspection_images(robot_id, captured_at);
CREATE INDEX idx_robot_inspection_retention ON robot_inspection_images(status, captured_at);

CREATE TABLE detection_runs (
  id VARCHAR(64) PRIMARY KEY,
  source_type VARCHAR(32) NOT NULL,
  image_id VARCHAR(64),
  task_id VARCHAR(100),
  checkpoint_id VARCHAR(100),
  status VARCHAR(32) NOT NULL,
  detections_json LONGTEXT NOT NULL,
  findings_json LONGTEXT,
  warnings_json LONGTEXT,
  input_image_url VARCHAR(1000),
  result_image_url VARCHAR(1000),
  result_storage_key VARCHAR(500),
  error_message VARCHAR(1000),
  created_by VARCHAR(100),
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  started_at VARCHAR(40),
  completed_at VARCHAR(40),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_detection_runs_image_created ON detection_runs(image_id, created_at);
CREATE INDEX idx_detection_runs_task_created ON detection_runs(task_id, created_at);
CREATE INDEX idx_detection_runs_status_created ON detection_runs(status, created_at);
CREATE INDEX idx_detection_runs_retention ON detection_runs(completed_at);
