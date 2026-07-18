package com.powerinspection.notification;

import java.io.Serializable;
import java.util.Objects;

public class NotificationRecipientId implements Serializable {
  private String notificationId;
  private String userId;

  public NotificationRecipientId() {
  }

  public NotificationRecipientId(String notificationId, String userId) {
    this.notificationId = notificationId;
    this.userId = userId;
  }

  public String getNotificationId() { return notificationId; }
  public String getUserId() { return userId; }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (!(other instanceof NotificationRecipientId that)) return false;
    return Objects.equals(notificationId, that.notificationId) && Objects.equals(userId, that.userId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(notificationId, userId);
  }
}
