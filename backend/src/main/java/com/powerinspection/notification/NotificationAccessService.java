package com.powerinspection.notification;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.ListQuery;
import com.powerinspection.common.PageResult;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class NotificationAccessService {
  private final NotificationRepository notificationRepository;
  private final NotificationRecipientRepository recipientRepository;

  public NotificationAccessService(
      NotificationRepository notificationRepository,
      NotificationRecipientRepository recipientRepository) {
    this.notificationRepository = notificationRepository;
    this.recipientRepository = recipientRepository;
  }

  public PageResult<Map<String, Object>> page(String userId, ListQuery query) {
    int pageNumber = Math.max(0, query.getPage());
    int pageSize = Math.max(1, Math.min(query.getSize(), 200));
    Sort.Direction direction = "asc".equalsIgnoreCase(query.getDirection())
      ? Sort.Direction.ASC
      : Sort.Direction.DESC;
    String sort = switch (query.getSort()) {
      case "id", "createdAt", "updatedAt", "type", "title" -> query.getSort();
      default -> "updatedAt";
    };
    Page<NotificationEntity> page = notificationRepository.findVisible(
      userId,
      blankToNull(query.getType()),
      blankToNull(query.getUpdatedAfter()),
      blankToNull(query.getQ()),
      parseRead(query.getRead()),
      PageRequest.of(pageNumber, pageSize, Sort.by(direction, sort))
    );
    Map<String, NotificationRecipientEntity> states = states(userId, page.getContent());
    List<Map<String, Object>> items = page.getContent().stream()
      .map(notification -> response(notification, states.get(notification.getId())))
      .toList();
    return new PageResult<>(items, page.getTotalElements(), pageNumber, pageSize, page.hasNext(), null);
  }

  public Map<String, Object> get(String id, String userId) {
    NotificationEntity notification = visible(id, userId);
    NotificationRecipientEntity state = recipientRepository
      .findById(new NotificationRecipientId(id, userId))
      .orElse(null);
    return response(notification, state);
  }

  @Transactional
  public Map<String, Object> markRead(String id, String userId) {
    NotificationEntity notification = visible(id, userId);
    NotificationRecipientEntity state = state(notification, userId);
    if (state.getReadAt() == null) {
      state.setReadAt(Instant.now().toString());
      state = recipientRepository.save(state);
    }
    return response(notification, state);
  }

  @Transactional
  public List<Map<String, Object>> markAllRead(String userId) {
    List<NotificationEntity> notifications = notificationRepository.findAllVisible(userId);
    Map<String, NotificationRecipientEntity> states = states(userId, notifications);
    String readAt = Instant.now().toString();
    for (NotificationEntity notification : notifications) {
      NotificationRecipientEntity state = states.computeIfAbsent(
        notification.getId(),
        ignored -> state(notification, userId)
      );
      if (state.getReadAt() == null) {
        state.setReadAt(readAt);
        recipientRepository.save(state);
      }
    }
    return notifications.stream()
      .map(notification -> response(notification, states.get(notification.getId())))
      .toList();
  }

  @Transactional
  public void remove(String id, String userId) {
    NotificationEntity notification = visible(id, userId);
    NotificationRecipientEntity state = state(notification, userId);
    state.setDeletedAt(Instant.now().toString());
    recipientRepository.save(state);
  }

  private NotificationEntity visible(String id, String userId) {
    NotificationEntity notification = notificationRepository.findById(id)
      .orElseThrow(() -> ApiException.notFound("通知不存在"));
    if (!userId.equals(notification.getUserId()) && !"*".equals(notification.getUserId())) {
      throw ApiException.notFound("通知不存在");
    }
    recipientRepository.findById(new NotificationRecipientId(id, userId))
      .filter(state -> state.getDeletedAt() != null)
      .ifPresent(state -> {
        throw ApiException.notFound("通知不存在");
      });
    return notification;
  }

  private NotificationRecipientEntity state(NotificationEntity notification, String userId) {
    return recipientRepository.findById(new NotificationRecipientId(notification.getId(), userId))
      .orElseGet(() -> {
        NotificationRecipientEntity state = new NotificationRecipientEntity();
        state.setNotificationId(notification.getId());
        state.setUserId(userId);
        if (!"*".equals(notification.getUserId()) && notification.isReadFlag()) {
          state.setReadAt(Instant.now().toString());
        }
        return state;
      });
  }

  private Map<String, NotificationRecipientEntity> states(
      String userId,
      List<NotificationEntity> notifications) {
    if (notifications.isEmpty()) {
      return new LinkedHashMap<>();
    }
    List<String> ids = notifications.stream().map(NotificationEntity::getId).toList();
    return recipientRepository.findByUserIdAndNotificationIdIn(userId, ids).stream()
      .collect(Collectors.toMap(
        NotificationRecipientEntity::getNotificationId,
        Function.identity(),
        (left, right) -> left,
        LinkedHashMap::new
      ));
  }

  private Map<String, Object> response(
      NotificationEntity notification,
      NotificationRecipientEntity state) {
    Map<String, Object> result = new LinkedHashMap<>(notification.toMap());
    boolean legacyPersonalRead = !"*".equals(notification.getUserId()) && notification.isReadFlag();
    result.put("read", state == null ? legacyPersonalRead : state.getReadAt() != null);
    return result;
  }

  private Boolean parseRead(String value) {
    if (value == null || value.isBlank()) return null;
    return Boolean.parseBoolean(value);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
