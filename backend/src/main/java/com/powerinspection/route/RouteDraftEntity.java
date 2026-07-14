package com.powerinspection.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "route_drafts")
public class RouteDraftEntity {
  @Id
  @Column(name = "route_id")
  private String routeId;
  @Column(name = "executor_json", nullable = false, columnDefinition = "LONGTEXT")
  private String executorJson;
  @Column(name = "validation_report_json", nullable = false, columnDefinition = "LONGTEXT")
  private String validationReportJson;
  @Column(name = "map_asset_id", nullable = false)
  private String mapAssetId;
  @Column(name = "map_image_sha256", nullable = false, length = 64)
  private String mapImageSha256;
  @Column(nullable = false)
  private boolean publishable;
  @Column(name = "checked_at", nullable = false)
  private String checkedAt;
  @Column(name = "updated_at", nullable = false)
  private String updatedAt;
  @Column(name = "updated_by")
  private String updatedBy;
  @Column(name = "publishable_executor_json", columnDefinition = "LONGTEXT")
  private String publishableExecutorJson;
  @Column(name = "publishable_validation_report_json", columnDefinition = "LONGTEXT")
  private String publishableValidationReportJson;
  @Column(name = "publishable_map_asset_id")
  private String publishableMapAssetId;
  @Column(name = "publishable_map_image_sha256", length = 64)
  private String publishableMapImageSha256;
  @Column(name = "publishable_checked_at")
  private String publishableCheckedAt;
  @Version
  private long version;

  public String getRouteId() { return routeId; }
  public void setRouteId(String value) { routeId = value; }
  public String getExecutorJson() { return executorJson; }
  public void setExecutorJson(String value) { executorJson = value; }
  public String getValidationReportJson() { return validationReportJson; }
  public void setValidationReportJson(String value) { validationReportJson = value; }
  public String getMapAssetId() { return mapAssetId; }
  public void setMapAssetId(String value) { mapAssetId = value; }
  public String getMapImageSha256() { return mapImageSha256; }
  public void setMapImageSha256(String value) { mapImageSha256 = value; }
  public boolean isPublishable() { return publishable; }
  public void setPublishable(boolean value) { publishable = value; }
  public String getCheckedAt() { return checkedAt; }
  public void setCheckedAt(String value) { checkedAt = value; }
  public String getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(String value) { updatedAt = value; }
  public String getUpdatedBy() { return updatedBy; }
  public void setUpdatedBy(String value) { updatedBy = value; }
  public String getPublishableExecutorJson() { return publishableExecutorJson; }
  public void setPublishableExecutorJson(String value) { publishableExecutorJson = value; }
  public String getPublishableValidationReportJson() { return publishableValidationReportJson; }
  public void setPublishableValidationReportJson(String value) { publishableValidationReportJson = value; }
  public String getPublishableMapAssetId() { return publishableMapAssetId; }
  public void setPublishableMapAssetId(String value) { publishableMapAssetId = value; }
  public String getPublishableMapImageSha256() { return publishableMapImageSha256; }
  public void setPublishableMapImageSha256(String value) { publishableMapImageSha256 = value; }
  public String getPublishableCheckedAt() { return publishableCheckedAt; }
  public void setPublishableCheckedAt(String value) { publishableCheckedAt = value; }
  public long getVersion() { return version; }
}
