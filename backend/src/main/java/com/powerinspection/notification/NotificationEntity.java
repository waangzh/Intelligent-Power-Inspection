package com.powerinspection.notification;

import com.powerinspection.domain.EntityPayloadCodec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "notifications")
public class NotificationEntity {
  private static final Set<String> KNOWN = Set.of(
    "id", "userId", "type", "title", "content", "link", "read", "version", "createdAt", "updatedAt"
  );

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
    entity.apply(map);
    return entity;
  }

  public void apply(Map<String, Object> map) {
    id = text(map.get("id"));
    userId = first(map.get("userId"), "*");
    type = first(map.get("type"), "SYSTEM");
    title = first(map.get("title"), "");
    content = first(map.get("content"), "");
    link = text(map.get("link"));
    readFlag = bool(map.get("read"));
    extraJson = EntityPayloadCodec.extraJson(map, KNOWN);
    createdAt = first(map.get("createdAt"), Instant.now().toString());
    updatedAt = first(map.get("updatedAt"), createdAt);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id);
    map.put("userId", userId);
    map.put("type", type);
    map.put("title", title);
    map.put("content", content);
    if (link != null) map.put("link", link);
    map.put("read", readFlag);
    map.put("createdAt", createdAt);
    map.put("updatedAt", updatedAt);
    EntityPayloadCodec.mergeExtra(map, extraJson);
    return map;
  }

  public String getId() { return id; }
  public String getUserId() { return userId; }
  public boolean isReadFlag() { return readFlag; }
  public Long getVersion() { return version; }
  public void setVersion(Long version) { this.version = version; }

  private static String text(Object value) { return value == null ? null : value.toString(); }
  private static String first(Object value, String fallback) {
    String text = text(value);
    return text == null || text.isBlank() ? fallback : text;
  }
  private static boolean bool(Object value) {
    return value instanceof Boolean b ? b : value != null && Boolean.parseBoolean(value.toString());
  }
}
