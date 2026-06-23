package com.powerinspection.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.config.JwtProperties;
import com.powerinspection.user.UserEntity;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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
    try {
      String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
      long exp = Instant.now().plusSeconds(jwtProperties.getTtlSeconds()).getEpochSecond();
      String payload = encodeJson(Map.of("sub", user.getId(), "username", user.getUsername(), "role", user.getRole().name(), "exp", exp));
      return header + "." + payload + "." + sign(header + "." + payload);
    } catch (Exception ex) {
      throw ApiException.unauthorized("Token 生成失败");
    }
  }

  public String subject(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        throw ApiException.unauthorized("Token 无效");
      }
      String expected = sign(parts[0] + "." + parts[1]);
      if (!constantTimeEquals(expected, parts[2])) {
        throw ApiException.unauthorized("Token 无效");
      }
      Map<String, Object> payload = objectMapper.readValue(base64Decode(parts[1]), new TypeReference<>() {
      });
      Number exp = (Number) payload.get("exp");
      if (exp == null || exp.longValue() < Instant.now().getEpochSecond()) {
        throw ApiException.unauthorized("登录已过期");
      }
      Object subject = payload.get("sub");
      if (subject == null) {
        throw ApiException.unauthorized("Token 无效");
      }
      return subject.toString();
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      throw ApiException.unauthorized("Token 无效");
    }
  }

  private String encodeJson(Object value) throws Exception {
    return base64Encode(objectMapper.writeValueAsBytes(value));
  }

  private String sign(String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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
