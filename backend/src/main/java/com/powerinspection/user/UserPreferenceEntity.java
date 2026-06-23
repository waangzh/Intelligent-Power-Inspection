package com.powerinspection.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_preferences")
public class UserPreferenceEntity {
  @Id
  @Column(name = "user_id")
  private String userId;

  @Column(name = "notify_alarm", nullable = false)
  private boolean notifyAlarm = true;

  @Column(name = "notify_task", nullable = false)
  private boolean notifyTask = true;

  @Column(name = "notify_system", nullable = false)
  private boolean notifySystem = true;

  @Column(name = "default_site_id")
  private String defaultSiteId;

  @Column(name = "sidebar_collapsed", nullable = false)
  private boolean sidebarCollapsed;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public boolean isNotifyAlarm() {
    return notifyAlarm;
  }

  public void setNotifyAlarm(boolean notifyAlarm) {
    this.notifyAlarm = notifyAlarm;
  }

  public boolean isNotifyTask() {
    return notifyTask;
  }

  public void setNotifyTask(boolean notifyTask) {
    this.notifyTask = notifyTask;
  }

  public boolean isNotifySystem() {
    return notifySystem;
  }

  public void setNotifySystem(boolean notifySystem) {
    this.notifySystem = notifySystem;
  }

  public String getDefaultSiteId() {
    return defaultSiteId;
  }

  public void setDefaultSiteId(String defaultSiteId) {
    this.defaultSiteId = defaultSiteId;
  }

  public boolean isSidebarCollapsed() {
    return sidebarCollapsed;
  }

  public void setSidebarCollapsed(boolean sidebarCollapsed) {
    this.sidebarCollapsed = sidebarCollapsed;
  }
}
