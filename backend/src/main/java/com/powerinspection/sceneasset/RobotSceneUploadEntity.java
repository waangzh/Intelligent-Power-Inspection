package com.powerinspection.sceneasset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "robot_scene_uploads", uniqueConstraints = @UniqueConstraint(
  name = "uq_robot_scene_uploads_robot_key", columnNames = {"robot_id", "idempotency_key"}))
public class RobotSceneUploadEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
  @Column(name = "robot_id", nullable = false, length = 100) String robotId;
  @Column(name = "idempotency_key", nullable = false, length = 160) String idempotencyKey;
  @Column(name = "model_sha256", nullable = false, length = 64) String modelSha256;
  @Column(name = "scene_asset_id", length = 64) String sceneAssetId;
  @Column(nullable = false, length = 32) String status;
  @Column(name = "created_at", nullable = false, length = 40) String createdAt;
  @Column(name = "updated_at", nullable = false, length = 40) String updatedAt;
}
