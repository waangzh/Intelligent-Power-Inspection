ALTER TABLE route_deployments ADD COLUMN last_attempt_at VARCHAR(40);
ALTER TABLE route_deployments ADD COLUMN next_reconcile_at VARCHAR(40);

CREATE INDEX idx_route_deployments_reconcile ON route_deployments(state, next_reconcile_at);
