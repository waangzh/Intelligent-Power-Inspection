package com.powerinspection.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationWebSocketEndToEndTests {
  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;
  @Autowired ObjectMapper objectMapper;
  @Autowired NotificationService notificationService;

  private WebSocketStompClient stompClient;
  private StompSession session;

  @AfterEach
  void closeSession() {
    if (session != null && session.isConnected()) session.disconnect();
    if (stompClient != null) stompClient.stop();
  }

  @Test
  void notificationFlowsThroughDatabaseStompRestAndReadState() throws Exception {
    String token = login("dispatcher", "Disp@123");
    ArrayBlockingQueue<Map<?, ?>> messages = new ArrayBlockingQueue<>(1);
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin("http://localhost:5173");
    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("Authorization", "Bearer " + token);
    session = stompClient.connectAsync(
        "ws://127.0.0.1:" + port + "/ws",
        handshakeHeaders,
        connectHeaders,
        new StompSessionHandlerAdapter() {})
      .get(10, TimeUnit.SECONDS);
    session.subscribe("/topic/notifications/user_dispatcher", new StompFrameHandler() {
      @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
      @Override public void handleFrame(StompHeaders headers, Object payload) {
        messages.offer((Map<?, ?>) payload);
      }
    });

    Map<String, Object> saved = notificationService.pushEvent(
        "user_dispatcher", "SYSTEM", "SYSTEM_E2E", "SYSTEM", "notification-e2e",
        "通知闭环验证", "验证真实 STOMP 通知链路。", "/notifications",
        "notification-e2e:" + System.nanoTime());
    Map<?, ?> event = messages.poll(10, TimeUnit.SECONDS);

    assertThat(event).isNotNull();
    assertThat(event.get("resourceId")).isEqualTo(saved.get("id"));
    JsonNode unread = authorizedGet("/api/v1/notifications/" + saved.get("id"), token);
    assertThat(unread.path("data").path("eventCode").asText()).isEqualTo("SYSTEM_E2E");
    assertThat(unread.path("data").path("read").asBoolean()).isFalse();

    HttpHeaders headers = bearerHeaders(token);
    ResponseEntity<String> readResponse = rest.exchange(
        "/api/v1/notifications/" + saved.get("id") + "/read",
        HttpMethod.PATCH,
        new HttpEntity<Void>(null, headers),
        String.class);
    assertThat(readResponse.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(objectMapper.readTree(readResponse.getBody()).path("data").path("read").asBoolean()).isTrue();
    assertThat(authorizedGet("/api/v1/notifications/" + saved.get("id"), token)
        .path("data").path("read").asBoolean()).isTrue();
  }

  private String login(String username, String password) throws Exception {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<String> response = rest.postForEntity(
        "/api/v1/auth/login",
        new HttpEntity<>(
            Map.of("username", username, "password", password, "remember", false),
            headers),
        String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(response.getBody()).path("data").path("token").asText();
  }

  private JsonNode authorizedGet(String path, String token) throws Exception {
    ResponseEntity<String> response = rest.exchange(
        path, HttpMethod.GET, new HttpEntity<Void>(null, bearerHeaders(token)), String.class);
    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    return objectMapper.readTree(response.getBody());
  }

  private HttpHeaders bearerHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }
}
