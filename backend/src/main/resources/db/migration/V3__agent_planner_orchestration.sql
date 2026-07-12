ALTER TABLE agent_runs ADD COLUMN degraded BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE agent_runs ADD COLUMN degradation_reason VARCHAR(500);
ALTER TABLE agent_runs ADD COLUMN pending_question_json LONGTEXT;

ALTER TABLE agent_tool_calls ADD COLUMN sequence_no INT;
ALTER TABLE agent_tool_calls ADD COLUMN arguments_hash VARCHAR(64);

UPDATE agent_tool_calls SET sequence_no = step_no WHERE sequence_no IS NULL;

CREATE INDEX idx_agent_tool_calls_run_name_hash ON agent_tool_calls(run_id, tool_name, arguments_hash);
