package com.powerinspection.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@IdClass(NotificationRecipientId.class)
@Table(name = "notification_recipient")
public class NotificationRecipientEntity {
  @Id
  @Column(name = "notification_id", nullable = false)
  private String notificationId;
  @Id
  @Column(name = "user_id", nullable = false)
  private String userId;
  @Column(name = "read_at")
  private String readAt;
  @Column(name = "deleted_at")
  private String deletedAt;

  public String getNotificationId() { return notificationId; }
  public void setNotificationId(String notificationId) { this.notificationId = notificationId; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getReadAt() { return readAt; }
  public void setReadAt(String readAt) { this.readAt = readAt; }
  public String getDeletedAt() { return deletedAt; }
  public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }
}
