package com.powerinspection.robot;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
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

  private static String text(Object value) { return value == null ? null : value.toString(); }
  private static Double dbl(Object value) {
    if (value instanceof Number number) return number.doubleValue();
    if (value == null) return null;
    try { return Double.parseDouble(value.toString()); } catch (Exception ex) { return null; }
  }
}
