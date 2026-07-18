package com.powerinspection.config;

import com.powerinspection.security.WebSocketAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
  private final CorsProperties corsProperties;
  private final WebSocketAuthorizationInterceptor authorizationInterceptor;

  public WebSocketConfig(
      CorsProperties corsProperties,
      WebSocketAuthorizationInterceptor authorizationInterceptor) {
    this.corsProperties = corsProperties;
    this.authorizationInterceptor = authorizationInterceptor;
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws").setAllowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new));
  }

  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(authorizationInterceptor);
  }
}
