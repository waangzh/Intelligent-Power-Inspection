package com.powerinspection.agent.planner;

public class PlannerValidationException extends RuntimeException {
  private final String code;

  public PlannerValidationException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
