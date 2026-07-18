package com.powerinspection.auth;

import com.powerinspection.auth.AuthDtos.ChangePasswordRequest;
import com.powerinspection.auth.AuthDtos.LoginRequest;
import com.powerinspection.auth.AuthDtos.LoginResponse;
import com.powerinspection.auth.AuthDtos.MeResponse;
import com.powerinspection.auth.AuthDtos.ProfileRequest;
import com.powerinspection.auth.AuthDtos.RegisterRequest;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.security.TokenService;
import com.powerinspection.user.UserActivityDto;
import com.powerinspection.user.UserActivityEntity;
import com.powerinspection.user.UserActivityRepository;
import com.powerinspection.user.UserDto;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserPreferenceEntity;
import com.powerinspection.user.UserPreferenceRepository;
import com.powerinspection.user.UserPreferencesDto;
import com.powerinspection.user.UserRepository;
import com.powerinspection.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");
  private static final Pattern HAS_LETTER = Pattern.compile("[a-zA-Z]");
  private static final Pattern HAS_DIGIT = Pattern.compile("[0-9]");

  private final UserRepository userRepository;
  private final UserPreferenceRepository preferenceRepository;
  private final UserActivityRepository activityRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final UserAccessService userAccessService;
  private final RefreshTokenService refreshTokenService;
  private final RefreshCookieSupport refreshCookieSupport;

  public AuthService(
    UserRepository userRepository,
    UserPreferenceRepository preferenceRepository,
    UserActivityRepository activityRepository,
    PasswordEncoder passwordEncoder,
    TokenService tokenService,
    UserAccessService userAccessService,
    RefreshTokenService refreshTokenService,
    RefreshCookieSupport refreshCookieSupport
  ) {
    this.userRepository = userRepository;
    this.preferenceRepository = preferenceRepository;
    this.activityRepository = activityRepository;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.userAccessService = userAccessService;
    this.refreshTokenService = refreshTokenService;
    this.refreshCookieSupport = refreshCookieSupport;
  }

  public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    UserEntity user = userRepository.findByUsername(request.username().trim())
      .orElseThrow(() -> ApiException.badRequest("用户名或密码错误"));
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw ApiException.forbidden("用户已被禁用");
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw ApiException.badRequest("用户名或密码错误");
    }
    logActivity(user.getId(), "LOGIN", "登录系统");
    long authTime = Instant.now().getEpochSecond();
    String access = tokenService.create(user, authTime);
    RefreshTokenService.IssuedRefresh refresh = refreshTokenService.issue(user, request.remember(), authTime);
    refreshCookieSupport.write(
      httpResponse,
      refresh.rawToken(),
      request.remember(),
      refresh.expiresAt(),
      refreshCookieSupport.secureRequest(httpRequest)
    );
    MeResponse accessInfo = userAccessService.me(user);
    return new LoginResponse(
      access,
      accessInfo.user(),
      accessInfo.permissions(),
      accessInfo.scopes(),
      accessInfo.features(),
      tokenService.accessExpiresAtEpochMilli()
    );
  }

  public LoginResponse refresh(HttpServletRequest request, HttpServletResponse response) {
    String raw = refreshCookieSupport.read(request);
    if (raw == null || raw.isBlank()) {
      throw ApiException.unauthorized("缺少刷新凭证");
    }
    RefreshTokenService.RotatedSession rotated = refreshTokenService.rotate(raw);
    String access = tokenService.create(rotated.user(), rotated.authTime());
    refreshCookieSupport.write(
      response,
      rotated.refresh().rawToken(),
      rotated.refresh().entity().isRemember(),
      rotated.refresh().expiresAt(),
      refreshCookieSupport.secureRequest(request)
    );
    MeResponse accessInfo = userAccessService.me(rotated.user());
    return new LoginResponse(
      access,
      accessInfo.user(),
      accessInfo.permissions(),
      accessInfo.scopes(),
      accessInfo.features(),
      tokenService.accessExpiresAtEpochMilli()
    );
  }

  public LoginResponse reauth(UserEntity user, String password, HttpServletRequest request, HttpServletResponse response) {
    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw ApiException.badRequest("密码不正确");
    }
    long authTime = Instant.now().getEpochSecond();
    String access = tokenService.create(user, authTime);
    String existingRefresh = refreshCookieSupport.read(request);
    if (existingRefresh != null && !existingRefresh.isBlank()) {
      try {
        RefreshTokenService.RotatedSession rotated =
          refreshTokenService.rotateAfterReauthentication(existingRefresh, authTime);
        access = tokenService.create(rotated.user(), authTime);
        refreshCookieSupport.write(
          response,
          rotated.refresh().rawToken(),
          rotated.refresh().entity().isRemember(),
          rotated.refresh().expiresAt(),
          refreshCookieSupport.secureRequest(request)
        );
      } catch (ApiException ignored) {
        // keep access token even if refresh rotation fails
      }
    }
    MeResponse accessInfo = userAccessService.me(user);
    logActivity(user.getId(), "LOGIN", "完成高风险操作再认证");
    return new LoginResponse(
      access,
      accessInfo.user(),
      accessInfo.permissions(),
      accessInfo.scopes(),
      accessInfo.features(),
      tokenService.accessExpiresAtEpochMilli()
    );
  }

  public void logout(HttpServletRequest request, HttpServletResponse response) {
    refreshTokenService.revokeRaw(refreshCookieSupport.read(request));
    refreshCookieSupport.clear(response, refreshCookieSupport.secureRequest(request));
  }

  @Transactional
  public UserDto register(RegisterRequest request) {
    String username = request.username().trim();
    validateUsername(username);
    validatePassword(request.password());
    if (!request.password().equals(request.confirmPassword())) {
      throw ApiException.badRequest("两次输入的密码不一致");
    }
    if (!request.agreed()) {
      throw ApiException.badRequest("请阅读并同意服务条款");
    }
    if (userRepository.existsByUsername(username)) {
      throw ApiException.badRequest("用户名已被注册");
    }
    UserEntity user = new UserEntity();
    user.setId(Ids.next("user"));
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setDisplayName(request.displayName().trim());
    user.setRole(UserRole.VIEWER);
    user.setPhone(blankToNull(request.phone()));
    user.setBio("");
    user.setAvatarUrl(defaultAvatar(user.getDisplayName(), user.getId()));
    user.setEnabled(true);
    user.setCreatedAt(Instant.now().toString());
    UserEntity saved = userRepository.save(user);
    ensurePreferences(saved.getId());
    return UserDto.from(saved);
  }

  @Transactional
  public UserDto updateProfile(UserEntity user, ProfileRequest request) {
    if (request.displayName() != null) {
      if (request.displayName().trim().isBlank()) {
        throw ApiException.badRequest("请填写姓名");
      }
      user.setDisplayName(request.displayName().trim());
    }
    if (request.phone() != null) {
      user.setPhone(blankToNull(request.phone()));
    }
    if (request.bio() != null) {
      if (request.bio().length() > 80) {
        throw ApiException.badRequest("个性签名不能超过 80 字");
      }
      user.setBio(request.bio().trim());
    }
    if (request.avatarUrl() != null) {
      user.setAvatarUrl(request.avatarUrl());
    }
    user.setUpdatedAt(Instant.now().toString());
    UserEntity saved = userRepository.save(user);
    logActivity(user.getId(), request.avatarUrl() != null ? "AVATAR" : "PROFILE", request.avatarUrl() != null ? "更新了个人头像" : "更新了个人资料");
    return UserDto.from(saved);
  }

  @Transactional
  public void changePassword(UserEntity user, ChangePasswordRequest request) {
    if (!request.newPassword().equals(request.confirmPassword())) {
      throw ApiException.badRequest("两次输入的新密码不一致");
    }
    validatePassword(request.newPassword());
    if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
      throw ApiException.badRequest("原密码不正确");
    }
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setUpdatedAt(Instant.now().toString());
    // Flush before revoke: @Modifying clearAutomatically would otherwise drop unflushed password hash.
    userRepository.saveAndFlush(user);
    refreshTokenService.revokeAllForUser(user.getId());
    logActivity(user.getId(), "PASSWORD", "修改了登录密码");
  }

  public UserPreferencesDto preferences(String userId) {
    return UserPreferencesDto.from(ensurePreferences(userId));
  }

  @Transactional
  public UserPreferencesDto savePreferences(String userId, UserPreferencesDto dto) {
    UserPreferenceEntity entity = ensurePreferences(userId);
    entity.setNotifyAlarm(dto.notifyAlarm());
    entity.setNotifyTask(dto.notifyTask());
    entity.setNotifySystem(dto.notifySystem());
    entity.setDefaultSiteId(dto.defaultSiteId());
    entity.setSidebarCollapsed(dto.sidebarCollapsed());
    preferenceRepository.save(entity);
    logActivity(userId, "SETTINGS", "更新了偏好设置");
    return UserPreferencesDto.from(entity);
  }

  public List<UserActivityDto> activities(String userId) {
    return activityRepository.findTop200ByUserIdOrderByCreatedAtDesc(userId).stream().map(UserActivityDto::from).toList();
  }

  @Transactional
  public void logActivity(String userId, String type, String message) {
    UserActivityEntity activity = new UserActivityEntity();
    activity.setId(Ids.next("act"));
    activity.setUserId(userId);
    activity.setType(type);
    activity.setMessage(message);
    activity.setCreatedAt(Instant.now().toString());
    activityRepository.save(activity);
  }

  private UserPreferenceEntity ensurePreferences(String userId) {
    return preferenceRepository.findById(userId).orElseGet(() -> {
      UserPreferenceEntity entity = new UserPreferenceEntity();
      entity.setUserId(userId);
      return preferenceRepository.save(entity);
    });
  }

  private void validateUsername(String username) {
    if (!USERNAME.matcher(username).matches()) {
      throw ApiException.badRequest(username.length() < 4 || username.length() > 20 ? "用户名长度为 4～20 位" : "用户名只能包含字母、数字和下划线");
    }
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 8) {
      throw ApiException.badRequest("密码至少 8 位");
    }
    if (!HAS_LETTER.matcher(password).find() || !HAS_DIGIT.matcher(password).find()) {
      throw ApiException.badRequest("密码须同时包含字母和数字");
    }
  }

  private String blankToNull(String value) {
    if (value == null || value.trim().isBlank()) {
      return null;
    }
    return value.trim();
  }

  private String defaultAvatar(String displayName, String id) {
    return "https://api.dicebear.com/9.x/initials/svg?seed=" + displayName + "-" + id;
  }
}
