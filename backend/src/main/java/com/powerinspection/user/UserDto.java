package com.powerinspection.user;

public record UserDto(
  String id,
  String username,
  String displayName,
  UserRole role,
  String phone,
  String avatarUrl,
  String bio,
  Boolean enabled,
  String createdAt,
  String updatedAt
) {
  public static UserDto from(UserEntity user) {
    return new UserDto(
      user.getId(),
      user.getUsername(),
      user.getDisplayName(),
      user.getRole(),
      user.getPhone(),
      user.getAvatarUrl(),
      user.getBio(),
      user.getEnabled(),
      user.getCreatedAt(),
      user.getUpdatedAt()
    );
  }
}
