package com.powerinspection.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "notifications")
public class NotificationEntity {
  @Id
  private String id;
  @Column(name = "user_id", nullable = false)
  private String userId;
  @Column(nullable = false)
  private String type;
  @Column(nullable = false)
  private String title;
  @Column(nullable = false)
  private String content;
  private String link;
  @Column(name = "read_flag", nullable = false)
  private boolean readFlag;
  @Column(name = "extra_json", columnDefinition = "LONGTEXT")
  private String extraJson;
  @Column(name = "created_at", nullable = false)
  private String createdAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private Long version;

  public static NotificationEntity fromMap(Map<String, Object> map) {
    NotificationEntity entity = new NotificationEntity();
    entity.id = text(map.get("id"));
    entity.userId = first(map.get("userId"), "*");
    entity.type = first(map.get("type"), "SYSTEM");
    entity.title = first(map.get("title"), "");
    entity.content = first(map.get("content"), "");
    entity.link = text(map.get("link"));
    entity.readFlag = bool(map.get("read"));
    entity.createdAt = first(map.get("createdAt"), Instant.now().toString());
    entity.updatedAt = first(map.get("updatedAt"), entity.createdAt);
    return entity;
  }

  private static String text(Object value) { return value == null ? null : value.toString(); }
  private static String first(Object value, String fallback) {
    String text = text(value);
    return text == null || text.isBlank() ? fallback : text;
  }
  private static boolean bool(Object value) {
    return value instanceof Boolean b ? b : value != null && Boolean.parseBoolean(value.toString());
  }
}
