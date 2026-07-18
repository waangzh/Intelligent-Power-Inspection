package com.powerinspection.auth;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.config.JwtProperties;
import com.powerinspection.user.UserEntity;
import com.powerinspection.user.UserRepository;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {
  public record IssuedRefresh(String rawToken, RefreshTokenEntity entity, Instant expiresAt) {
  }

  public record RotatedSession(UserEntity user, IssuedRefresh refresh, long authTime) {
  }

  private final RefreshTokenRepository repository;
  private final UserRepository userRepository;
  private final JwtProperties jwtProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  public RefreshTokenService(
      RefreshTokenRepository repository,
      UserRepository userRepository,
      JwtProperties jwtProperties) {
    this.repository = repository;
    this.userRepository = userRepository;
    this.jwtProperties = jwtProperties;
  }

  @Transactional
  public IssuedRefresh issue(UserEntity user, boolean remember, long authTimeEpochSeconds) {
    long ttl = remember ? jwtProperties.getRefreshRememberTtlSeconds() : jwtProperties.getRefreshSessionTtlSeconds();
    Instant expiresAt = Instant.now().plusSeconds(ttl);
    String raw = randomToken();
    RefreshTokenEntity entity = new RefreshTokenEntity();
    entity.setId(Ids.next("rt"));
    entity.setUserId(user.getId());
    entity.setTokenHash(hash(raw));
    entity.setFamilyId(Ids.next("rtf"));
    entity.setRemember(remember);
    entity.setExpiresAt(expiresAt.toString());
    entity.setAuthTimeEpochSeconds(authTimeEpochSeconds);
    entity.setCreatedAt(Instant.now().toString());
    repository.save(entity);
    return new IssuedRefresh(raw, entity, expiresAt);
  }

  @Transactional(dontRollbackOn = ApiException.class)
  public RotatedSession rotate(String rawToken) {
    return rotate(rawToken, null);
  }

  @Transactional(dontRollbackOn = ApiException.class)
  public RotatedSession rotateAfterReauthentication(String rawToken, long authTimeEpochSeconds) {
    return rotate(rawToken, authTimeEpochSeconds);
  }

  private RotatedSession rotate(String rawToken, Long replacementAuthTimeEpochSeconds) {
    RefreshTokenEntity current = repository.findByTokenHashForUpdate(hash(rawToken))
      .orElseThrow(() -> ApiException.unauthorized("刷新凭证无效"));
    if (current.getRevokedAt() != null) {
      repository.revokeFamily(current.getFamilyId(), Instant.now().toString());
      throw ApiException.unauthorized("刷新凭证已失效，请重新登录");
    }
    Instant expiresAt = Instant.parse(current.getExpiresAt());
    if (expiresAt.isBefore(Instant.now())) {
      current.setRevokedAt(Instant.now().toString());
      repository.save(current);
      throw ApiException.unauthorized("登录已过期，请重新登录");
    }
    UserEntity user = userRepository.findById(current.getUserId())
      .orElseThrow(() -> ApiException.unauthorized("用户不存在"));
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      revokeAllForUser(user.getId());
      throw ApiException.forbidden("用户已被禁用");
    }

    String nextRaw = randomToken();
    RefreshTokenEntity next = new RefreshTokenEntity();
    next.setId(Ids.next("rt"));
    next.setUserId(user.getId());
    next.setTokenHash(hash(nextRaw));
    next.setFamilyId(current.getFamilyId());
    next.setRemember(current.isRemember());
    next.setExpiresAt(current.getExpiresAt());
    long authTime = replacementAuthTimeEpochSeconds == null
      ? current.getAuthTimeEpochSeconds()
      : replacementAuthTimeEpochSeconds;
    next.setAuthTimeEpochSeconds(authTime);
    next.setCreatedAt(Instant.now().toString());
    repository.save(next);

    current.setRevokedAt(Instant.now().toString());
    current.setReplacedById(next.getId());
    repository.save(current);

    return new RotatedSession(user, new IssuedRefresh(nextRaw, next, expiresAt), authTime);
  }

  @Transactional
  public void revokeRaw(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    repository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
      if (token.getRevokedAt() == null) {
        token.setRevokedAt(Instant.now().toString());
        repository.save(token);
      }
    });
  }

  @Transactional
  public void revokeAllForUser(String userId) {
    repository.revokeAllActiveForUser(userId, Instant.now().toString());
  }

  private String randomToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String raw) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception ex) {
      throw new IllegalStateException("无法计算刷新令牌摘要", ex);
    }
  }
}
