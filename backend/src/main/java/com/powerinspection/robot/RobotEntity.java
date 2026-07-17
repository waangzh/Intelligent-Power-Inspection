package com.powerinspection.robot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "robots")
public class RobotEntity {
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
    entity.id = text(map.get("id"));
    entity.name = first(map.get("name"), entity.id);
    entity.model = text(map.get("model"));
    entity.serialNo = text(map.get("serialNo"));
    entity.siteId = text(map.get("siteId"));
    entity.status = first(map.get("status"), "OFFLINE");
    Object position = map.get("position");
    if (position instanceof Map<?, ?> p) {
      entity.positionLat = dbl(p.get("lat"));
      entity.positionLng = dbl(p.get("lng"));
    }
    entity.createdAt = first(map.get("createdAt"), Instant.now().toString());
    entity.updatedAt = first(map.get("updatedAt"), entity.createdAt);
    return entity;
  }

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
