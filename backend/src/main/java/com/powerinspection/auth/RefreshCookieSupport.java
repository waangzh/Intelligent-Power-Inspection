package com.powerinspection.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshCookieSupport {
  public static final String COOKIE_NAME = "pi_refresh";

  public String read(HttpServletRequest request) {
    if (request.getCookies() == null) {
      return null;
    }
    for (Cookie cookie : request.getCookies()) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        return cookie.getValue();
      }
    }
    return null;
  }

  public void write(
      HttpServletResponse response,
      String rawToken,
      boolean remember,
      Instant expiresAt,
      boolean secure) {
    ResponseCookie.ResponseCookieBuilder builder =
        ResponseCookie.from(COOKIE_NAME, rawToken)
            .httpOnly(true)
            .secure(secure)
            .path("/api/v1/auth")
            .sameSite("Lax");
    if (remember) {
      long maxAge = Math.max(1, Duration.between(Instant.now(), expiresAt).getSeconds());
      builder.maxAge(maxAge);
    }
    // remember=false => session cookie (no Max-Age)
    response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
  }

  public void clear(HttpServletResponse response, boolean secure) {
    ResponseCookie cookie =
        ResponseCookie.from(COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secure)
            .path("/api/v1/auth")
            .sameSite("Lax")
            .maxAge(0)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  public boolean secureRequest(HttpServletRequest request) {
    if (request.isSecure()) {
      return true;
    }
    String forwarded = request.getHeader("X-Forwarded-Proto");
    return forwarded != null && forwarded.equalsIgnoreCase("https");
  }
}
