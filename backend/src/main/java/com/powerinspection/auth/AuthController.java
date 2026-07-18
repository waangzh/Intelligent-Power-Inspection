package com.powerinspection.auth;

import com.powerinspection.auth.AuthDtos.ChangePasswordRequest;
import com.powerinspection.auth.AuthDtos.LoginRequest;
import com.powerinspection.auth.AuthDtos.LoginResponse;
import com.powerinspection.auth.AuthDtos.MeResponse;
import com.powerinspection.auth.AuthDtos.ProfileRequest;
import com.powerinspection.auth.AuthDtos.ReauthRequest;
import com.powerinspection.auth.AuthDtos.RegisterRequest;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.UserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
  private final RefreshCookieSupport refreshCookieSupport;

  public AuthController(
      AuthService authService,
      UserAccessService userAccessService,
      CurrentUser currentUser,
      RefreshCookieSupport refreshCookieSupport) {
    this.authService = authService;
    this.userAccessService = userAccessService;
    this.currentUser = currentUser;
    this.refreshCookieSupport = refreshCookieSupport;
  }

  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    return ApiResponse.ok(authService.login(request, httpRequest, httpResponse));
  }

  @PostMapping("/register")
  public ApiResponse<UserDto> register(@Valid @RequestBody RegisterRequest request) {
    return ApiResponse.ok(authService.register(request));
  }

  @PostMapping("/refresh")
  public ApiResponse<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
    return ApiResponse.ok(authService.refresh(request, response));
  }

  @PostMapping("/reauth")
  public ApiResponse<LoginResponse> reauth(
      @Valid @RequestBody ReauthRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    return ApiResponse.ok(authService.reauth(currentUser.get(), request.password(), httpRequest, httpResponse));
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    authService.logout(request, response);
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
  public ApiResponse<Void> changePassword(
      @Valid @RequestBody ChangePasswordRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    authService.changePassword(currentUser.get(), request);
    refreshCookieSupport.clear(httpResponse, refreshCookieSupport.secureRequest(httpRequest));
    return ApiResponse.ok();
  }
}
