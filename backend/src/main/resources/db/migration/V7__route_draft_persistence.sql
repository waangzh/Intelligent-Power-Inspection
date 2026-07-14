CREATE TABLE route_drafts (
  route_id VARCHAR(100) PRIMARY KEY,
  executor_json LONGTEXT NOT NULL,
  validation_report_json LONGTEXT NOT NULL,
  map_asset_id VARCHAR(100) NOT NULL,
  map_image_sha256 VARCHAR(64) NOT NULL,
  publishable BOOLEAN NOT NULL DEFAULT FALSE,
  checked_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  updated_by VARCHAR(64),
  publishable_executor_json LONGTEXT,
  publishable_validation_report_json LONGTEXT,
  publishable_map_asset_id VARCHAR(100),
  publishable_map_image_sha256 VARCHAR(64),
  publishable_checked_at VARCHAR(40),
  version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_route_drafts_map_asset ON route_drafts(map_asset_id);
