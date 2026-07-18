package com.powerinspection.security;

import com.powerinspection.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final TokenService tokenService;
  private final UserRepository userRepository;

  public JwtAuthenticationFilter(TokenService tokenService, UserRepository userRepository) {
    this.tokenService = tokenService;
    this.userRepository = userRepository;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
    throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      String token = authorization.substring("Bearer ".length());
      try {
        Map<String, Object> claims = tokenService.claims(token);
        String userId = String.valueOf(claims.get("sub"));
        userRepository.findById(userId).ifPresent(user -> {
          if (!Boolean.TRUE.equals(user.getEnabled())) {
            SecurityContextHolder.clearContext();
            return;
          }
          long authTime = 0L;
          Object authTimeClaim = claims.get("auth_time");
          if (authTimeClaim instanceof Number number) {
            authTime = number.longValue();
          }
          AuthenticatedUser principal = new AuthenticatedUser(user, authTime);
          UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            token,
            principal.getAuthorities()
          );
          authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        });
      } catch (Exception ignored) {
        SecurityContextHolder.clearContext();
      }
    }
    filterChain.doFilter(request, response);
  }
}
