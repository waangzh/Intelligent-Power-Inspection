package com.powerinspection.mapasset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "robot_map_uploads", uniqueConstraints = @UniqueConstraint(
  name = "uq_robot_map_uploads_robot_key", columnNames = {"robot_id", "idempotency_key"}))
public class RobotMapUploadEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "robot_id", nullable = false, length = 100)
  private String robotId;

  @Column(name = "idempotency_key", nullable = false, length = 160)
  private String idempotencyKey;

  @Column(name = "yaml_sha256", nullable = false, length = 64)
  private String yamlSha256;

  @Column(name = "pgm_sha256", nullable = false, length = 64)
  private String pgmSha256;

  @Column(name = "map_asset_id", length = 100)
  private String mapAssetId;

  @Column(nullable = false, length = 32)
  private String status;

  @Column(name = "created_at", nullable = false, length = 40)
  private String createdAt;

  @Column(name = "updated_at", nullable = false, length = 40)
  private String updatedAt;

  public Long getId() { return id; }
  public String getRobotId() { return robotId; }
  public void setRobotId(String robotId) { this.robotId = robotId; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
  public String getYamlSha256() { return yamlSha256; }
  public void setYamlSha256(String yamlSha256) { this.yamlSha256 = yamlSha256; }
  public String getPgmSha256() { return pgmSha256; }
  public void setPgmSha256(String pgmSha256) { this.pgmSha256 = pgmSha256; }
  public String getMapAssetId() { return mapAssetId; }
  public void setMapAssetId(String mapAssetId) { this.mapAssetId = mapAssetId; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
  public String getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
