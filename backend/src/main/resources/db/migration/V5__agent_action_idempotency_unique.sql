-- V5: add UNIQUE constraint on agent_actions(idempotency_key) to prevent duplicate actions at the database level.
-- Remove duplicates (if any), keeping the earliest created row per idempotency_key.
DELETE FROM agent_actions WHERE id IN (
  SELECT id FROM (
    SELECT a1.id FROM agent_actions a1
    INNER JOIN agent_actions a2 ON a1.idempotency_key = a2.idempotency_key
    WHERE a1.created_at > a2.created_at OR (a1.created_at = a2.created_at AND a1.id > a2.id)
  ) AS duplicates_to_remove
);

ALTER TABLE agent_actions ADD CONSTRAINT uq_agent_actions_idempotency UNIQUE (idempotency_key);

CREATE INDEX idx_agent_action_execution_claims_action ON agent_action_execution_claims(action_id);
