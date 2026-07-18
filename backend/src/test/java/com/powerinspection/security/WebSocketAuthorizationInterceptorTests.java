package com.powerinspection.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.powerinspection.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class WebSocketAuthorizationInterceptorTests {
  @Autowired
  WebSocketAuthorizationInterceptor interceptor;

  @Autowired
  TokenService tokenService;

  @Autowired
  UserRepository userRepository;

  @Test
  void connectRequiresValidBearerToken() {
    StompHeaderAccessor accessor = accessor(StompCommand.CONNECT);

    assertThatThrownBy(() -> interceptor.preSend(message(accessor), null))
      .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void userCanSubscribeOnlyToOwnNotificationTopic() {
    var dispatcher = userRepository.findByUsername("dispatcher").orElseThrow();
    String token = tokenService.create(dispatcher);
    StompHeaderAccessor connect = accessor(StompCommand.CONNECT);
    connect.setNativeHeader("Authorization", "Bearer " + token);
    interceptor.preSend(message(connect), null);

    StompHeaderAccessor own = subscription(
      "/topic/notifications/" + dispatcher.getId(),
      connect
    );
    StompHeaderAccessor anotherUser = subscription("/topic/notifications/user_admin", connect);

    assertThatCode(() -> interceptor.preSend(message(own), null)).doesNotThrowAnyException();
    assertThatThrownBy(() -> interceptor.preSend(message(anotherUser), null))
      .isInstanceOf(AccessDeniedException.class);
  }

  private StompHeaderAccessor subscription(String destination, StompHeaderAccessor connect) {
    StompHeaderAccessor accessor = accessor(StompCommand.SUBSCRIBE);
    accessor.setDestination(destination);
    accessor.setUser(connect.getUser());
    return accessor;
  }

  private StompHeaderAccessor accessor(StompCommand command) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    accessor.setLeaveMutable(true);
    return accessor;
  }

  private Message<byte[]> message(StompHeaderAccessor accessor) {
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }
}
