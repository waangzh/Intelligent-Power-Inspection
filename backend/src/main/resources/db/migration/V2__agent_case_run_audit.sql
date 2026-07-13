CREATE TABLE agent_cases (
  id VARCHAR(64) PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  goal LONGTEXT NOT NULL,
  operator_note LONGTEXT,
  trigger_type VARCHAR(32) NOT NULL,
  task_id VARCHAR(64),
  alarm_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  priority VARCHAR(16) NOT NULL,
  created_by_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  resolved_at TIMESTAMP NULL,
  closed_at TIMESTAMP NULL,
  version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE agent_runs (
  id VARCHAR(64) PRIMARY KEY,
  case_id VARCHAR(64) NOT NULL,
  run_number INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  goal_snapshot LONGTEXT NOT NULL,
  input_snapshot_json LONGTEXT NOT NULL,
  conclusion_json LONGTEXT,
  planner_type VARCHAR(32) NOT NULL,
  model_name VARCHAR(128),
  prompt_version VARCHAR(64),
  reanalysis_reason VARCHAR(64),
  started_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  error_code VARCHAR(64),
  error_message LONGTEXT,
  created_by_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_agent_runs_case FOREIGN KEY (case_id) REFERENCES agent_cases(id),
  CONSTRAINT uq_agent_runs_case_number UNIQUE (case_id, run_number)
);

CREATE TABLE agent_steps (
  id VARCHAR(64) PRIMARY KEY,
  case_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  sequence_no INT NOT NULL,
  type VARCHAR(48) NOT NULL,
  summary VARCHAR(500) NOT NULL,
  detail_json LONGTEXT,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_agent_steps_case FOREIGN KEY (case_id) REFERENCES agent_cases(id),
  CONSTRAINT fk_agent_steps_run FOREIGN KEY (run_id) REFERENCES agent_runs(id),
  CONSTRAINT uq_agent_steps_run_sequence UNIQUE (run_id, sequence_no)
);

CREATE TABLE agent_tool_calls (
  id VARCHAR(64) PRIMARY KEY,
  case_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  step_no INT NOT NULL,
  tool_name VARCHAR(96) NOT NULL,
  arguments_json LONGTEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP NULL,
  duration_ms BIGINT NULL,
  result_summary VARCHAR(1000),
  error_code VARCHAR(64),
  error_message LONGTEXT,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_agent_tool_calls_case FOREIGN KEY (case_id) REFERENCES agent_cases(id),
  CONSTRAINT fk_agent_tool_calls_run FOREIGN KEY (run_id) REFERENCES agent_runs(id)
);

CREATE TABLE agent_evidence (
  id VARCHAR(64) PRIMARY KEY,
  case_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  tool_call_id VARCHAR(64),
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(64),
  title VARCHAR(255) NOT NULL,
  summary VARCHAR(1000) NOT NULL,
  content_type VARCHAR(64) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  content_hash VARCHAR(64) NOT NULL,
  collected_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_agent_evidence_case FOREIGN KEY (case_id) REFERENCES agent_cases(id),
  CONSTRAINT fk_agent_evidence_run FOREIGN KEY (run_id) REFERENCES agent_runs(id),
  CONSTRAINT fk_agent_evidence_tool_call FOREIGN KEY (tool_call_id) REFERENCES agent_tool_calls(id)
);

CREATE TABLE agent_actions (
  id VARCHAR(64) PRIMARY KEY,
  case_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  type VARCHAR(64) NOT NULL,
  title VARCHAR(255) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  risk_level VARCHAR(16) NOT NULL,
  status VARCHAR(32) NOT NULL,
  payload_json LONGTEXT NOT NULL,
  policy_decision VARCHAR(32) NOT NULL,
  policy_code VARCHAR(64) NOT NULL,
  requires_approval BOOLEAN NOT NULL,
  idempotency_key VARCHAR(255) NOT NULL,
  approved_by_id VARCHAR(64),
  approved_at TIMESTAMP NULL,
  approval_comment VARCHAR(1000),
  rejected_by_id VARCHAR(64),
  rejected_at TIMESTAMP NULL,
  rejection_comment VARCHAR(1000),
  execution_started_at TIMESTAMP NULL,
  execution_completed_at TIMESTAMP NULL,
  result_json LONGTEXT,
  error_code VARCHAR(64),
  error_message LONGTEXT,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_agent_actions_case FOREIGN KEY (case_id) REFERENCES agent_cases(id),
  CONSTRAINT fk_agent_actions_run FOREIGN KEY (run_id) REFERENCES agent_runs(id)
);

CREATE TABLE agent_action_execution_claims (
  id VARCHAR(64) PRIMARY KEY,
  idempotency_key VARCHAR(255) NOT NULL,
  action_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  result_json LONGTEXT,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT uq_agent_action_execution_claim_key UNIQUE (idempotency_key),
  CONSTRAINT fk_agent_action_execution_claim_action FOREIGN KEY (action_id) REFERENCES agent_actions(id)
);

CREATE INDEX idx_agent_runs_case_created ON agent_runs(case_id, created_at);
CREATE INDEX idx_agent_steps_run_sequence ON agent_steps(run_id, sequence_no);
CREATE INDEX idx_agent_tool_calls_run_created ON agent_tool_calls(run_id, created_at);
CREATE INDEX idx_agent_evidence_run_created ON agent_evidence(run_id, created_at);
CREATE INDEX idx_agent_actions_run_created ON agent_actions(run_id, created_at);
CREATE INDEX idx_agent_actions_idempotency ON agent_actions(idempotency_key);
CREATE INDEX idx_agent_cases_created_by ON agent_cases(created_by_id);
CREATE INDEX idx_agent_runs_created_by ON agent_runs(created_by_id);
