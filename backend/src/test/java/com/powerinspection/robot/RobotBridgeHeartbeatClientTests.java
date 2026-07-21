package com.powerinspection.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RobotBridgeHeartbeatClientTests {
  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    server.createContext("/bridge/v1/robots/robot-001", exchange -> {
      byte[] body = ("{\"robotId\":\"robot-001\",\"lastSeen\":\"2026-07-15T06:00:00Z\","
        + "\"protocolVersion\":\"1.0\",\"bootId\":\"boot-1\",\"state\":\"idle\","
        + "\"softwareVersion\":\"build-1\",\"acceptedEventSequence\":0,"
        + "\"capabilities\":{\"remoteImmediateStart\":true,\"localConfirmStart\":true,\"localConfirmProtocolVersion\":\"1\"},"
        + "\"capabilityReportedAt\":\"2026-07-15T06:00:00Z\","
        + "\"health\":{\"systemMode\":\"ready\",\"lastError\":null,\"localConfirmStartReady\":true,\"localConfirmStartError\":null}}")
        .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void ignoresNullHealthValuesReturnedByBridge() {
    RobotProperties properties = new RobotProperties();
    properties.setBridgeAdminToken("test-token");
    properties.setHeartbeatBridgeBaseUrl(baseUrl);
    RobotBridgeHeartbeatClient client = new RobotBridgeHeartbeatClient(properties);

    BridgeRobotSnapshot snapshot = client.robot("robot-001");

    assertEquals("ready", snapshot.health().get("systemMode"));
    assertFalse(snapshot.health().containsKey("lastError"));
    assertEquals(true, snapshot.reportedSupportsLocalConfirmStart());
    assertEquals("1", snapshot.localConfirmProtocolVersion());
    assertEquals(true, snapshot.localConfirmStartReady());
  }
}
