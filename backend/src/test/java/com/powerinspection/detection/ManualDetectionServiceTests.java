package com.powerinspection.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.model.LocateAnythingRequest;
import com.powerinspection.model.LocateAnythingResult;
import com.powerinspection.model.ModelProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManualDetectionServiceTests {
  private static final byte[] ANNOTATED_IMAGE = new byte[] {1, 2, 3, 4};
  private static final String REQUEST_ID = "manual_rehost_test";

  private HttpServer server;
  private ManualDetectionService service;
  private Path resultFile;
  private AtomicInteger imageDownloads;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    imageDownloads = new AtomicInteger();
    server.createContext("/files/annotated/result.jpg", exchange -> {
      imageDownloads.incrementAndGet();
      exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
      exchange.sendResponseHeaders(200, ANNOTATED_IMAGE.length);
      exchange.getResponseBody().write(ANNOTATED_IMAGE);
      exchange.close();
    });
    server.start();
    resultFile = ModelFileWebConfig.MODEL_FILE_ROOT
      .resolve("locate-anything")
      .resolve("results")
      .resolve(REQUEST_ID + "_annotated.jpg");
    Files.deleteIfExists(resultFile);
  }

  @AfterEach
  void tearDown() throws Exception {
    if (service != null) {
      service.shutdown();
    }
    if (server != null) {
      server.stop(0);
    }
    Files.deleteIfExists(resultFile);
  }

  @Test
  void manualDetectionUsesModelInputUrlAndRehostsAnnotatedImage() throws Exception {
    String modelBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    AtomicReference<LocateAnythingRequest> capturedRequest = new AtomicReference<>();
    LocateAnythingGateway gateway = request -> {
      capturedRequest.set(request);
      return new LocateAnythingResult(List.of(new LocateAnythingFinding(
        "SWITCH",
        "红色刀闸开关",
        0.88,
        List.of(12, 20, 120, 160),
        "红色刀闸开关",
        "http://127.0.0.1:9001/files/annotated/result.jpg",
        Map.of("rawAnswer", "<box><100><100><500><500></box>")
      )), List.of("测试警告"), "http://127.0.0.1:9001/files/annotated/result.jpg");
    };
    ModelProperties properties = new ModelProperties();
    properties.getLocateAnything().setBaseUrl(modelBaseUrl);
    properties.getLocateAnything().setTimeoutSeconds(5);
    Map<String, DetectionRunEntity> runs = new ConcurrentHashMap<>();
    DetectionRunRepository repository = mock(DetectionRunRepository.class);
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
      DetectionRunEntity run = invocation.getArgument(0);
      runs.put(run.getId(), run);
      return run;
    });
    when(repository.save(any())).thenAnswer(invocation -> {
      DetectionRunEntity run = invocation.getArgument(0);
      runs.put(run.getId(), run);
      return run;
    });
    when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(runs.get(invocation.getArgument(0))));
    service = new ManualDetectionService(gateway, properties, repository, new ObjectMapper());

    service.submit(
      REQUEST_ID,
      "http://localhost:8080/model-files/locate-anything/uploads/input.jpg",
      "http://127.0.0.1:18080/model-files/locate-anything/uploads/input.jpg",
      "http://localhost:8080/model-files/locate-anything/results/",
      640,
      480,
      List.of(Map.of("type", "SWITCH", "displayLabel", "红色刀闸开关", "enabled", true))
    );

    ManualDetectionController.ManualDetectionResponse result = awaitResult();

    assertThat(result.status()).isEqualTo("SUCCEEDED");
    assertThat(result.inputImageUrl()).isEqualTo("http://localhost:8080/model-files/locate-anything/uploads/input.jpg");
    assertThat(result.resultImageUrl()).isEqualTo("http://localhost:8080/model-files/locate-anything/results/manual_rehost_test_annotated.jpg");
    assertThat(result.findings().get(0).imageUrl()).isEqualTo(result.resultImageUrl());
    assertThat(result.findings().get(0).label()).isEqualTo("红色刀闸开关");
    assertThat(result.warnings()).containsExactly("测试警告");
    assertThat(imageDownloads.get()).isEqualTo(1);
    assertThat(Files.readAllBytes(resultFile)).isEqualTo(ANNOTATED_IMAGE);
    assertThat(capturedRequest.get().imageUrl()).isEqualTo("http://127.0.0.1:18080/model-files/locate-anything/uploads/input.jpg");
    assertThat(capturedRequest.get().imageWidth()).isEqualTo(640);
    assertThat(capturedRequest.get().imageHeight()).isEqualTo(480);
  }

  private ManualDetectionController.ManualDetectionResponse awaitResult() throws Exception {
    for (int index = 0; index < 100; index += 1) {
      ManualDetectionController.ManualDetectionResponse result = service.get(REQUEST_ID);
      if (!"RUNNING".equals(result.status())) {
        return result;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("manual detection job did not finish");
  }
}
