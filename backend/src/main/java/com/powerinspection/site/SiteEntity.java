package com.powerinspection.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "sites")
public class SiteEntity {
  @Id
  private String id;
  @Column(nullable = false)
  private String name;
  private String address;
  private String description;
  @Column(name = "center_lat")
  private Double centerLat;
  @Column(name = "center_lng")
  private Double centerLng;
  private String status;
  @Column(name = "device_map_uploaded", nullable = false)
  private boolean deviceMapUploaded;
  @Column(name = "lingbot_map_id")
  private String lingbotMapId;
  @Column(name = "extra_json", columnDefinition = "LONGTEXT")
  private String extraJson;
  @Column(name = "created_at", nullable = false)
  private String createdAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private Long version;

  public static SiteEntity fromMap(Map<String, Object> map) {
    SiteEntity entity = new SiteEntity();
    entity.id = text(map.get("id"));
    entity.name = first(map.get("name"), entity.id);
    entity.address = text(map.get("address"));
    entity.description = text(map.get("description"));
    Object center = map.get("center");
    if (center instanceof Map<?, ?> c) {
      entity.centerLat = dbl(c.get("lat"));
      entity.centerLng = dbl(c.get("lng"));
    }
    entity.status = text(map.get("status"));
    entity.deviceMapUploaded = bool(map.get("deviceMapUploaded"));
    entity.lingbotMapId = text(map.get("lingbotMapId"));
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
  private static boolean bool(Object value) {
    return value instanceof Boolean b ? b : value != null && Boolean.parseBoolean(value.toString());
  }
}
