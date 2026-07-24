package com.powerinspection.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {
  @Mock DataStoreService dataStore;
  @Mock SimpMessagingTemplate messagingTemplate;
  @Mock NotificationRecipientRepository recipientRepository;

  @Test
  void eventMetadataIsPersistedAndDuplicateKeyDoesNotPublishAgain() {
    NotificationService service = new NotificationService(dataStore, messagingTemplate, recipientRepository);
    Map<String, Object> first = new LinkedHashMap<>(Map.of(
        "id", "ntf-1", "userId", "user-1", "type", "TASK", "title", "任务", "content", "内容"));
    List<Map<String, Object>> notifications = new ArrayList<>();
    when(dataStore.list(DataCategory.NOTIFICATION)).thenAnswer(invocation -> notifications);
    when(dataStore.upsert(any(), any())).thenAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) invocation.getArgument(1));
      notifications.add(payload);
      return payload;
    });

    Map<String, Object> saved = service.pushEvent("user-1", "TASK", "TASK_COMPLETED", "TASK", "task-1",
        "任务完成", "任务已完成", "/tasks/task-1", "task-1:completed");
    Map<String, Object> duplicate = service.pushEvent("user-1", "TASK", "TASK_COMPLETED", "TASK", "task-1",
        "任务完成", "任务已完成", "/tasks/task-1", "task-1:completed");

    assertThat(saved.get("eventCode")).isEqualTo("TASK_COMPLETED");
    assertThat(duplicate).isSameAs(saved);
    verify(dataStore).upsert(any(), any());
    verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/notifications/user-1"), any(Object.class));
    verify(messagingTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/notifications"), any(Object.class));
  }

  @Test
  void disabledSystemNotificationsAreNotPersistedOrPublished() {
    NotificationService service = new NotificationService(
        dataStore, messagingTemplate, recipientRepository, false);

    Map<String, Object> result = service.pushEvent(
        "*", "SYSTEM", "ROBOT_OFFLINE", "ROBOT", "robot-1",
        "机器人状态异常", "机器人已离线", "/robots/status", null);

    assertThat(result).isEmpty();
    verifyNoInteractions(dataStore, messagingTemplate, recipientRepository);
  }

  @Test
  void disabledSystemNotificationsDoNotBlockTaskNotifications() {
    NotificationService service = new NotificationService(
        dataStore, messagingTemplate, recipientRepository, false);
    when(dataStore.upsert(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));

    Map<String, Object> result = service.pushEvent(
        "user-1", "TASK", "TASK_CREATED", "TASK", "task-1",
        "任务已创建", "任务已创建", "/tasks/task-1", null);

    assertThat(result).containsEntry("type", "TASK");
    verify(dataStore).upsert(any(), any());
    verify(recipientRepository).save(any(NotificationRecipientEntity.class));
    verify(messagingTemplate).convertAndSend(
        org.mockito.ArgumentMatchers.eq("/topic/notifications/user-1"), any(Object.class));
  }

  @Test
  void disabledSystemNotificationsAreNotResolvedForAgentActions() {
    NotificationService service = new NotificationService(
        dataStore, messagingTemplate, recipientRepository, false);

    Map<String, Object> result = service.pushForAgentAction(
        "user-1", "SYSTEM", "系统异常", "后台任务失败", "/system", "action-1");

    assertThat(result).isEmpty();
    verifyNoInteractions(dataStore, messagingTemplate, recipientRepository);
  }
}
