package com.powerinspection.route;

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
@Table(name = "routes")
public class RouteEntity {
  private static final Set<String> KNOWN = Set.of(
    "id", "siteId", "name", "mapId", "status", "executor", "executorJson",
    "checkpoints", "version", "createdAt", "updatedAt"
  );

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
    entity.apply(map);
    return entity;
  }

  public void apply(Map<String, Object> map) {
    id = text(map.get("id"));
    siteId = first(map.get("siteId"), "");
    name = first(map.get("name"), id);
    mapId = text(map.get("mapId"));
    status = text(map.get("status"));
    Object executor = map.get("executor") != null ? map.get("executor") : map.get("executorJson");
    executorJson = EntityPayloadCodec.write(executor);
    checkpointsJson = EntityPayloadCodec.write(map.get("checkpoints"));
    extraJson = EntityPayloadCodec.extraJson(map, KNOWN);
    createdAt = first(map.get("createdAt"), Instant.now().toString());
    updatedAt = first(map.get("updatedAt"), createdAt);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id);
    map.put("siteId", siteId);
    map.put("name", name);
    if (mapId != null) map.put("mapId", mapId);
    if (status != null) map.put("status", status);
    Object executor = EntityPayloadCodec.readValue(executorJson);
    if (executor != null) map.put("executor", executor);
    Object checkpoints = EntityPayloadCodec.readValue(checkpointsJson);
    if (checkpoints != null) map.put("checkpoints", checkpoints);
    map.put("createdAt", createdAt);
    map.put("updatedAt", updatedAt);
    EntityPayloadCodec.mergeExtra(map, extraJson);
    return map;
  }

  public String getId() { return id; }
  public Long getVersion() { return version; }
  public void setVersion(Long version) { this.version = version; }

  private static String text(Object value) { return value == null ? null : value.toString(); }
  private static String first(Object value, String fallback) {
    String text = text(value);
    return text == null || text.isBlank() ? fallback : text;
  }
}
