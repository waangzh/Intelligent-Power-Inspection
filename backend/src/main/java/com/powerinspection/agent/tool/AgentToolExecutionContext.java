package com.powerinspection.agent.tool;

import com.powerinspection.user.UserEntity;

public record AgentToolExecutionContext(String caseId, String runId, UserEntity user) {
}
