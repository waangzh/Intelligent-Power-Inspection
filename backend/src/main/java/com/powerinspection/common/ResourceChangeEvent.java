package com.powerinspection.common;

import java.time.Instant;

public record ResourceChangeEvent(
    String resource,
    String resourceId,
    String operation,
    Instant updatedAt
) {
  public static ResourceChangeEvent updated(String resource, Object resourceId) {
    return new ResourceChangeEvent(resource, String.valueOf(resourceId), "UPDATED", Instant.now());
  }

  public static ResourceChangeEvent created(String resource, Object resourceId) {
    return new ResourceChangeEvent(resource, String.valueOf(resourceId), "CREATED", Instant.now());
  }

  public static ResourceChangeEvent deleted(String resource, Object resourceId) {
    return new ResourceChangeEvent(resource, String.valueOf(resourceId), "DELETED", Instant.now());
  }
}
