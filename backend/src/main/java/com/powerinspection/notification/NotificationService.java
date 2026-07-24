package com.powerinspection.notification;

import com.powerinspection.common.Ids;
import com.powerinspection.common.ResourceChangeEvent;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {
  private final DataStoreService dataStore;
  private final SimpMessagingTemplate messagingTemplate;
  private final NotificationRecipientRepository recipientRepository;
  private final boolean systemEnabled;

  public NotificationService(
      DataStoreService dataStore,
      SimpMessagingTemplate messagingTemplate,
      NotificationRecipientRepository recipientRepository) {
    this(dataStore, messagingTemplate, recipientRepository, true);
  }

  @Autowired
  public NotificationService(
      DataStoreService dataStore,
      SimpMessagingTemplate messagingTemplate,
      NotificationRecipientRepository recipientRepository,
      @Value("${app.notification.system-enabled:false}") boolean systemEnabled) {
    this.dataStore = dataStore;
    this.messagingTemplate = messagingTemplate;
    this.recipientRepository = recipientRepository;
    this.systemEnabled = systemEnabled;
  }

  public Map<String, Object> push(String userId, String type, String title, String content, String link) {
    return pushEvent(userId, type, null, null, null, title, content, link, null, Map.of());
  }

  public synchronized Map<String, Object> pushEvent(
      String userId,
      String type,
      String eventCode,
      String resourceType,
      String resourceId,
      String title,
      String content,
      String link,
      String idempotencyKey) {
    return pushEvent(userId, type, eventCode, resourceType, resourceId, title, content, link, idempotencyKey, Map.of());
  }

  public Map<String, Object> pushEvent(
      String userId,
      String type,
      String eventCode,
      String resourceType,
      String resourceId,
      String title,
      String content,
      String link,
      String idempotencyKey,
      Map<String, Object> extras) {
    if (isDisabled(type)) return Map.of();
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      Map<String, Object> existing = dataStore.list(DataCategory.NOTIFICATION).stream()
          .filter(item -> idempotencyKey.equals(String.valueOf(item.get("idempotencyKey"))))
          .findFirst().orElse(null);
      if (existing != null) return existing;
    }
    Map<String, Object> metadata = new LinkedHashMap<>(extras);
    if (eventCode != null) metadata.put("eventCode", eventCode);
    if (resourceType != null) metadata.put("resourceType", resourceType);
    if (resourceId != null) metadata.put("resourceId", resourceId);
    if (idempotencyKey != null) metadata.put("idempotencyKey", idempotencyKey);
    return push(userId, type, title, content, link, metadata);
  }

  public Map<String, Object> pushForAgentAction(String userId, String type, String title, String content, String link, String actionId) {
    if (isDisabled(type)) return Map.of();
    return dataStore.list(DataCategory.NOTIFICATION).stream()
      .filter(item -> actionId.equals(String.valueOf(item.get("agentActionId"))))
      .findFirst()
      .orElseGet(() -> push(userId, type, title, content, link, Map.of("agentActionId", actionId)));
  }

  private Map<String, Object> push(String userId, String type, String title, String content, String link, Map<String, Object> extras) {
    if (isDisabled(type)) return Map.of();
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", Ids.next("ntf"));
    item.put("userId", userId);
    item.put("type", type);
    item.put("title", title);
    item.put("content", content);
    item.put("read", false);
    if (link != null) {
      item.put("link", link);
    }
    item.putAll(extras);
    item.put("createdAt", Instant.now().toString());
    Map<String, Object> saved = dataStore.upsert(DataCategory.NOTIFICATION, item);
    if (!"*".equals(userId)) {
      NotificationRecipientEntity recipient = new NotificationRecipientEntity();
      recipient.setNotificationId(String.valueOf(saved.get("id")));
      recipient.setUserId(userId);
      recipientRepository.save(recipient);
    }
    ResourceChangeEvent event = ResourceChangeEvent.created("notification", saved.get("id"));
    publishAfterCommit(userId, event);
    return saved;
  }

  private boolean isDisabled(String type) {
    return !systemEnabled && "SYSTEM".equalsIgnoreCase(type);
  }

  private void publishAfterCommit(String userId, ResourceChangeEvent event) {
    Runnable publish = () -> {
      if ("*".equals(userId)) messagingTemplate.convertAndSend("/topic/notifications", event);
      else messagingTemplate.convertAndSend("/topic/notifications/" + userId, event);
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { publish.run(); }
      });
    } else {
      publish.run();
    }
  }

  public Map<String, Object> pushToAll(String type, String title, String content, String link) {
    return push("*", type, title, content, link);
  }
}
