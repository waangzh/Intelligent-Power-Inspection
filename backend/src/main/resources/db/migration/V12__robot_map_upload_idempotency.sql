CREATE TABLE robot_map_uploads (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  robot_id VARCHAR(100) NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  yaml_sha256 VARCHAR(64) NOT NULL,
  pgm_sha256 VARCHAR(64) NOT NULL,
  map_asset_id VARCHAR(100),
  status VARCHAR(32) NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  CONSTRAINT uq_robot_map_uploads_robot_key UNIQUE (robot_id, idempotency_key)
);

CREATE INDEX idx_robot_map_uploads_asset ON robot_map_uploads(map_asset_id);
