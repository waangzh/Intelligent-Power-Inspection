package com.powerinspection.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.config.JwtProperties;
import com.powerinspection.user.UserEntity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final JwtProperties jwtProperties;
  private final ObjectMapper objectMapper;

  public TokenService(JwtProperties jwtProperties, ObjectMapper objectMapper) {
    this.jwtProperties = jwtProperties;
    this.objectMapper = objectMapper;
  }

  public String create(UserEntity user) {
    return create(user, Instant.now().getEpochSecond());
  }

  public String create(UserEntity user, long authTimeEpochSeconds) {
    try {
      String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
      long now = Instant.now().getEpochSecond();
      long exp = now + jwtProperties.getAccessTtlSeconds();
      Map<String, Object> claims = new LinkedHashMap<>();
      claims.put("sub", user.getId());
      claims.put("username", user.getUsername());
      claims.put("role", user.getRole().name());
      claims.put("token_version", user.getTokenVersion());
      claims.put("iat", now);
      claims.put("auth_time", authTimeEpochSeconds);
      claims.put("exp", exp);
      String payload = encodeJson(claims);
      return header + "." + payload + "." + sign(header + "." + payload);
    } catch (Exception ex) {
      throw ApiException.unauthorized("Token 生成失败");
    }
  }

  public String subject(String token) {
    return String.valueOf(claims(token).get("sub"));
  }

  public Map<String, Object> claims(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        throw ApiException.unauthorized("Token 无效");
      }
      String expected = sign(parts[0] + "." + parts[1]);
      if (!constantTimeEquals(expected, parts[2])) {
        throw ApiException.unauthorized("Token 无效");
      }
      Map<String, Object> payload =
          objectMapper.readValue(base64Decode(parts[1]), new TypeReference<>() {});
      Number exp = (Number) payload.get("exp");
      if (exp == null || exp.longValue() < Instant.now().getEpochSecond()) {
        throw ApiException.unauthorized("登录已过期");
      }
      if (payload.get("sub") == null) {
        throw ApiException.unauthorized("Token 无效");
      }
      return payload;
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw ApiException.unauthorized("Token 无效");
    }
  }

  public long authTime(String token) {
    Object value = claims(token).get("auth_time");
    if (value instanceof Number number) {
      return number.longValue();
    }
    Object iat = claims(token).get("iat");
    if (iat instanceof Number number) {
      return number.longValue();
    }
    return 0L;
  }

  public void validateUserToken(UserEntity user, Map<String, Object> claims) {
    if (!Boolean.TRUE.equals(user.getEnabled())) {
      throw ApiException.unauthorized("用户已被禁用");
    }
    Object claim = claims.get("token_version");
    if (!(claim instanceof Number number) || number.longValue() != user.getTokenVersion()) {
      throw ApiException.unauthorized("登录状态已失效，请重新登录");
    }
  }

  public void requireRecentAuth(String token) {
    long authTime = authTime(token);
    long window = jwtProperties.getReauthWindowSeconds();
    if (authTime <= 0 || Instant.now().getEpochSecond() - authTime > window) {
      throw ApiException.forbidden("高风险操作需要近期重新认证，请先验证密码");
    }
  }

  public long accessExpiresAtEpochMilli() {
    return Instant.now().plusSeconds(jwtProperties.getAccessTtlSeconds()).toEpochMilli();
  }

  private String encodeJson(Object value) throws Exception {
    return base64Encode(objectMapper.writeValueAsBytes(value));
  }

  private String sign(String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(
        new SecretKeySpec(
            jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return base64Encode(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
  }

  private String base64Encode(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private byte[] base64Decode(String value) {
    return Base64.getUrlDecoder().decode(value);
  }

  private boolean constantTimeEquals(String a, String b) {
    if (a.length() != b.length()) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }
}
