package com.powerinspection.agent.tool;

public class AgentToolExecutionException extends RuntimeException {
  private final String code;

  public AgentToolExecutionException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() { return code; }
}
