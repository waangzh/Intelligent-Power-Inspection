ALTER TABLE agent_actions ADD COLUMN evidence_ids_json LONGTEXT NOT NULL DEFAULT '[]';
ALTER TABLE agent_actions ADD COLUMN payload_audit_json LONGTEXT;
ALTER TABLE agent_actions ADD COLUMN policy_reason LONGTEXT NOT NULL DEFAULT '';
ALTER TABLE agent_actions ADD COLUMN confidence DOUBLE NOT NULL DEFAULT 0;

CREATE TABLE agent_human_questions (
  id VARCHAR(64) PRIMARY KEY,
  case_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  question_type VARCHAR(64) NOT NULL,
  prompt LONGTEXT NOT NULL,
  options_json LONGTEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  answered_at TIMESTAMP NULL,
  answered_by_id VARCHAR(64),
  CONSTRAINT fk_agent_human_questions_case FOREIGN KEY (case_id) REFERENCES agent_cases(id),
  CONSTRAINT fk_agent_human_questions_run FOREIGN KEY (run_id) REFERENCES agent_runs(id)
);

CREATE TABLE agent_human_answers (
  id VARCHAR(64) PRIMARY KEY,
  question_id VARCHAR(64) NOT NULL,
  case_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL,
  mode VARCHAR(48) NOT NULL,
  answer_text LONGTEXT,
  attachment_ids_json LONGTEXT NOT NULL,
  answer_user_id VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT uq_agent_human_answers_question UNIQUE (question_id),
  CONSTRAINT fk_agent_human_answers_question FOREIGN KEY (question_id) REFERENCES agent_human_questions(id),
  CONSTRAINT fk_agent_human_answers_case FOREIGN KEY (case_id) REFERENCES agent_cases(id),
  CONSTRAINT fk_agent_human_answers_run FOREIGN KEY (run_id) REFERENCES agent_runs(id)
);

CREATE INDEX idx_agent_human_questions_run ON agent_human_questions(run_id, created_at);
CREATE INDEX idx_agent_human_answers_run ON agent_human_answers(run_id, created_at);
