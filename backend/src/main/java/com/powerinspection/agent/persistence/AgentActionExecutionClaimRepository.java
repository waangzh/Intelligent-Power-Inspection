package com.powerinspection.agent.persistence;

import com.powerinspection.agent.domain.AgentActionExecutionClaimEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentActionExecutionClaimRepository extends JpaRepository<AgentActionExecutionClaimEntity, String> {
  Optional<AgentActionExecutionClaimEntity> findByIdempotencyKey(String idempotencyKey);
}
