CREATE TABLE route_revisions (
  id VARCHAR(100) PRIMARY KEY,
  route_id VARCHAR(100) NOT NULL,
  revision_no BIGINT NOT NULL,
  executor_json LONGTEXT NOT NULL,
  content_sha256 VARCHAR(64) NOT NULL,
  map_asset_id VARCHAR(100) NOT NULL,
  map_image_sha256 VARCHAR(64) NOT NULL,
  validation_report_json LONGTEXT NOT NULL,
  created_by VARCHAR(64),
  created_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_route_revisions_route_revision UNIQUE (route_id, revision_no),
  CONSTRAINT uq_route_revisions_route_content UNIQUE (route_id, content_sha256)
);

CREATE TABLE route_deployments (
  id VARCHAR(100) PRIMARY KEY,
  route_revision_id VARCHAR(100) NOT NULL,
  robot_id VARCHAR(100) NOT NULL,
  request_id VARCHAR(160) NOT NULL,
  state VARCHAR(32) NOT NULL,
  attempt_no INT NOT NULL DEFAULT 1,
  remote_summary_json LONGTEXT,
  error_code VARCHAR(64),
  error_message VARCHAR(500),
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_route_deployments_request UNIQUE (request_id)
);

CREATE TABLE task_executions (
  task_id VARCHAR(100) PRIMARY KEY,
  execution_id VARCHAR(100) NOT NULL UNIQUE,
  route_revision_id VARCHAR(100) NOT NULL,
  robot_id VARCHAR(100) NOT NULL,
  route_content_sha256 VARCHAR(64) NOT NULL,
  map_image_sha256 VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  last_robot_sequence BIGINT NOT NULL DEFAULT 0,
  last_error_code VARCHAR(64),
  last_error_message VARCHAR(500),
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_route_deployments_revision_robot ON route_deployments(route_revision_id, robot_id);
CREATE INDEX idx_task_executions_robot_status ON task_executions(robot_id, status);
