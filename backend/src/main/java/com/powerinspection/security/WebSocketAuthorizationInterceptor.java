package com.powerinspection.security;

import com.powerinspection.user.UserRepository;
import java.security.Principal;
import java.util.Map;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthorizationInterceptor implements ChannelInterceptor {
  private static final String USER_NOTIFICATION_PREFIX = "/topic/notifications/";

  private final TokenService tokenService;
  private final UserRepository userRepository;

  public WebSocketAuthorizationInterceptor(TokenService tokenService, UserRepository userRepository) {
    this.tokenService = tokenService;
    this.userRepository = userRepository;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null) {
      return message;
    }
    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      authenticate(accessor);
    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
      authorizeSubscription(accessor);
    }
    return message;
  }

  private void authenticate(StompHeaderAccessor accessor) {
    String authorization = accessor.getFirstNativeHeader("Authorization");
    if (authorization == null || !authorization.startsWith("Bearer ")) {
      throw new AccessDeniedException("WebSocket 连接缺少认证凭证");
    }
    String token = authorization.substring("Bearer ".length());
    Map<String, Object> claims = tokenService.claims(token);
    String userId = String.valueOf(claims.get("sub"));
    var user = userRepository.findById(userId)
      .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
      .orElseThrow(() -> new AccessDeniedException("WebSocket 用户不存在或已禁用"));
    AuthenticatedUser principal = new AuthenticatedUser(user, tokenService.authTime(token));
    accessor.setUser(new UsernamePasswordAuthenticationToken(
      principal,
      token,
      principal.getAuthorities()
    ));
  }

  private void authorizeSubscription(StompHeaderAccessor accessor) {
    Principal user = accessor.getUser();
    if (!(user instanceof UsernamePasswordAuthenticationToken authentication)
        || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
      throw new AccessDeniedException("WebSocket 订阅未认证");
    }
    String destination = accessor.getDestination();
    if (destination != null && destination.startsWith(USER_NOTIFICATION_PREFIX)) {
      String requestedUserId = destination.substring(USER_NOTIFICATION_PREFIX.length());
      if (!principal.user().getId().equals(requestedUserId)) {
        throw new AccessDeniedException("不能订阅其他用户的通知");
      }
    }
  }
}
