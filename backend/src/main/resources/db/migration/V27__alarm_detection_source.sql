ALTER TABLE alarms ADD COLUMN source_type VARCHAR(32) NULL;
ALTER TABLE alarms ADD COLUMN detection_run_id VARCHAR(64) NULL;
ALTER TABLE alarms ADD COLUMN image_id VARCHAR(64) NULL;
ALTER TABLE alarms ADD COLUMN checkpoint_id VARCHAR(100) NULL;
ALTER TABLE alarms ADD COLUMN item_id VARCHAR(255) NULL;
ALTER TABLE alarms ADD COLUMN finding_key VARCHAR(512) NULL;

CREATE INDEX idx_alarms_detection_run ON alarms(detection_run_id, created_at);
