package com.powerinspection.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
  @Id
  private String id;
  @Column(name = "user_id", nullable = false)
  private String userId;
  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;
  @Column(name = "family_id", nullable = false)
  private String familyId;
  @Column(nullable = false)
  private boolean remember;
  @Column(name = "expires_at", nullable = false)
  private String expiresAt;
  @Column(name = "revoked_at")
  private String revokedAt;
  @Column(name = "replaced_by_id")
  private String replacedById;
  @Column(name = "created_at", nullable = false)
  private String createdAt;

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getUserId() { return userId; }
  public void setUserId(String userId) { this.userId = userId; }
  public String getTokenHash() { return tokenHash; }
  public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
  public String getFamilyId() { return familyId; }
  public void setFamilyId(String familyId) { this.familyId = familyId; }
  public boolean isRemember() { return remember; }
  public void setRemember(boolean remember) { this.remember = remember; }
  public String getExpiresAt() { return expiresAt; }
  public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
  public String getRevokedAt() { return revokedAt; }
  public void setRevokedAt(String revokedAt) { this.revokedAt = revokedAt; }
  public String getReplacedById() { return replacedById; }
  public void setReplacedById(String replacedById) { this.replacedById = replacedById; }
  public String getCreatedAt() { return createdAt; }
  public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
