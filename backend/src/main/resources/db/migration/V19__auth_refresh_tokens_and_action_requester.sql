CREATE TABLE refresh_tokens (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  token_hash VARCHAR(128) NOT NULL,
  family_id VARCHAR(64) NOT NULL,
  remember BOOLEAN NOT NULL DEFAULT FALSE,
  expires_at VARCHAR(40) NOT NULL,
  revoked_at VARCHAR(40),
  replaced_by_id VARCHAR(64),
  created_at VARCHAR(40) NOT NULL,
  CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
  CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);

ALTER TABLE agent_actions ADD COLUMN requested_by_id VARCHAR(64);

UPDATE agent_actions
SET requested_by_id = (
  SELECT r.created_by_id FROM agent_runs r WHERE r.id = agent_actions.run_id
)
WHERE requested_by_id IS NULL;

CREATE INDEX idx_agent_actions_requested_by ON agent_actions(requested_by_id);
