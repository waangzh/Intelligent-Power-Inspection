package com.powerinspection.robot;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "robot_telemetry")
public class RobotTelemetryEntity {
  @Id
  @Column(name = "robot_id")
  private String robotId;
  @Column(name = "patrol_state")
  private String patrolState;
  @Column(name = "system_mode")
  private String systemMode;
  @Column(name = "mapping_status")
  private String mappingStatus;
  @Column(name = "nav2_status")
  private String nav2Status;
  @Column(name = "can_status")
  private String canStatus;
  @Column(name = "zlac_status")
  private String zlacStatus;
  @Column(name = "pose_x")
  private Double poseX;
  @Column(name = "pose_y")
  private Double poseY;
  @Column(name = "pose_yaw")
  private Double poseYaw;
  @Column(name = "last_odom_age_sec")
  private Double lastOdomAgeSec;
  @Column(name = "last_scan_age_sec")
  private Double lastScanAgeSec;
  @Column(name = "gps_valid")
  private Boolean gpsValid;
  @Column(name = "gps_stale")
  private Boolean gpsStale;
  @Column(name = "gps_latitude")
  private Double gpsLatitude;
  @Column(name = "gps_longitude")
  private Double gpsLongitude;
  @Column(name = "gps_altitude")
  private Double gpsAltitude;
  @Column(name = "gps_quality")
  private Integer gpsQuality;
  @Column(name = "gps_fix_type")
  private String gpsFixType;
  @Column(name = "gps_satellites")
  private Integer gpsSatellites;
  @Column(name = "gps_hdop")
  private Double gpsHdop;
  @Column(name = "gps_differential_age")
  private Double gpsDifferentialAge;
  @Column(name = "gps_base_station_id")
  private String gpsBaseStationId;
  @Column(name = "gps_observed_at")
  private Instant gpsObservedAt;
  @Column(name = "gps_received_at")
  private Instant gpsReceivedAt;
  @Column(name = "payload_json", columnDefinition = "LONGTEXT")
  private String payloadJson;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Version
  private Long version;

  public static RobotTelemetryEntity fromMap(String robotId, Map<String, Object> map) {
    RobotTelemetryEntity entity = new RobotTelemetryEntity();
    entity.robotId = robotId;
    entity.patrolState = text(map.get("patrolState"));
    entity.systemMode = text(map.get("systemMode"));
    entity.mappingStatus = text(map.get("mappingStatus"));
    entity.nav2Status = text(map.get("nav2Status"));
    entity.canStatus = text(map.get("canStatus"));
    entity.zlacStatus = text(map.get("zlacStatus"));
    Object pose = map.get("pose");
    if (pose instanceof Map<?, ?> p) {
      entity.poseX = dbl(p.get("x"));
      entity.poseY = dbl(p.get("y"));
      entity.poseYaw = dbl(p.get("yaw"));
    }
    entity.lastOdomAgeSec = dbl(map.get("lastOdomAgeSec"));
    entity.lastScanAgeSec = dbl(map.get("lastScanAgeSec"));
    try { entity.payloadJson = new ObjectMapper().writeValueAsString(map); } catch (Exception ignored) { entity.payloadJson = null; }
    entity.updatedAt = Instant.now().toString();
    return entity;
  }

  public Map<String, Object> toMap() {
    if (payloadJson != null && !payloadJson.isBlank()) {
      try {
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = new ObjectMapper().readValue(payloadJson, Map.class);
        return new LinkedHashMap<>(parsed);
      } catch (Exception ignored) {
      }
    }
    Map<String, Object> map = new LinkedHashMap<>();
    if (patrolState != null) map.put("patrolState", patrolState);
    if (systemMode != null) map.put("systemMode", systemMode);
    if (mappingStatus != null) map.put("mappingStatus", mappingStatus);
    if (nav2Status != null) map.put("nav2Status", nav2Status);
    if (canStatus != null) map.put("canStatus", canStatus);
    if (zlacStatus != null) map.put("zlacStatus", zlacStatus);
    if (poseX != null || poseY != null || poseYaw != null) {
      Map<String, Object> pose = new LinkedHashMap<>();
      pose.put("x", poseX == null ? 0 : poseX);
      pose.put("y", poseY == null ? 0 : poseY);
      pose.put("yaw", poseYaw == null ? 0 : poseYaw);
      map.put("pose", pose);
    }
    if (lastOdomAgeSec != null) map.put("lastOdomAgeSec", lastOdomAgeSec);
    if (lastScanAgeSec != null) map.put("lastScanAgeSec", lastScanAgeSec);
    return map;
  }

  public String getRobotId() { return robotId; }
  public void setRobotId(String robotId) { this.robotId = robotId; }
  public String getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
  public Long getVersion() { return version; }
  public void setVersion(Long version) { this.version = version; }

  public Boolean getGpsValid() { return gpsValid; }
  public Boolean getGpsStale() { return gpsStale; }
  public Double getGpsLatitude() { return gpsLatitude; }
  public Double getGpsLongitude() { return gpsLongitude; }
  public Double getGpsAltitude() { return gpsAltitude; }
  public Integer getGpsQuality() { return gpsQuality; }
  public String getGpsFixType() { return gpsFixType; }
  public Integer getGpsSatellites() { return gpsSatellites; }
  public Double getGpsHdop() { return gpsHdop; }
  public Double getGpsDifferentialAge() { return gpsDifferentialAge; }
  public String getGpsBaseStationId() { return gpsBaseStationId; }
  public Instant getGpsObservedAt() { return gpsObservedAt; }
  public Instant getGpsReceivedAt() { return gpsReceivedAt; }

  public void applyGnssFix(BridgeGnssFix fix, Instant receivedAt) {
    if (fix == null) return;
    gpsValid = fix.valid();
    gpsStale = fix.stale();
    gpsLatitude = fix.latitude();
    gpsLongitude = fix.longitude();
    gpsAltitude = fix.altitude();
    gpsQuality = fix.quality();
    gpsFixType = fix.fixType();
    gpsSatellites = fix.satellites();
    gpsHdop = fix.hdop();
    gpsDifferentialAge = fix.differentialAge();
    gpsBaseStationId = fix.baseStationId();
    gpsObservedAt = fix.observedAt() != null ? fix.observedAt() : receivedAt;
    gpsReceivedAt = receivedAt;
    if (!Boolean.TRUE.equals(gpsValid) && !GnssFixParser.coordinateValid(gpsLatitude, gpsLongitude)) {
      gpsLatitude = null;
      gpsLongitude = null;
    }
  }

  public BridgeGnssFix toGnssFix() {
    if (gpsLatitude == null || gpsLongitude == null) return null;
    return new BridgeGnssFix(
        Boolean.TRUE.equals(gpsValid),
        Boolean.TRUE.equals(gpsStale),
        "gps_link",
        gpsLatitude,
        gpsLongitude,
        gpsAltitude,
        gpsQuality,
        gpsFixType == null ? "UNKNOWN" : gpsFixType,
        gpsSatellites,
        gpsHdop,
        gpsDifferentialAge,
        gpsBaseStationId,
        null,
        gpsObservedAt);
  }

  public boolean hasStoredCoordinates() {
    return GnssFixParser.coordinateValid(gpsLatitude, gpsLongitude);
  }

  private static String text(Object value) { return value == null ? null : value.toString(); }
  private static Double dbl(Object value) {
    if (value instanceof Number number) return number.doubleValue();
    if (value == null) return null;
    try { return Double.parseDouble(value.toString()); } catch (Exception ex) { return null; }
  }
}
