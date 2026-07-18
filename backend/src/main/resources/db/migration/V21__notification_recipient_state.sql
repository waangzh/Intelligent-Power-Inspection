CREATE TABLE notification_recipient (
  notification_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  read_at VARCHAR(40),
  deleted_at VARCHAR(40),
  PRIMARY KEY (notification_id, user_id),
  CONSTRAINT fk_notification_recipient_notification
    FOREIGN KEY (notification_id) REFERENCES notifications(id),
  CONSTRAINT fk_notification_recipient_user
    FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE INDEX idx_notification_recipient_user_state
  ON notification_recipient(user_id, deleted_at, read_at);

INSERT INTO notification_recipient (notification_id, user_id, read_at, deleted_at)
SELECT id, user_id,
  CASE WHEN read_flag = TRUE THEN updated_at ELSE NULL END,
  NULL
FROM notifications
WHERE user_id <> '*';
