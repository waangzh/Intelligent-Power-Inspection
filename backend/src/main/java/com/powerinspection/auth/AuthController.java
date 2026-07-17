package com.powerinspection.auth;

import com.powerinspection.auth.AuthDtos.ChangePasswordRequest;
import com.powerinspection.auth.AuthDtos.LoginRequest;
import com.powerinspection.auth.AuthDtos.LoginResponse;
import com.powerinspection.auth.AuthDtos.MeResponse;
import com.powerinspection.auth.AuthDtos.ProfileRequest;
import com.powerinspection.auth.AuthDtos.RegisterRequest;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.UserDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService authService;
  private final UserAccessService userAccessService;
  private final CurrentUser currentUser;

  public AuthController(AuthService authService, UserAccessService userAccessService, CurrentUser currentUser) {
    this.authService = authService;
    this.userAccessService = userAccessService;
    this.currentUser = currentUser;
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return ApiResponse.ok(authService.login(request));
  }

  @PostMapping("/register")
  public ApiResponse<UserDto> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResponse.ok(authService.register(request));
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout() {
    return ApiResponse.ok();
  }

  @GetMapping("/me")
  public ApiResponse<MeResponse> me() {
    return ApiResponse.ok(userAccessService.me(currentUser.get()));
  }

  @PatchMapping("/me")
  public ApiResponse<UserDto> updateMe(@RequestBody ProfileRequest request) {
    return ApiResponse.ok(authService.updateProfile(currentUser.get(), request));
  }

  @PutMapping("/password")
  public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
    authService.changePassword(currentUser.get(), request);
    return ApiResponse.ok();
  }
}
