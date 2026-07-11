package com.powerinspection.agent.persistence;

import com.powerinspection.agent.domain.AgentActionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentActionRepository extends JpaRepository<AgentActionEntity, String> {
  List<AgentActionEntity> findByRunIdOrderByCreatedAtAsc(String runId);
  List<AgentActionEntity> findByIdempotencyKeyOrderByCreatedAtAsc(String idempotencyKey);
}
