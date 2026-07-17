package com.powerinspection.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "routes")
public class RouteEntity {
  @Id
  private String id;
  @Column(name = "site_id", nullable = false)
  private String siteId;
  @Column(nullable = false)
  private String name;
  @Column(name = "map_id")
  private String mapId;
  private String status;
  @Column(name = "executor_json", columnDefinition = "LONGTEXT")
  private String executorJson;
  @Column(name = "checkpoints_json", columnDefinition = "LONGTEXT")
  private String checkpointsJson;
  @Column(name = "extra_json", columnDefinition = "LONGTEXT")
  private String extraJson;
  @Column(name = "created_at", nullable = false)
  private String createdAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private Long version;

  public static RouteEntity fromMap(Map<String, Object> map) {
    RouteEntity entity = new RouteEntity();
    entity.id = text(map.get("id"));
    entity.siteId = first(map.get("siteId"), "");
    entity.name = first(map.get("name"), entity.id);
    entity.mapId = text(map.get("mapId"));
    entity.status = text(map.get("status"));
    Object executor = map.get("executor") != null ? map.get("executor") : map.get("executorJson");
    if (executor != null) {
      entity.executorJson = asJson(executor);
    }
    Object checkpoints = map.get("checkpoints");
    if (checkpoints != null) {
      entity.checkpointsJson = asJson(checkpoints);
    }
    entity.createdAt = first(map.get("createdAt"), Instant.now().toString());
    entity.updatedAt = first(map.get("updatedAt"), entity.createdAt);
    return entity;
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static String text(Object value) { return value == null ? null : value.toString(); }
  private static String first(Object value, String fallback) {
    String text = text(value);
    return text == null || text.isBlank() ? fallback : text;
  }
  private static String asJson(Object value) {
    if (value instanceof String s) return s;
    try { return MAPPER.writeValueAsString(value); } catch (Exception ex) { return null; }
  }
}
