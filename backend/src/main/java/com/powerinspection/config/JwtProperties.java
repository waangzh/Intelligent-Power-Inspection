package com.powerinspection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
  private String secret = "dev-secret-change-me";
  private long ttlSeconds = 604800;

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public long getTtlSeconds() {
    return ttlSeconds;
  }

  public void setTtlSeconds(long ttlSeconds) {
    this.ttlSeconds = ttlSeconds;
  }
}
