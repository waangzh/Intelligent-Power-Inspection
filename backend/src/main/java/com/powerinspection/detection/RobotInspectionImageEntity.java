package com.powerinspection.detection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "robot_inspection_images", uniqueConstraints = @UniqueConstraint(
  name = "uq_robot_inspection_image_upload", columnNames = {"robot_id", "idempotency_key"}))
public class RobotInspectionImageEntity {
  @Id private String id;
  @Column(nullable = false, length = 32) private String source;
  @Column(name = "robot_id", nullable = false, length = 100) private String robotId;
  @Column(name = "task_id", nullable = false, length = 100) private String taskId;
  @Column(name = "execution_id", length = 100) private String executionId;
  @Column(name = "route_id", nullable = false, length = 100) private String routeId;
  @Column(name = "route_revision_id", length = 100) private String routeRevisionId;
  @Column(name = "checkpoint_id", nullable = false, length = 100) private String checkpointId;
  @Column(name = "checkpoint_name", nullable = false, length = 160) private String checkpointName;
  @Column(name = "captured_at", nullable = false, length = 40) private String capturedAt;
  @Column(name = "content_type", nullable = false, length = 64) private String contentType;
  @Column(nullable = false, length = 10) private String extension;
  private Integer width;
  private Integer height;
  @Column(name = "size_bytes", nullable = false) private long sizeBytes;
  @Column(nullable = false, length = 64) private String sha256;
  @Column(name = "storage_key", length = 500) private String storageKey;
  @Column(nullable = false, length = 32) private String status;
  @Column(name = "idempotency_key", length = 160) private String idempotencyKey;
  @Column(name = "created_by", length = 100) private String createdBy;
  @Column(name = "original_purged_at", length = 40) private String originalPurgedAt;
  @Column(name = "created_at", nullable = false, length = 40) private String createdAt;
  @Column(name = "updated_at", nullable = false, length = 40) private String updatedAt;
  @Version private long version;

  public String getId() { return id; }
  public void setId(String value) { id = value; }
  public String getSource() { return source; }
  public void setSource(String value) { source = value; }
  public String getRobotId() { return robotId; }
  public void setRobotId(String value) { robotId = value; }
  public String getTaskId() { return taskId; }
  public void setTaskId(String value) { taskId = value; }
  public String getExecutionId() { return executionId; }
  public void setExecutionId(String value) { executionId = value; }
  public String getRouteId() { return routeId; }
  public void setRouteId(String value) { routeId = value; }
  public String getRouteRevisionId() { return routeRevisionId; }
  public void setRouteRevisionId(String value) { routeRevisionId = value; }
  public String getCheckpointId() { return checkpointId; }
  public void setCheckpointId(String value) { checkpointId = value; }
  public String getCheckpointName() { return checkpointName; }
  public void setCheckpointName(String value) { checkpointName = value; }
  public String getCapturedAt() { return capturedAt; }
  public void setCapturedAt(String value) { capturedAt = value; }
  public String getContentType() { return contentType; }
  public void setContentType(String value) { contentType = value; }
  public String getExtension() { return extension; }
  public void setExtension(String value) { extension = value; }
  public Integer getWidth() { return width; }
  public void setWidth(Integer value) { width = value; }
  public Integer getHeight() { return height; }
  public void setHeight(Integer value) { height = value; }
  public long getSizeBytes() { return sizeBytes; }
  public void setSizeBytes(long value) { sizeBytes = value; }
  public String getSha256() { return sha256; }
  public void setSha256(String value) { sha256 = value; }
  public String getStorageKey() { return storageKey; }
  public void setStorageKey(String value) { storageKey = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String value) { idempotencyKey = value; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String value) { createdBy = value; }
  public String getOriginalPurgedAt() { return originalPurgedAt; }
  public void setOriginalPurgedAt(String value) { originalPurgedAt = value; }
  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String value) { createdAt = value; }
  public String getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(String value) { updatedAt = value; }
}
