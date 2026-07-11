package com.powerinspection.agent.persistence;

import com.powerinspection.agent.domain.AgentStepEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AgentStepRepository extends JpaRepository<AgentStepEntity, String> {
  List<AgentStepEntity> findByRunIdOrderBySequenceNoAsc(String runId);

  @Query("select coalesce(max(item.sequenceNo), 0) from AgentStepEntity item where item.runId = :runId")
  int findMaxSequenceNo(String runId);
}
