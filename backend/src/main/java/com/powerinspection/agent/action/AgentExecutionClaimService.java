package com.powerinspection.agent.action;

import com.powerinspection.agent.domain.AgentActionExecutionClaimEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.persistence.AgentActionExecutionClaimRepository;
import com.powerinspection.common.Ids;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class AgentExecutionClaimService {
  private final AgentActionExecutionClaimRepository repository;

  public AgentExecutionClaimService(AgentActionExecutionClaimRepository repository) {
    this.repository = repository;
  }

  public synchronized ClaimResult claim(String idempotencyKey, String actionId) {
    Optional<AgentActionExecutionClaimEntity> existing = repository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return new ClaimResult(false, existing.get());
    }
    Instant now = Instant.now();
    AgentActionExecutionClaimEntity item = new AgentActionExecutionClaimEntity();
    item.setId(Ids.next("agent_claim"));
    item.setIdempotencyKey(idempotencyKey);
    item.setActionId(actionId);
    item.setStatus(AgentEnums.ExecutionClaimStatus.EXECUTING);
    item.setCreatedAt(now);
    item.setUpdatedAt(now);
    try {
      return new ClaimResult(true, repository.saveAndFlush(item));
    } catch (DataIntegrityViolationException ex) {
      AgentActionExecutionClaimEntity winner = repository.findByIdempotencyKey(idempotencyKey)
        .orElseThrow(() -> ex);
      return new ClaimResult(false, winner);
    }
  }

  public void complete(AgentActionExecutionClaimEntity claim, AgentEnums.ExecutionClaimStatus status, String resultJson) {
    claim.setStatus(status);
    claim.setResultJson(resultJson);
    claim.setUpdatedAt(Instant.now());
    repository.save(claim);
  }

  public record ClaimResult(boolean owner, AgentActionExecutionClaimEntity claim) {
  }
}
