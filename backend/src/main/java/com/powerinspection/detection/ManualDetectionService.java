package com.powerinspection.detection;

import com.powerinspection.common.ApiException;
import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.model.LocateAnythingRequest;
import com.powerinspection.model.LocateAnythingResult;
import com.powerinspection.model.ModelProperties;
import com.powerinspection.model.ModelServiceException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class ManualDetectionService {
  private static final String STATUS_RUNNING = "RUNNING";
  private static final String STATUS_SUCCEEDED = "SUCCEEDED";
  private static final String STATUS_FAILED = "FAILED";
  private static final int MAX_ANNOTATED_IMAGE_BYTES = 20 * 1024 * 1024;
  private static final Path RESULT_DIR = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("locate-anything").resolve("results");

  private final LocateAnythingGateway locateAnythingGateway;
  private final URI modelBaseUri;
  private final Duration modelTimeout;
  private final HttpClient httpClient;
  private final Map<String, ManualDetectionJob> jobs = new ConcurrentHashMap<>();
  private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "manual-detection-worker");
    thread.setDaemon(true);
    return thread;
  });

  public ManualDetectionService(LocateAnythingGateway locateAnythingGateway, ModelProperties modelProperties) {
    this.locateAnythingGateway = locateAnythingGateway;
    this.modelBaseUri = URI.create(modelProperties.getLocateAnything().getBaseUrl());
    this.modelTimeout = Duration.ofSeconds(modelProperties.getLocateAnything().getTimeoutSeconds());
    this.httpClient = HttpClient.newBuilder().connectTimeout(modelTimeout).build();
  }

  public ManualDetectionController.ManualDetectionResponse submit(
      String requestId,
      String publicInputImageUrl,
      String modelInputImageUrl,
      String publicResultBaseUrl,
      Integer imageWidth,
      Integer imageHeight,
      List<Map<String, Object>> detections) {
    ManualDetectionJob job = new ManualDetectionJob(requestId, STATUS_RUNNING, publicInputImageUrl, null, List.of(), List.of(), null, Instant.now().toString(), null, null);
    jobs.put(requestId, job);
    executor.submit(() -> runDetection(requestId, publicInputImageUrl, modelInputImageUrl, publicResultBaseUrl, imageWidth, imageHeight, detections));
    return job.toResponse();
  }

  public ManualDetectionController.ManualDetectionResponse get(String requestId) {
    ManualDetectionJob job = jobs.get(requestId);
    if (job == null) {
      throw ApiException.notFound("检测任务不存在");
    }
    return job.toResponse();
  }

  private void runDetection(
      String requestId,
      String publicInputImageUrl,
      String modelInputImageUrl,
      String publicResultBaseUrl,
      Integer imageWidth,
      Integer imageHeight,
      List<Map<String, Object>> detections) {
    ManualDetectionJob current = jobs.get(requestId);
    String startedAt = Instant.now().toString();
    if (current != null) {
      jobs.put(requestId, current.withTiming(startedAt, null));
    }
    try {
      Map<String, Object> task = map("id", requestId, "name", "Manual LocateAnything Detection", "createdAt", Instant.now().toString());
      Map<String, Object> route = map("id", "manual_route", "name", "Manual Detection");
      Map<String, Object> checkpoint = map("id", "manual_checkpoint", "name", "Manual Upload");
      LocateAnythingResult result = locateAnythingGateway.detectCheckpoint(
        new LocateAnythingRequest(task, route, checkpoint, modelInputImageUrl, imageWidth, imageHeight, detections)
      );
      String advertisedResultImageUrl = firstText(result.resultImageUrl(), result.findings().stream()
        .map(LocateAnythingFinding::imageUrl)
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElse(null));
      String resultImageUrl = rehostAnnotatedImage(requestId, publicResultBaseUrl, advertisedResultImageUrl);
      List<LocateAnythingFinding> findings = withResultImage(result.findings(), resultImageUrl);
      jobs.put(requestId, new ManualDetectionJob(
        requestId,
        STATUS_SUCCEEDED,
        publicInputImageUrl,
        resultImageUrl,
        findings,
        result.warnings(),
        null,
        current == null ? startedAt : current.createdAt(),
        startedAt,
        Instant.now().toString()
      ));
    } catch (Exception ex) {
      jobs.put(requestId, new ManualDetectionJob(
        requestId,
        STATUS_FAILED,
        publicInputImageUrl,
        null,
        List.of(),
        List.of(ex.getMessage() == null ? "模型检测失败" : ex.getMessage()),
        ex.getMessage() == null ? "模型检测失败" : ex.getMessage(),
        current == null ? startedAt : current.createdAt(),
        startedAt,
        Instant.now().toString()
      ));
    }
  }

  private String rehostAnnotatedImage(String requestId, String publicResultBaseUrl, String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      return null;
    }
    String filename = requestId + "_annotated" + extension(imageUrl);
    downloadAnnotatedImage(imageUrl, filename);
    return appendPath(publicResultBaseUrl, filename);
  }

  private List<LocateAnythingFinding> withResultImage(List<LocateAnythingFinding> findings, String resultImageUrl) {
    if (resultImageUrl == null || resultImageUrl.isBlank()) {
      return findings;
    }
    return findings.stream().map(finding -> {
      Map<String, Object> rawResult = new LinkedHashMap<>(finding.rawResult() == null ? Map.of() : finding.rawResult());
      rawResult.put("imageUrl", resultImageUrl);
      return new LocateAnythingFinding(
        finding.type(),
        finding.prompt(),
        finding.score(),
        finding.bbox(),
        finding.label(),
        resultImageUrl,
        rawResult
      );
    }).toList();
  }

  private String firstText(String preferred, String fallback) {
    return preferred == null || preferred.isBlank() ? fallback : preferred;
  }

  private void downloadAnnotatedImage(String imageUrl, String filename) {
    URI advertisedSource = URI.create(imageUrl);
    if (advertisedSource.getPath() == null || !advertisedSource.getPath().startsWith("/files/annotated/")) {
      throw new ModelServiceException("LocateAnything 标注图地址非法");
    }
    URI source = modelBaseUri.resolve(advertisedSource.getPath());
    try {
      HttpRequest request = HttpRequest.newBuilder(source).timeout(modelTimeout).GET().build();
      HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ModelServiceException("LocateAnything 标注图下载失败: HTTP " + response.statusCode());
      }
      String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
      if (!contentType.startsWith("image/")) {
        throw new ModelServiceException("LocateAnything 标注图响应类型非法");
      }
      if (response.body().length > MAX_ANNOTATED_IMAGE_BYTES) {
        throw new ModelServiceException("LocateAnything 标注图超过 20MB 限制");
      }
      Files.createDirectories(RESULT_DIR);
      Path output = RESULT_DIR.resolve(filename).normalize();
      if (!output.startsWith(RESULT_DIR)) {
        throw new ModelServiceException("LocateAnything 标注图文件名非法");
      }
      Files.write(output, response.body());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ModelServiceException("LocateAnything 标注图下载被中断", ex);
    } catch (IOException ex) {
      throw new ModelServiceException("LocateAnything 标注图下载失败", ex);
    }
  }

  private String extension(String imageUrl) {
    String path = URI.create(imageUrl).getPath().toLowerCase(Locale.ROOT);
    if (path.endsWith(".png")) {
      return ".png";
    }
    if (path.endsWith(".webp")) {
      return ".webp";
    }
    if (path.endsWith(".bmp")) {
      return ".bmp";
    }
    return ".jpg";
  }

  private String appendPath(String baseUrl, String filename) {
    return (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/") + filename;
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> item = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      item.put(values[i].toString(), values[i + 1]);
    }
    return item;
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  private record ManualDetectionJob(
    String requestId,
    String status,
    String inputImageUrl,
    String resultImageUrl,
    List<LocateAnythingFinding> findings,
    List<String> warnings,
    String errorMessage,
    String createdAt,
    String startedAt,
    String completedAt
  ) {
    ManualDetectionJob withTiming(String startedAt, String completedAt) {
      return new ManualDetectionJob(requestId, status, inputImageUrl, resultImageUrl, findings, warnings, errorMessage, createdAt, startedAt, completedAt);
    }

    ManualDetectionController.ManualDetectionResponse toResponse() {
      return new ManualDetectionController.ManualDetectionResponse(requestId, status, inputImageUrl, resultImageUrl, findings, warnings, errorMessage, createdAt, startedAt, completedAt);
    }
  }
}
