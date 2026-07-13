package com.powerinspection.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(name = "route_revisions", uniqueConstraints = {
  @UniqueConstraint(name = "uq_route_revisions_route_revision", columnNames = {"route_id", "revision_no"}),
  @UniqueConstraint(name = "uq_route_revisions_route_content", columnNames = {"route_id", "content_sha256"})
})
public class RouteRevisionEntity {
  @Id
  private String id;

  @Column(name = "route_id", nullable = false)
  private String routeId;

  @Column(name = "revision_no", nullable = false)
  private long revisionNo;

  @Column(name = "executor_json", nullable = false, columnDefinition = "LONGTEXT")
  private String executorJson;

  @Column(name = "content_sha256", nullable = false, length = 64)
  private String contentSha256;

  @Column(name = "map_asset_id", nullable = false)
  private String mapAssetId;

  @Column(name = "map_image_sha256", nullable = false, length = 64)
  private String mapImageSha256;

  @Column(name = "validation_report_json", nullable = false, columnDefinition = "LONGTEXT")
  private String validationReportJson;

  @Column(name = "created_by")
  private String createdBy;

  @Column(name = "created_at", nullable = false)
  private String createdAt;

  @Version
  private long version;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getRouteId() { return routeId; }
  public void setRouteId(String routeId) { this.routeId = routeId; }
  public long getRevisionNo() { return revisionNo; }
  public void setRevisionNo(long revisionNo) { this.revisionNo = revisionNo; }
  public String getExecutorJson() { return executorJson; }
  public void setExecutorJson(String executorJson) { this.executorJson = executorJson; }
  public String getContentSha256() { return contentSha256; }
  public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }
  public String getMapAssetId() { return mapAssetId; }
  public void setMapAssetId(String mapAssetId) { this.mapAssetId = mapAssetId; }
  public String getMapImageSha256() { return mapImageSha256; }
  public void setMapImageSha256(String mapImageSha256) { this.mapImageSha256 = mapImageSha256; }
  public String getValidationReportJson() { return validationReportJson; }
  public void setValidationReportJson(String validationReportJson) { this.validationReportJson = validationReportJson; }
  public String getCreatedBy() { return createdBy; }
  public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
  public long getVersion() { return version; }
}
