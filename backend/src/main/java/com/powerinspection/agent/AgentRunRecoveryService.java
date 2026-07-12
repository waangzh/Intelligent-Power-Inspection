package com.powerinspection.agent;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class AgentRunRecoveryService {
  private final AgentOrchestrator orchestrator;
  public AgentRunRecoveryService(AgentOrchestrator orchestrator) { this.orchestrator = orchestrator; }
  @EventListener(ApplicationReadyEvent.class)
  public void recover() { orchestrator.recoverExpiredRuns(); }
}
