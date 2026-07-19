package com.powerinspection.user;

import com.powerinspection.auth.AuthDtos.EnabledRequest;
import com.powerinspection.auth.AuthDtos.PreferencesRequest;
import com.powerinspection.auth.AuthDtos.ProfileRequest;
import com.powerinspection.auth.AuthDtos.RoleRequest;
import com.powerinspection.auth.AuthService;
import com.powerinspection.auth.RefreshTokenService;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final UserRepository userRepository;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;
  private final AuthService authService;
  private final RefreshTokenService refreshTokenService;

  public UserController(
      UserRepository userRepository,
      PermissionService permissionService,
      CurrentUser currentUser,
      AuthService authService,
      RefreshTokenService refreshTokenService) {
    this.userRepository = userRepository;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.authService = authService;
    this.refreshTokenService = refreshTokenService;
  }

  @GetMapping
  public ApiResponse<List<UserDto>> list() {
    permissionService.require(currentUser.get(), Permission.USER_MANAGE);
    return ApiResponse.ok(
        userRepository.findAll().stream()
            .sorted(Comparator.comparing(UserEntity::getCreatedAt))
            .map(UserDto::from)
            .toList());
  }

  @PatchMapping("/{id}/role")
  public ApiResponse<UserDto> role(
      @PathVariable String id, @Valid @RequestBody RoleRequest request) {
    permissionService.require(currentUser.get(), Permission.USER_MANAGE);
    UserEntity user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("用户不存在"));
    user.setRole(request.role());
    user.setUpdatedAt(Instant.now().toString());
    return ApiResponse.ok(UserDto.from(userRepository.save(user)));
  }

  @PatchMapping("/{id}/enabled")
  public ApiResponse<UserDto> enabled(
      @PathVariable String id, @RequestBody EnabledRequest request) {
    permissionService.require(currentUser.get(), Permission.USER_MANAGE);
    UserEntity user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("用户不存在"));
    user.setEnabled(request.enabled());
    user.setUpdatedAt(Instant.now().toString());
    if (!request.enabled()) {
      user.incrementTokenVersion();
    }
    UserDto saved = UserDto.from(userRepository.saveAndFlush(user));
    if (!request.enabled()) {
      refreshTokenService.revokeAllForUser(id);
    }
    return ApiResponse.ok(saved);
  }

  @PatchMapping("/me")
  public ApiResponse<UserDto> updateMe(@RequestBody ProfileRequest request) {
    return ApiResponse.ok(authService.updateProfile(currentUser.get(), request));
  }

  @GetMapping("/me/activities")
  public ApiResponse<List<UserActivityDto>> activities() {
    return ApiResponse.ok(authService.activities(currentUser.get().getId()));
  }

  @GetMapping("/me/preferences")
  public ApiResponse<UserPreferencesDto> preferences() {
    return ApiResponse.ok(authService.preferences(currentUser.get().getId()));
  }

  @PutMapping("/me/preferences")
  public ApiResponse<UserPreferencesDto> savePreferences(@RequestBody PreferencesRequest request) {
    return ApiResponse.ok(authService.savePreferences(currentUser.get().getId(), request.toDto()));
  }
}
