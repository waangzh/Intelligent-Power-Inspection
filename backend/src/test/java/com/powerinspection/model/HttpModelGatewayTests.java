package com.powerinspection.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpModelGatewayTests {
  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void httpLocateAnythingGatewayMapsFindings() {
    AtomicReference<String> requestBody = new AtomicReference<>();
    server.createContext("/v1/locate/checkpoint", exchange -> {
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      writeJson(exchange, """
      {
        "provider": "locate-anything",
        "modelVersion": "mock",
        "status": "SUCCEEDED",
        "resultImageUrl": "http://example.test/combined.jpg",
        "findings": [
          {
            "itemId": "switch_main",
            "type": "SWITCH",
            "prompt": "红色刀闸开关",
            "label": "红色刀闸开关",
            "score": null,
            "outputType": "box",
            "normalizedBox": [120, 80, 360, 260],
            "pixelBox": [48, 32, 144, 104],
            "imageUrl": "http://example.test/combined.jpg",
            "rawAnswer": "<box><120><80><360><260></box>"
          }
        ],
        "warnings": ["Unparsed secondary output"]
      }
      """);
    });

    HttpLocateAnythingGateway gateway = new HttpLocateAnythingGateway(properties(baseUrl));
    LocateAnythingResult result = gateway.detectCheckpoint(new LocateAnythingRequest(
      Map.of("id", "task_001"),
      Map.of("id", "route_001"),
      Map.of("id", "cp_001"),
      "http://example.test/input.jpg",
      400,
      200,
      List.of(Map.of("itemId", "switch_main", "type", "SWITCH", "displayLabel", "红色刀闸开关", "prompt", "定位红色刀闸开关", "enabled", true))
    ));

    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().get(0).itemId()).isEqualTo("switch_main");
    assertThat(result.findings().get(0).type()).isEqualTo("SWITCH");
    assertThat(result.findings().get(0).bbox()).containsExactly(48, 32, 144, 104);
    assertThat(result.findings().get(0).label()).isEqualTo("红色刀闸开关");
    assertThat(result.findings().get(0).imageUrl()).isEqualTo("http://example.test/combined.jpg");
    assertThat(result.resultImageUrl()).isEqualTo("http://example.test/combined.jpg");
    assertThat(result.findings().get(0).rawResult()).containsEntry("rawAnswer", "<box><120><80><360><260></box>");
    assertThat(result.warnings()).containsExactly("Unparsed secondary output");
    assertThat(requestBody.get()).contains("\"generationMode\":\"fast\"");
    assertThat(requestBody.get()).contains("\"imageWidth\":400");
    assertThat(requestBody.get()).contains("\"imageHeight\":200");
    assertThat(requestBody.get()).contains("\"itemId\":\"switch_main\"");
    assertThat(requestBody.get()).contains("\"displayLabel\":\"红色刀闸开关\"");
  }

  private ModelProperties properties(String locateBaseUrl) {
    ModelProperties properties = new ModelProperties();
    properties.getLocateAnything().setBaseUrl(locateBaseUrl);
    properties.getLocateAnything().setTimeoutSeconds(5);
    return properties;
  }

  private void writeJson(HttpExchange exchange, String json) throws IOException {
    byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
