package com.powerinspection.robot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "robot_location_history",
    indexes = {
      @Index(name = "idx_robot_location_robot_time", columnList = "robot_id,observed_at"),
      @Index(name = "idx_robot_location_execution_time", columnList = "execution_id,observed_at")
    })
public class RobotLocationHistoryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "robot_id", nullable = false, length = 128)
  private String robotId;

  @Column(name = "execution_id", length = 128)
  private String executionId;

  @Column(name = "task_id", length = 128)
  private String taskId;

  @Column(name = "route_id", length = 128)
  private String routeId;

  @Column(name = "target_id", length = 128)
  private String targetId;

  @Column(name = "cycle_index")
  private Integer cycleIndex;

  @Column(name = "robot_state", length = 32)
  private String robotState;

  @Column(name = "navigation_phase", length = 32)
  private String navigationPhase;

  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(nullable = false)
  private Double latitude;

  @Column(nullable = false)
  private Double longitude;

  private Double altitude;
  private Integer quality;

  @Column(name = "fix_type", length = 32)
  private String fixType;

  private Integer satellites;
  private Double hdop;

  @Column(nullable = false, length = 32)
  private String source = "GNSS";

  public Long getId() { return id; }
  public String getRobotId() { return robotId; }
  public void setRobotId(String robotId) { this.robotId = robotId; }
  public String getExecutionId() { return executionId; }
  public void setExecutionId(String executionId) { this.executionId = executionId; }
  public String getTaskId() { return taskId; }
  public void setTaskId(String taskId) { this.taskId = taskId; }
  public String getRouteId() { return routeId; }
  public void setRouteId(String routeId) { this.routeId = routeId; }
  public String getTargetId() { return targetId; }
  public void setTargetId(String targetId) { this.targetId = targetId; }
  public Integer getCycleIndex() { return cycleIndex; }
  public void setCycleIndex(Integer cycleIndex) { this.cycleIndex = cycleIndex; }
  public String getRobotState() { return robotState; }
  public void setRobotState(String robotState) { this.robotState = robotState; }
  public String getNavigationPhase() { return navigationPhase; }
  public void setNavigationPhase(String navigationPhase) { this.navigationPhase = navigationPhase; }
  public Instant getObservedAt() { return observedAt; }
  public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
  public Instant getReceivedAt() { return receivedAt; }
  public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }
  public Double getLatitude() { return latitude; }
  public void setLatitude(Double latitude) { this.latitude = latitude; }
  public Double getLongitude() { return longitude; }
  public void setLongitude(Double longitude) { this.longitude = longitude; }
  public Double getAltitude() { return altitude; }
  public void setAltitude(Double altitude) { this.altitude = altitude; }
  public Integer getQuality() { return quality; }
  public void setQuality(Integer quality) { this.quality = quality; }
  public String getFixType() { return fixType; }
  public void setFixType(String fixType) { this.fixType = fixType; }
  public Integer getSatellites() { return satellites; }
  public void setSatellites(Integer satellites) { this.satellites = satellites; }
  public Double getHdop() { return hdop; }
  public void setHdop(Double hdop) { this.hdop = hdop; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
}
