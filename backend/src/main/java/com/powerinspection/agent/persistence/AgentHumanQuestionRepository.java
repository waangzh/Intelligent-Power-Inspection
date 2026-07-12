package com.powerinspection.agent.persistence;

import com.powerinspection.agent.domain.AgentHumanQuestionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentHumanQuestionRepository extends JpaRepository<AgentHumanQuestionEntity, String> {
  Optional<AgentHumanQuestionEntity> findByIdAndRunId(String id, String runId);
}
