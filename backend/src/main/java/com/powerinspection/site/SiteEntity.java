package com.powerinspection.site;

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
@Table(name = "sites")
public class SiteEntity {
  private static final Set<String> KNOWN = Set.of(
    "id", "name", "address", "description", "center", "status",
    "deviceMapUploaded", "lingbotMapId", "createdAt", "updatedAt"
  );

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
    entity.apply(map);
    return entity;
  }

  public void apply(Map<String, Object> map) {
    id = text(map.get("id"));
    name = first(map.get("name"), id);
    address = text(map.get("address"));
    description = text(map.get("description"));
    Object center = map.get("center");
    if (center instanceof Map<?, ?> c) {
      centerLat = dbl(c.get("lat"));
      centerLng = dbl(c.get("lng"));
    }
    status = text(map.get("status"));
    deviceMapUploaded = bool(map.get("deviceMapUploaded"));
    lingbotMapId = text(map.get("lingbotMapId"));
    extraJson = EntityPayloadCodec.extraJson(map, KNOWN);
    createdAt = first(map.get("createdAt"), Instant.now().toString());
    updatedAt = first(map.get("updatedAt"), createdAt);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("id", id);
    map.put("name", name);
    if (address != null) map.put("address", address);
    if (description != null) map.put("description", description);
    if (centerLat != null || centerLng != null) {
      Map<String, Object> center = new LinkedHashMap<>();
      center.put("lat", centerLat == null ? 0 : centerLat);
      center.put("lng", centerLng == null ? 0 : centerLng);
      map.put("center", center);
    }
    if (status != null) map.put("status", status);
    map.put("deviceMapUploaded", deviceMapUploaded);
    if (lingbotMapId != null) map.put("lingbotMapId", lingbotMapId);
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
  private static boolean bool(Object value) {
    return value instanceof Boolean b ? b : value != null && Boolean.parseBoolean(value.toString());
  }
}
