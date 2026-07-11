package com.powerinspection.agent.persistence;

import com.powerinspection.agent.domain.AgentToolCallEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentToolCallRepository extends JpaRepository<AgentToolCallEntity, String> {
  List<AgentToolCallEntity> findByRunIdOrderByCreatedAtAsc(String runId);
}
