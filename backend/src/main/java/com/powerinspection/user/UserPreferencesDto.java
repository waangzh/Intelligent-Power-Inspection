package com.powerinspection.user;

public record UserPreferencesDto(
  boolean notifyAlarm,
  boolean notifyTask,
  boolean notifySystem,
  String defaultSiteId,
  boolean sidebarCollapsed
) {
  public static UserPreferencesDto from(UserPreferenceEntity entity) {
    return new UserPreferencesDto(
      entity.isNotifyAlarm(),
      entity.isNotifyTask(),
      entity.isNotifySystem(),
      entity.getDefaultSiteId(),
      entity.isSidebarCollapsed()
    );
  }
}
