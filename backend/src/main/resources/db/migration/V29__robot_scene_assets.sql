CREATE TABLE scene_assets (
  id VARCHAR(64) PRIMARY KEY,
  site_id VARCHAR(100) NOT NULL,
  status VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  source_robot_id VARCHAR(100),
  source_bridge_robot_id VARCHAR(100),
  asset_kind VARCHAR(32) NOT NULL,
  format VARCHAR(16) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(100),
  file_size BIGINT NOT NULL,
  model_sha256 VARCHAR(64) NOT NULL,
  metadata_sha256 VARCHAR(64) NOT NULL,
  source_capture_session_id VARCHAR(160),
  source_reconstruct_session_id VARCHAR(160) NOT NULL,
  reconstruct_profile VARCHAR(160),
  coordinate_system VARCHAR(64) NOT NULL,
  unit VARCHAR(32) NOT NULL,
  point_count BIGINT NOT NULL,
  reported_point_count BIGINT,
  captured_at VARCHAR(40),
  reconstructed_at VARCHAR(40) NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  reviewed_by VARCHAR(100),
  reviewed_at VARCHAR(40),
  review_comment VARCHAR(1000),
  storage_key VARCHAR(500),
  preview_storage_key VARCHAR(500),
  files_ready BOOLEAN NOT NULL DEFAULT FALSE,
  scene_frame VARCHAR(100),
  reference_frame VARCHAR(100),
  scene_to_reference_transform LONGTEXT,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_scene_assets_status_created ON scene_assets(status, created_at);
CREATE INDEX idx_scene_assets_site_status ON scene_assets(site_id, status);
CREATE INDEX idx_scene_assets_robot_created ON scene_assets(source_robot_id, created_at);
CREATE INDEX idx_scene_assets_model_sha ON scene_assets(model_sha256);

CREATE TABLE robot_scene_uploads (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  robot_id VARCHAR(100) NOT NULL,
  idempotency_key VARCHAR(160) NOT NULL,
  model_sha256 VARCHAR(64) NOT NULL,
  scene_asset_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  CONSTRAINT uq_robot_scene_uploads_robot_key UNIQUE (robot_id, idempotency_key)
);

CREATE INDEX idx_robot_scene_uploads_asset ON robot_scene_uploads(scene_asset_id);
CREATE INDEX idx_robot_scene_uploads_status_updated ON robot_scene_uploads(status, updated_at);
