package com.powerinspection.detection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "detection_runs")
public class DetectionRunEntity {
  @Id private String id;
  @Column(name = "source_type", nullable = false, length = 32) private String sourceType;
  @Column(name = "image_id", length = 64) private String imageId;
  @Column(name = "task_id", length = 100) private String taskId;
  @Column(name = "checkpoint_id", length = 100) private String checkpointId;
  @Column(nullable = false, length = 32) private String status;
  @Column(name = "detections_json", nullable = false, columnDefinition = "LONGTEXT") private String detectionsJson;
  @Column(name = "findings_json", columnDefinition = "LONGTEXT") private String findingsJson;
  @Column(name = "warnings_json", columnDefinition = "LONGTEXT") private String warningsJson;
  @Column(name = "input_image_url", length = 1000) private String inputImageUrl;
  @Column(name = "result_image_url", length = 1000) private String resultImageUrl;
  @Column(name = "result_storage_key", length = 500) private String resultStorageKey;
  @Column(name = "error_message", length = 1000) private String errorMessage;
  @Column(name = "created_by", length = 100) private String createdBy;
  @Column(name = "created_at", nullable = false, length = 40) private String createdAt;
  @Column(name = "updated_at", nullable = false, length = 40) private String updatedAt;
  @Column(name = "started_at", length = 40) private String startedAt;
  @Column(name = "completed_at", length = 40) private String completedAt;
  @Version private long version;

  public String getId() { return id; }
  public void setId(String value) { id = value; }
  public String getSourceType() { return sourceType; }
  public void setSourceType(String value) { sourceType = value; }
  public String getImageId() { return imageId; }
  public void setImageId(String value) { imageId = value; }
  public String getTaskId() { return taskId; }
  public void setTaskId(String value) { taskId = value; }
  public String getCheckpointId() { return checkpointId; }
  public void setCheckpointId(String value) { checkpointId = value; }
  public String getStatus() { return status; }
  public void setStatus(String value) { status = value; }
  public String getDetectionsJson() { return detectionsJson; }
  public void setDetectionsJson(String value) { detectionsJson = value; }
  public String getFindingsJson() { return findingsJson; }
  public void setFindingsJson(String value) { findingsJson = value; }
  public String getWarningsJson() { return warningsJson; }
  public void setWarningsJson(String value) { warningsJson = value; }
  public String getInputImageUrl() { return inputImageUrl; }
  public void setInputImageUrl(String value) { inputImageUrl = value; }
  public String getResultImageUrl() { return resultImageUrl; }
  public void setResultImageUrl(String value) { resultImageUrl = value; }
  public String getResultStorageKey() { return resultStorageKey; }
  public void setResultStorageKey(String value) { resultStorageKey = value; }
  public String getErrorMessage() { return errorMessage; }
  public void setErrorMessage(String value) { errorMessage = value; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String value) { createdBy = value; }
  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String value) { createdAt = value; }
  public String getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(String value) { updatedAt = value; }
  public String getStartedAt() { return startedAt; }
  public void setStartedAt(String value) { startedAt = value; }
  public String getCompletedAt() { return completedAt; }
  public void setCompletedAt(String value) { completedAt = value; }
}
