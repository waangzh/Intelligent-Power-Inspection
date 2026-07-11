package com.powerinspection.agent.persistence;

import com.powerinspection.agent.domain.AgentEvidenceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentEvidenceRepository extends JpaRepository<AgentEvidenceEntity, String> {
  List<AgentEvidenceEntity> findByRunIdOrderByCreatedAtAsc(String runId);
}
