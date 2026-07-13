package com.powerinspection.agent.planner;

/** Decisions that may be acted on by the phase-two orchestrator. */
public enum PlannerDecisionType {
  CALL_TOOL,
  ASK_HUMAN,
  FINISH,
  PROPOSE_ACTION
}
