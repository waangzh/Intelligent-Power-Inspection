package com.powerinspection.auth;

import com.powerinspection.user.UserDto;
import com.powerinspection.user.UserPreferencesDto;
import com.powerinspection.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public final class AuthDtos {
  private AuthDtos() {
  }

  public record LoginRequest(@NotBlank(message = "请输入用户名") String username, @NotBlank(message = "请输入密码") String password, boolean remember) {
  }

  public record RegisterRequest(
    @NotBlank(message = "请输入用户名") String username,
    @NotBlank(message = "请输入密码") String password,
    @NotBlank(message = "请再次输入密码") String confirmPassword,
    @NotBlank(message = "请填写姓名") String displayName,
    String phone,
    boolean agreed
  ) {
  }

  public record MeResponse(
    UserDto user,
    List<String> permissions,
    Map<String, Object> scopes,
    Map<String, Object> features
  ) {
  }

  public record LoginResponse(
    String token,
    UserDto user,
    List<String> permissions,
    Map<String, Object> scopes,
    Map<String, Object> features,
    Long expiresAt
  ) {
  }

  public record ReauthRequest(@NotBlank(message = "请输入密码") String password) {
  }

  public record ProfileRequest(String displayName, String phone, String bio, String avatarUrl) {
  }

  public record ChangePasswordRequest(
    @NotBlank(message = "请输入原密码") String oldPassword,
    @NotBlank(message = "请输入新密码") String newPassword,
    @NotBlank(message = "请再次输入新密码") String confirmPassword
  ) {
  }

  public record RoleRequest(@NotNull(message = "请选择角色") UserRole role) {
  }

  public record EnabledRequest(boolean enabled) {
  }

  public record PreferencesRequest(
    boolean notifyAlarm,
    boolean notifyTask,
    boolean notifySystem,
    String defaultSiteId,
    boolean sidebarCollapsed
  ) {
    public UserPreferencesDto toDto() {
      return new UserPreferencesDto(notifyAlarm, notifyTask, notifySystem, defaultSiteId, sidebarCollapsed);
    }
  }
}
