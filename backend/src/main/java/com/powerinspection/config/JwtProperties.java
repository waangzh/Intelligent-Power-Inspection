package com.powerinspection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
  private String secret = "dev-secret-change-me";

  /** Access token TTL (seconds). */
  private long accessTtlSeconds = 900;

  /** Refresh token TTL when remember=false (session cookie; absolute server expiry). */
  private long refreshSessionTtlSeconds = 28800;

  /** Refresh token TTL when remember=true. */
  private long refreshRememberTtlSeconds = 604800;

  /** High-risk actions require password-auth within this window. */
  private long reauthWindowSeconds = 900;

  /**
   * @deprecated use accessTtlSeconds
   */
  private Long ttlSeconds;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getAccessTtlSeconds() {
    return ttlSeconds != null ? ttlSeconds : accessTtlSeconds;
  }

  public void setAccessTtlSeconds(long accessTtlSeconds) {
    this.accessTtlSeconds = accessTtlSeconds;
  }

  public long getRefreshSessionTtlSeconds() {
    return refreshSessionTtlSeconds;
  }

  public void setRefreshSessionTtlSeconds(long refreshSessionTtlSeconds) {
    this.refreshSessionTtlSeconds = refreshSessionTtlSeconds;
  }

  public long getRefreshRememberTtlSeconds() {
    return refreshRememberTtlSeconds;
  }

  public void setRefreshRememberTtlSeconds(long refreshRememberTtlSeconds) {
    this.refreshRememberTtlSeconds = refreshRememberTtlSeconds;
  }

  public long getReauthWindowSeconds() {
    return reauthWindowSeconds;
  }

  public void setReauthWindowSeconds(long reauthWindowSeconds) {
    this.reauthWindowSeconds = reauthWindowSeconds;
  }

  public Long getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(Long ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }
}
