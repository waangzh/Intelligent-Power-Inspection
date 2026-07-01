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
        "findings": [
          {
            "type": "SWITCH",
            "prompt": "红色刀闸开关",
            "label": "abnormal",
            "score": null,
            "outputType": "box",
            "normalizedBox": [120, 80, 360, 260],
            "pixelBox": [48, 32, 144, 104],
            "imageUrl": "http://example.test/annotated.jpg",
            "rawAnswer": "<box><120><80><360><260></box>"
          }
        ],
        "warnings": []
      }
      """);
    });

    HttpLocateAnythingGateway gateway = new HttpLocateAnythingGateway(properties(baseUrl, baseUrl));
    List<LocateAnythingFinding> findings = gateway.detectCheckpoint(new LocateAnythingRequest(
      Map.of("id", "task_001"),
      Map.of("id", "route_001"),
      Map.of("id", "cp_001"),
      "http://example.test/input.jpg",
      List.of(Map.of("type", "SWITCH", "prompt", "红色刀闸开关", "enabled", true))
    ));

    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).type()).isEqualTo("SWITCH");
    assertThat(findings.get(0).bbox()).containsExactly(48, 32, 144, 104);
    assertThat(findings.get(0).imageUrl()).isEqualTo("http://example.test/annotated.jpg");
    assertThat(findings.get(0).rawResult()).containsEntry("rawAnswer", "<box><120><80><360><260></box>");
    assertThat(requestBody.get()).contains("\"generationMode\":\"fast\"");
  }

  @Test
  void httpLingBotMapGatewayMapsStatusAndArtifacts() {
    server.createContext("/v1/reconstruction/jobs", exchange -> writeJson(exchange, """
      {"jobId":"py_job_001","status":"QUEUED","progress":0}
      """));
    server.createContext("/v1/reconstruction/jobs/py_job_001", exchange -> writeJson(exchange, """
      {
        "jobId": "py_job_001",
        "status": "SUCCEEDED",
        "progress": 100,
        "mapId": "map_001",
        "frameCount": 1200,
        "pointCount": 120000,
        "artifacts": {"pointCloudUrl": "http://example.test/maps/map_001/cloud.ply"}
      }
      """));

    HttpLingBotMapGateway gateway = new HttpLingBotMapGateway(properties(baseUrl, baseUrl));
    Map<String, Object> created = gateway.createJob(Map.of("id", "lingbot_001", "siteId", "site_001"));
    Map<String, Object> completed = gateway.advanceJob(created);
    Map<String, Object> pointCloud = gateway.pointCloud(completed);

    assertThat(created.get("status")).isEqualTo("PENDING");
    assertThat(created.get("externalJobId")).isEqualTo("py_job_001");
    assertThat(completed.get("status")).isEqualTo("COMPLETED");
    assertThat(completed.get("frameCount")).isEqualTo(1200);
    assertThat(completed).doesNotContainKey("videoCount");
    assertThat(pointCloud.get("url")).isEqualTo("http://example.test/maps/map_001/cloud.ply");
  }

  private ModelProperties properties(String locateBaseUrl, String lingbotBaseUrl) {
    ModelProperties properties = new ModelProperties();
    properties.getLocateAnything().setBaseUrl(locateBaseUrl);
    properties.getLocateAnything().setTimeoutSeconds(5);
    properties.getLingbotMap().setBaseUrl(lingbotBaseUrl);
    properties.getLingbotMap().setTimeoutSeconds(5);
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
