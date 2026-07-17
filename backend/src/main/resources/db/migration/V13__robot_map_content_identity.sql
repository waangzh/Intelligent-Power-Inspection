ALTER TABLE robot_map_uploads
  ADD COLUMN content_identity_sha256 VARCHAR(64) NULL;

CREATE INDEX idx_robot_map_uploads_content_identity
  ON robot_map_uploads(robot_id, content_identity_sha256);
