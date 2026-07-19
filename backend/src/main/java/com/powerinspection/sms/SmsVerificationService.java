package com.powerinspection.sms;

import com.powerinspection.common.ApiException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class SmsVerificationService {
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SmsProperties properties;
  private final MockSmsSender mockSmsSender;
  private final PnvsSmsClient pnvsSmsClient;
  private final PasswordEncoder passwordEncoder;
  private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

  public SmsVerificationService(
      SmsProperties properties,
      MockSmsSender mockSmsSender,
      PnvsSmsClient pnvsSmsClient,
      PasswordEncoder passwordEncoder) {
    this.properties = properties;
    this.mockSmsSender = mockSmsSender;
    this.pnvsSmsClient = pnvsSmsClient;
    this.passwordEncoder = passwordEncoder;
  }

  public SendResult sendCode(String rawPhone, SmsPurpose purpose) {
    if (!properties.verificationRequired()) {
      throw ApiException.badRequest("当前未启用短信验证码");
    }
    String phone = normalizePhone(rawPhone);
    String key = key(phone, purpose);
    Instant now = Instant.now();
    Challenge existing = challenges.get(key);
    if (existing != null) {
      if (existing.lastSentAt.plusSeconds(properties.getResendIntervalSeconds()).isAfter(now)) {
        throw ApiException.badRequest("发送过于频繁，请稍后再试");
      }
      if (sameUtcDay(existing.dayAnchor, now)
          && existing.dailyCount >= properties.getDailyLimitPerPhone()) {
        throw ApiException.badRequest("今日发送次数已达上限");
      }
    }

    String debugCode = null;
    String codeHash = null;
    if (properties.resolvedMode() == SmsMode.MOCK) {
      String code = generateCode(properties.getCodeLength());
      mockSmsSender.sendVerificationCode(phone, code);
      codeHash = passwordEncoder.encode(code);
      debugCode = code;
    } else {
      pnvsSmsClient.sendVerifyCode(phone, properties.templateCodeFor(purpose));
    }

    int dailyCount = 1;
    Instant dayAnchor = now;
    if (existing != null && sameUtcDay(existing.dayAnchor, now)) {
      dailyCount = existing.dailyCount + 1;
      dayAnchor = existing.dayAnchor;
    }
    challenges.put(
        key,
        new Challenge(
            codeHash,
            now.plusSeconds(properties.getCodeTtlSeconds()),
            now,
            dayAnchor,
            dailyCount,
            false));

    return new SendResult(
        phone, properties.getResendIntervalSeconds(), properties.getCodeTtlSeconds(), debugCode);
  }

  public void consumeCode(String rawPhone, SmsPurpose purpose, String rawCode) {
    if (!properties.verificationRequired()) {
      return;
    }
    String phone = normalizePhone(rawPhone);
    if (rawCode == null || rawCode.isBlank()) {
      throw ApiException.badRequest("请输入短信验证码");
    }
    String code = rawCode.trim();
    String key = key(phone, purpose);
    Challenge challenge = challenges.get(key);
    if (challenge == null || challenge.consumed) {
      throw ApiException.badRequest("请先获取短信验证码");
    }
    if (challenge.expiresAt.isBefore(Instant.now())) {
      challenges.remove(key);
      throw ApiException.badRequest("验证码已过期，请重新获取");
    }

    if (properties.resolvedMode() == SmsMode.MOCK) {
      if (challenge.codeHash == null || !passwordEncoder.matches(code, challenge.codeHash)) {
        throw ApiException.badRequest("验证码错误");
      }
    } else {
      pnvsSmsClient.checkVerifyCode(phone, code);
    }

    challenges.put(
        key,
        new Challenge(
            challenge.codeHash,
            challenge.expiresAt,
            challenge.lastSentAt,
            challenge.dayAnchor,
            challenge.dailyCount,
            true));
  }

  public static String normalizePhone(String rawPhone) {
    if (rawPhone == null || rawPhone.isBlank()) {
      throw ApiException.badRequest("请输入手机号");
    }
    String phone = rawPhone.trim().replaceAll("\\s+", "");
    if (phone.startsWith("+86")) {
      phone = phone.substring(3);
    } else if (phone.startsWith("86") && phone.length() == 13) {
      phone = phone.substring(2);
    }
    if (!phone.matches("^1\\d{10}$")) {
      throw ApiException.badRequest("手机号格式不正确");
    }
    return phone;
  }

  private static String key(String phone, SmsPurpose purpose) {
    return purpose.name() + ":" + phone;
  }

  private static String generateCode(int length) {
    int digits = Math.max(4, Math.min(8, length));
    int bound = (int) Math.pow(10, digits);
    int value = RANDOM.nextInt(bound / 10, bound);
    return String.valueOf(value);
  }

  private static boolean sameUtcDay(Instant a, Instant b) {
    return a.atZone(java.time.ZoneOffset.UTC)
        .toLocalDate()
        .equals(b.atZone(java.time.ZoneOffset.UTC).toLocalDate());
  }

  public record SendResult(
      String phone, long resendIntervalSeconds, long expiresInSeconds, String debugCode) {}

  private record Challenge(
      String codeHash,
      Instant expiresAt,
      Instant lastSentAt,
      Instant dayAnchor,
      int dailyCount,
      boolean consumed) {}
}
