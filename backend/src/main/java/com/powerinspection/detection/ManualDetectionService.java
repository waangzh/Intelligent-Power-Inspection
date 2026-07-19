package com.powerinspection.detection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class ManualDetectionService {
  private static final String STATUS_RUNNING = "RUNNING";
  private static final String STATUS_SUCCEEDED = "SUCCEEDED";
  private static final String STATUS_FAILED = "FAILED";
  private static final int MAX_ANNOTATED_IMAGE_BYTES = 20 * 1024 * 1024;
  private static final Path RESULT_DIR =
      ModelFileWebConfig.MODEL_FILE_ROOT.resolve("locate-anything").resolve("results");
  private static final TypeReference<List<LocateAnythingFinding>> FINDING_LIST =
      new TypeReference<>() {};
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private final LocateAnythingGateway locateAnythingGateway;
  private final DetectionRunRepository repository;
  private final ObjectMapper objectMapper;
  private final URI modelBaseUri;
  private final Duration modelTimeout;
  private final HttpClient httpClient;
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "manual-detection-worker");
            thread.setDaemon(true);
            return thread;
          });

  public ManualDetectionService(
      LocateAnythingGateway locateAnythingGateway,
      ModelProperties modelProperties,
      DetectionRunRepository repository,
      ObjectMapper objectMapper) {
    this.locateAnythingGateway = locateAnythingGateway;
    this.repository = repository;
    this.objectMapper = objectMapper;
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
    String createdAt = Instant.now().toString();
    DetectionRunEntity job = new DetectionRunEntity();
    job.setId(requestId);
    job.setSourceType("LOCAL_UPLOAD");
    job.setStatus(STATUS_RUNNING);
    job.setDetectionsJson(json(detections));
    job.setFindingsJson("[]");
    job.setWarningsJson("[]");
    job.setInputImageUrl(publicInputImageUrl);
    job.setCreatedAt(createdAt);
    job.setUpdatedAt(createdAt);
    job = repository.saveAndFlush(job);
    executor.submit(
        () ->
            runDetection(
                requestId,
                publicInputImageUrl,
                modelInputImageUrl,
                publicResultBaseUrl,
                imageWidth,
                imageHeight,
                detections));
    return toResponse(job);
  }

  public ManualDetectionController.ManualDetectionResponse get(String requestId) {
    return toResponse(
        repository.findById(requestId).orElseThrow(() -> ApiException.notFound("检测任务不存在")));
  }

  private void runDetection(
      String requestId,
      String publicInputImageUrl,
      String modelInputImageUrl,
      String publicResultBaseUrl,
      Integer imageWidth,
      Integer imageHeight,
      List<Map<String, Object>> detections) {
    DetectionRunEntity current = repository.findById(requestId).orElse(null);
    if (current == null) return;
    String startedAt = Instant.now().toString();
    current.setStartedAt(startedAt);
    current.setUpdatedAt(startedAt);
    current = repository.saveAndFlush(current);
    String finalStatus = STATUS_SUCCEEDED;
    try {
      Map<String, Object> task =
          map(
              "id",
              requestId,
              "name",
              "Manual LocateAnything Detection",
              "createdAt",
              Instant.now().toString());
      Map<String, Object> route = map("id", "manual_route", "name", "Manual Detection");
      Map<String, Object> checkpoint = map("id", "manual_checkpoint", "name", "Manual Upload");
      LocateAnythingResult result =
          locateAnythingGateway.detectCheckpoint(
              new LocateAnythingRequest(
                  task,
                  route,
                  checkpoint,
                  modelInputImageUrl,
                  imageWidth,
                  imageHeight,
                  detections));
      String advertisedResultImageUrl =
          firstText(
              result.resultImageUrl(),
              result.findings().stream()
                  .map(LocateAnythingFinding::imageUrl)
                  .filter(value -> value != null && !value.isBlank())
                  .findFirst()
                  .orElse(null));
      String resultImageUrl =
          rehostAnnotatedImage(requestId, publicResultBaseUrl, advertisedResultImageUrl);
      List<LocateAnythingFinding> findings = withResultImage(result.findings(), resultImageUrl);
      current.setResultImageUrl(resultImageUrl);
      current.setResultStorageKey(
          resultImageUrl == null
              ? null
              : "locate-anything/results/" + requestId + "_annotated" + extension(resultImageUrl));
      current.setFindingsJson(json(findings));
      current.setWarningsJson(json(result.warnings()));
      current.setErrorMessage(null);
    } catch (Exception ex) {
      String message = ex.getMessage() == null ? "模型检测失败" : ex.getMessage();
      finalStatus = STATUS_FAILED;
      current.setResultImageUrl(null);
      current.setFindingsJson("[]");
      current.setWarningsJson(json(List.of(message)));
      current.setErrorMessage(message);
    }
    String completedAt = Instant.now().toString();
    current.setCompletedAt(completedAt);
    current.setUpdatedAt(completedAt);
    current.setStatus(finalStatus);
    repository.save(current);
  }

  private String rehostAnnotatedImage(
      String requestId, String publicResultBaseUrl, String imageUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      return null;
    }
    String filename = requestId + "_annotated" + extension(imageUrl);
    downloadAnnotatedImage(imageUrl, filename);
    return appendPath(publicResultBaseUrl, filename);
  }

  private List<LocateAnythingFinding> withResultImage(
      List<LocateAnythingFinding> findings, String resultImageUrl) {
    if (resultImageUrl == null || resultImageUrl.isBlank()) {
      return findings;
    }
    return findings.stream()
        .map(
            finding -> {
              Map<String, Object> rawResult =
                  new LinkedHashMap<>(finding.rawResult() == null ? Map.of() : finding.rawResult());
              rawResult.put("imageUrl", resultImageUrl);
              return new LocateAnythingFinding(
                  finding.type(),
                  finding.prompt(),
                  finding.score(),
                  finding.bbox(),
                  finding.label(),
                  resultImageUrl,
                  rawResult);
            })
        .toList();
  }

  private String firstText(String preferred, String fallback) {
    return preferred == null || preferred.isBlank() ? fallback : preferred;
  }

  private void downloadAnnotatedImage(String imageUrl, String filename) {
    URI advertisedSource = URI.create(imageUrl);
    if (advertisedSource.getPath() == null
        || !advertisedSource.getPath().startsWith("/files/annotated/")) {
      throw new ModelServiceException("LocateAnything 标注图地址非法");
    }
    URI source = modelBaseUri.resolve(advertisedSource.getPath());
    try {
      HttpRequest request = HttpRequest.newBuilder(source).timeout(modelTimeout).GET().build();
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ModelServiceException("LocateAnything 标注图下载失败: HTTP " + response.statusCode());
      }
      String contentType =
          response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
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

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private <T> T read(String value, TypeReference<T> type, T fallback) {
    if (value == null || value.isBlank()) return fallback;
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException ex) {
      return fallback;
    }
  }

  private ManualDetectionController.ManualDetectionResponse toResponse(DetectionRunEntity job) {
    return new ManualDetectionController.ManualDetectionResponse(
        job.getId(),
        job.getStatus(),
        job.getInputImageUrl(),
        job.getResultImageUrl(),
        read(job.getFindingsJson(), FINDING_LIST, List.of()),
        read(job.getWarningsJson(), STRING_LIST, List.of()),
        job.getErrorMessage(),
        job.getCreatedAt(),
        job.getStartedAt(),
        job.getCompletedAt());
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }
}
