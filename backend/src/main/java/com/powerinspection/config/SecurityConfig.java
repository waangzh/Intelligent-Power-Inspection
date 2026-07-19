package com.powerinspection.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.robot.RobotBridgeIdMapper;
import com.powerinspection.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      AuthenticationEntryPoint authenticationEntryPoint,
      AccessDeniedHandler accessDeniedHandler)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
        .cors(cors -> {})
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/health",
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/refresh",
                        "/ws/**",
                        "/model-files/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/internal/robot-map-assets")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/internal/robot-inspection-images")
                    .permitAll()
                    .requestMatchers("/h2-console/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/route-deployments/*",
                        "/api/v1/route-revisions/*",
                        "/api/v1/map-assets/*",
                        "/api/v1/map-assets/*/yaml",
                        "/api/v1/map-assets/*/pgm")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * 未登录 / token 失效：统一返回 401 + ApiResponse JSON，而不是 Spring Security 默认的 403。 Bridge
   * 平台凭据不是标准登录态，命中需登录接口时视为“已识别但无权限”，保留 403。
   */
  @Bean
  AuthenticationEntryPoint authenticationEntryPoint(
      ObjectMapper objectMapper, RobotBridgeIdMapper robotBridgeIdMapper) {
    return (request, response, authException) -> {
      boolean bridgeRequest =
          robotBridgeIdMapper.isBridgePlatformRequest(request.getHeader("Authorization"));
      if (bridgeRequest) {
        writeJsonError(response, objectMapper, HttpStatus.FORBIDDEN, 403, "Bridge 凭据无权访问该接口");
      } else {
        writeJsonError(response, objectMapper, HttpStatus.UNAUTHORIZED, 401, "未登录或登录状态已失效");
      }
    };
  }

  /** 已登录但权限不足（Spring Security 层面拒绝，如非法 CORS/方法级校验）：返回 403 + ApiResponse JSON。 */
  @Bean
  AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
    return (request, response, accessDeniedException) ->
        writeJsonError(response, objectMapper, HttpStatus.FORBIDDEN, 403, "没有访问权限");
  }

  private static void writeJsonError(
      jakarta.servlet.http.HttpServletResponse response,
      ObjectMapper objectMapper,
      HttpStatus status,
      int code,
      String message)
      throws java.io.IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(code, message)));
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    CorsConfiguration config = new CorsConfiguration();
    if (!corsProperties.getAllowedOriginPatterns().isEmpty()) {
      config.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());
    } else {
      config.setAllowedOrigins(corsProperties.getAllowedOrigins());
    }
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setExposedHeaders(List.of("Authorization"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
