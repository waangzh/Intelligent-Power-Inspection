package com.powerinspection.agent.persistence;

import com.powerinspection.agent.domain.AgentRunEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgentRunRepository extends JpaRepository<AgentRunEntity, String> {
  List<AgentRunEntity> findByCaseIdOrderByRunNumberDesc(String caseId);

  @Query("select coalesce(max(item.runNumber), 0) from AgentRunEntity item where item.caseId = :caseId")
  int findMaxRunNumber(String caseId);
}
