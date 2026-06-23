package com.powerinspection.security;

import com.powerinspection.common.ApiException;
import com.powerinspection.user.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  public UserEntity get() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
      throw ApiException.unauthorized("未登录");
    }
    return principal.user();
  }
}
