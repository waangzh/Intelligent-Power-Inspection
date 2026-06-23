package com.powerinspection.user;

public record UserActivityDto(String id, String userId, String type, String message, String createdAt) {
  public static UserActivityDto from(UserActivityEntity entity) {
    return new UserActivityDto(entity.getId(), entity.getUserId(), entity.getType(), entity.getMessage(), entity.getCreatedAt());
  }
}
