package com.powerinspection.security;

import com.powerinspection.user.UserEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUser implements UserDetails {
  private final UserEntity user;
  private final long authTimeEpochSeconds;

  public AuthenticatedUser(UserEntity user) {
    this(user, 0L);
  }

  public AuthenticatedUser(UserEntity user, long authTimeEpochSeconds) {
    this.user = user;
    this.authTimeEpochSeconds = authTimeEpochSeconds;
  }

  public UserEntity user() {
    return user;
  }

  public long authTimeEpochSeconds() {
    return authTimeEpochSeconds;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(() -> "ROLE_" + user.getRole().name());
  }

  @Override
  public String getPassword() {
    return user.getPasswordHash();
  }

  @Override
  public String getUsername() {
    return user.getUsername();
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return Boolean.TRUE.equals(user.getEnabled());
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return Boolean.TRUE.equals(user.getEnabled());
  }
}
