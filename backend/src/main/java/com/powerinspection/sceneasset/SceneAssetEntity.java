package com.powerinspection.sceneasset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "scene_assets")
public class SceneAssetEntity {
  @Id String id;
  @Column(name = "site_id", nullable = false, length = 100) String siteId;
  @Column(nullable = false, length = 32) String status;
  @Column(nullable = false, length = 32) String source;
  @Column(name = "source_robot_id", length = 100) String sourceRobotId;
  @Column(name = "source_bridge_robot_id", length = 100) String sourceBridgeRobotId;
  @Column(name = "asset_kind", nullable = false, length = 32) String assetKind;
  @Column(nullable = false, length = 16) String format;
  @Column(name = "original_name", nullable = false, length = 255) String originalName;
  @Column(name = "content_type", length = 100) String contentType;
  @Column(name = "file_size", nullable = false) long fileSize;
  @Column(name = "model_sha256", nullable = false, length = 64) String modelSha256;
  @Column(name = "metadata_sha256", nullable = false, length = 64) String metadataSha256;
  @Column(name = "source_capture_session_id", length = 160) String sourceCaptureSessionId;
  @Column(name = "source_reconstruct_session_id", nullable = false, length = 160) String sourceReconstructSessionId;
  @Column(name = "reconstruct_profile", length = 160) String reconstructProfile;
  @Column(name = "coordinate_system", nullable = false, length = 64) String coordinateSystem;
  @Column(nullable = false, length = 32) String unit;
  @Column(name = "point_count", nullable = false) long pointCount;
  @Column(name = "reported_point_count") Long reportedPointCount;
  @Column(name = "captured_at", length = 40) String capturedAt;
  @Column(name = "reconstructed_at", nullable = false, length = 40) String reconstructedAt;
  @Column(name = "created_at", nullable = false, length = 40) String createdAt;
  @Column(name = "updated_at", nullable = false, length = 40) String updatedAt;
  @Column(name = "reviewed_by", length = 100) String reviewedBy;
  @Column(name = "reviewed_at", length = 40) String reviewedAt;
  @Column(name = "review_comment", length = 1000) String reviewComment;
  @Column(name = "storage_key", length = 500) String storageKey;
  @Column(name = "preview_storage_key", length = 500) String previewStorageKey;
  @Column(name = "files_ready", nullable = false) boolean filesReady;
  @Column(name = "scene_frame", length = 100) String sceneFrame;
  @Column(name = "reference_frame", length = 100) String referenceFrame;
  @Column(name = "scene_to_reference_transform", columnDefinition = "LONGTEXT") String sceneToReferenceTransform;
  @Version long version;
}
