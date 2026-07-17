package com.powerinspection.route;

public enum RouteDeploymentState {
  PENDING("待同步"),
  INSTALLING("同步中"),
  READY_FOR_ROBOT("Bridge 已就绪，待机器人领取任务"),
  FAILED("同步失败"),
  UNKNOWN("待对账");

  private final String displayLabel;

  RouteDeploymentState(String displayLabel) {
    this.displayLabel = displayLabel;
  }

  public String displayLabel() {
    return displayLabel;
  }

  public static String displayLabelOf(String state) {
    if (state == null || state.isBlank()) {
      return "未知";
    }
    try {
      return RouteDeploymentState.valueOf(state).displayLabel();
    } catch (IllegalArgumentException ex) {
      return state;
    }
  }
}
