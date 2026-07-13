package com.powerinspection.agent.persistence;

import com.powerinspection.agent.domain.AgentCaseEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AgentCaseRepository extends JpaRepository<AgentCaseEntity, String> {
  List<AgentCaseEntity> findAllByOrderByUpdatedAtDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select item from AgentCaseEntity item where item.id = :id")
  Optional<AgentCaseEntity> lockById(String id);
}
