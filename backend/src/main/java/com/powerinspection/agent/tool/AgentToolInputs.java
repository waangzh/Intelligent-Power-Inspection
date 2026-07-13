package com.powerinspection.agent.tool;

public final class AgentToolInputs {
  private AgentToolInputs() {
  }

  public record TaskIdInput(String taskId) {
  }

  public record AlarmIdInput(String alarmId) {
  }

  public record RobotIdInput(String robotId) {
  }

  public record RouteIdInput(String routeId) {
  }

  public record RelatedWorkOrdersInput(String alarmId, String taskId) {
  }

  public record InspectAlarmImageInput(String alarmId, String taskId) {
  }
}
