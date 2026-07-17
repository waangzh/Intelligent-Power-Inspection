package com.powerinspection.notification;

import com.powerinspection.common.Ids;
import com.powerinspection.common.ResourceChangeEvent;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
  private final DataStoreService dataStore;
  private final SimpMessagingTemplate messagingTemplate;

  public NotificationService(DataStoreService dataStore, SimpMessagingTemplate messagingTemplate) {
    this.dataStore = dataStore;
    this.messagingTemplate = messagingTemplate;
  }

  public Map<String, Object> push(String userId, String type, String title, String content, String link) {
    return push(userId, type, title, content, link, Map.of());
  }

  public Map<String, Object> pushForAgentAction(String userId, String type, String title, String content, String link, String actionId) {
    return dataStore.list(DataCategory.NOTIFICATION).stream()
      .filter(item -> actionId.equals(String.valueOf(item.get("agentActionId"))))
      .findFirst()
      .orElseGet(() -> push(userId, type, title, content, link, Map.of("agentActionId", actionId)));
  }

  private Map<String, Object> push(String userId, String type, String title, String content, String link, Map<String, Object> extras) {
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
    ResourceChangeEvent event = ResourceChangeEvent.created("notification", saved.get("id"));
    if ("*".equals(userId)) {
      messagingTemplate.convertAndSend("/topic/notifications", event);
    } else {
      messagingTemplate.convertAndSend("/topic/notifications/" + userId, event);
    }
    return saved;
  }

  public Map<String, Object> pushToAll(String type, String title, String content, String link) {
    return push("*", type, title, content, link);
  }
}
