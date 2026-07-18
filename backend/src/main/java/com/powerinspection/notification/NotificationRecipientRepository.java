package com.powerinspection.notification;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecipientRepository
    extends JpaRepository<NotificationRecipientEntity, NotificationRecipientId> {
  List<NotificationRecipientEntity> findByUserIdAndNotificationIdIn(
    String userId,
    Collection<String> notificationIds
  );
}
