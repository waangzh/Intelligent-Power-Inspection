package com.powerinspection.robot;

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
@Table(name = "robots")
public class RobotEntity {
  private static final Set<String> KNOWN = Set.of(
    "id", "name", "model", "serialNo", "siteId", "status", "position", "telemetry",
    "createdAt", "updatedAt"
  );

  @Id
  private String id;
  @Column(nullable = false)
  private String name;
  private String model;
  @Column(name = "serial_no")
  private String serialNo;
  @Column(name = "site_id")
  private String siteId;
  @Column(nullable = false)
  private String status;
  @Column(name = "position_lat")
  private Double positionLat;
  @Column(name = "position_lng")
  private Double positionLng;
  @Column(name = "extra_json", columnDefinition = "LONGTEXT")
  private String extraJson;
  @Column(name = "created_at", nullable = false)
  private String createdAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private Long version;

  public static RobotEntity fromMap(Map<String, Object> map) {
    RobotEntity entity = new RobotEntity();
    entity.apply(map);
    return entity;
  }

  public void apply(Map<String, Object> map) {
    id = text(map.get("id"));
    name = first(map.get("name"), id);
    model = text(map.get("model"));
    serialNo = text(map.get("serialNo"));
    siteId = text(map.get("siteId"));
    status = first(map.get("status"), "OFFLINE");
    Object position = map.get("position");
    if (position instanceof Map<?, ?> p) {
      positionLat = dbl(p.get("lat"));
      positionLng = dbl(p.get("lng"));
    }
    extraJson = EntityPayloadCodec.extraJson(map, KNOWN);
    createdAt = first(map.get("createdAt"), Instant.now().toString());
    updatedAt = first(map.get("updatedAt"), createdAt);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id);
    map.put("name", name);
    if (model != null) map.put("model", model);
    if (serialNo != null) map.put("serialNo", serialNo);
    if (siteId != null) map.put("siteId", siteId);
    map.put("status", status);
    if (positionLat != null || positionLng != null) {
      Map<String, Object> position = new LinkedHashMap<>();
      position.put("lat", positionLat == null ? 0 : positionLat);
      position.put("lng", positionLng == null ? 0 : positionLng);
      map.put("position", position);
    }
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
  private static Double dbl(Object value) {
    if (value instanceof Number number) return number.doubleValue();
    if (value == null) return null;
    try { return Double.parseDouble(value.toString()); } catch (Exception ex) { return null; }
  }
}
