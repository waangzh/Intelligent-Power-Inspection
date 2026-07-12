package com.powerinspection.agent.persistence;

import com.powerinspection.agent.domain.AgentHumanAnswerEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentHumanAnswerRepository extends JpaRepository<AgentHumanAnswerEntity, String> {
  Optional<AgentHumanAnswerEntity> findByQuestionId(String questionId);
}
