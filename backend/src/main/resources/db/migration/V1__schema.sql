CREATE TABLE app_users (
  id VARCHAR(64) PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(128) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  role VARCHAR(32) NOT NULL,
  phone VARCHAR(32),
  avatar_url TEXT,
  bio VARCHAR(255),
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40)
);

CREATE TABLE user_preferences (
  user_id VARCHAR(64) PRIMARY KEY,
  notify_alarm BOOLEAN NOT NULL DEFAULT TRUE,
  notify_task BOOLEAN NOT NULL DEFAULT TRUE,
  notify_system BOOLEAN NOT NULL DEFAULT TRUE,
  default_site_id VARCHAR(64),
  sidebar_collapsed BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE TABLE user_activities (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  type VARCHAR(32) NOT NULL,
  message VARCHAR(255) NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  CONSTRAINT fk_user_activities_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE TABLE data_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category VARCHAR(64) NOT NULL,
  record_id VARCHAR(100) NOT NULL,
  payload LONGTEXT NOT NULL,
  created_at VARCHAR(40) NOT NULL,
  updated_at VARCHAR(40) NOT NULL,
  CONSTRAINT uq_data_records_category_record UNIQUE (category, record_id)
);

CREATE INDEX idx_data_records_category ON data_records(category);
CREATE INDEX idx_user_activities_user_created ON user_activities(user_id, created_at);
