package com.powerinspection.detection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.model.DetectionItems;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.model.LocateAnythingRequest;
import com.powerinspection.model.LocateAnythingResult;
import com.powerinspection.model.ModelProperties;
import com.powerinspection.model.ModelServiceException;
import com.powerinspection.notification.NotificationService;
import com.powerinspection.robot.RobotInspectionImage;
import jakarta.annotation.PostConstruct;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DetectionRunService {
  private static final TypeReference<List<Map<String, Object>>> DETECTION_LIST = new TypeReference<>() {};
  private static final TypeReference<List<LocateAnythingFinding>> FINDING_LIST = new TypeReference<>() {};
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
  private static final Path RESULT_ROOT = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("detection-runs").normalize();
  private static final int MAX_RESULT_BYTES = 20 * 1024 * 1024;

  private final DetectionRunRepository repository;
  private final RobotInspectionImageService imageService;
  private final LocateAnythingGateway gateway;
  private final ObjectMapper objectMapper;
  private final DetectionAlarmService detectionAlarmService;
  private final URI modelBaseUri;
  private final Duration timeout;
  private final HttpClient httpClient;
  private final NotificationService notificationService;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "detection-run-worker");
    thread.setDaemon(true);
    return thread;
  });

  @org.springframework.beans.factory.annotation.Autowired
  public DetectionRunService(DetectionRunRepository repository, RobotInspectionImageService imageService,
      LocateAnythingGateway gateway, ObjectMapper objectMapper, ModelProperties properties,
      DetectionAlarmService detectionAlarmService, NotificationService notificationService) {
    this.repository = repository;
    this.imageService = imageService;
    this.gateway = gateway;
    this.objectMapper = objectMapper;
    this.detectionAlarmService = detectionAlarmService;
    this.modelBaseUri = URI.create(properties.getLocateAnything().getBaseUrl());
    this.timeout = Duration.ofSeconds(properties.getLocateAnything().getTimeoutSeconds());
    this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    this.notificationService = notificationService;
  }

  public DetectionRunService(DetectionRunRepository repository, RobotInspectionImageService imageService,
      LocateAnythingGateway gateway, ObjectMapper objectMapper, ModelProperties properties,
      DetectionAlarmService detectionAlarmService) {
    this(repository, imageService, gateway, objectMapper, properties, detectionAlarmService, null);
  }

  @PostConstruct
  void recoverInterruptedRuns() {
    String now = Instant.now().toString();
    for (DetectionRunEntity run : repository.findByStatus("RUNNING")) {
      run.setStatus("FAILED");
      run.setErrorMessage("后端重启，未完成的检测运行已终止，请重新提交");
      run.setCompletedAt(now);
      run.setUpdatedAt(now);
      repository.save(run);
      notifyFailure(run, "DETECTION_RUN_INTERRUPTED", "检测任务已中断", "检测运行 " + run.getId() + " 因后端重启被终止。", "recovered");
    }
  }

  public DetectionRunEntity submitRobotImage(String imageId, List<Map<String, Object>> rawDetections, String createdBy,
      String publicModelFileBaseUrl) {
    RobotInspectionImageEntity image = imageService.requireAvailable(imageId);
    List<Map<String, Object>> detections = normalizeDetections(rawDetections);
    if (detections.isEmpty()) throw ApiException.badRequest("请至少启用一个检测项");
    String now = Instant.now().toString();
    DetectionRunEntity run = new DetectionRunEntity();
    run.setId(Ids.next("det_run"));
    run.setSourceType("ROBOT_IMAGE");
    run.setImageId(image.getId());
    run.setTaskId(image.getTaskId());
    run.setCheckpointId(image.getCheckpointId());
    run.setStatus("RUNNING");
    run.setDetectionsJson(json(detections));
    run.setFindingsJson("[]");
    run.setWarningsJson("[]");
    run.setInputImageUrl(imageService.publicImageUrl(image, publicModelFileBaseUrl));
    run.setCreatedBy(createdBy);
    run.setCreatedAt(now);
    run.setUpdatedAt(now);
    DetectionRunEntity saved = repository.saveAndFlush(run);
    executor.submit(() -> execute(saved.getId()));
    return saved;
  }

  public DetectionRunEntity recordTaskResult(
      Map<String, Object> task,
      Map<String, Object> route,
      Map<String, Object> checkpoint,
      RobotInspectionImage image,
      List<Map<String, Object>> detections,
      LocateAnythingResult modelResult) {
    String now = Instant.now().toString();
    DetectionRunEntity run = new DetectionRunEntity();
    run.setId(Ids.next("det_run"));
    run.setSourceType("TASK_CHECKPOINT");
    run.setImageId(image == null ? null : image.id());
    run.setTaskId(text(task.get("id")));
    run.setCheckpointId(text(checkpoint.get("id")));
    run.setStatus("SUCCEEDED");
    run.setDetectionsJson(json(detections == null ? List.of() : detections));
    List<LocateAnythingFinding> findings = modelResult == null || modelResult.findings() == null
        ? List.of() : modelResult.findings();
    List<String> warnings = modelResult == null || modelResult.warnings() == null
        ? List.of() : modelResult.warnings();
    run.setFindingsJson(json(findings));
    run.setWarningsJson(json(warnings));
    run.setInputImageUrl(image == null ? null : image.url());
    run.setResultImageUrl(modelResult == null ? null : modelResult.resultImageUrl());
    run.setCreatedBy(text(task.get("robotId")));
    run.setCreatedAt(now);
    run.setStartedAt(now);
    run.setCompletedAt(now);
    run.setUpdatedAt(now);
    DetectionRunEntity saved = repository.saveAndFlush(run);

    Map<String, Object> context = new LinkedHashMap<>();
    copy(task, context, "siteId", "robotId", "taskId");
    context.put("taskId", run.getTaskId());
    context.put("routeId", text(route.get("id")));
    context.put("routeName", route.get("name"));
    context.put("checkpointId", run.getCheckpointId());
    context.put("checkpointName", checkpoint.get("name"));
    context.put("imageId", run.getImageId());
    try {
      detectionAlarmService.createAlarms(run.getId(), "DETECTION_RUN", context,
          detections == null ? List.of() : detections, findings);
    } catch (RuntimeException ignored) {
      // 告警链路失败不改变已完成的模型检测状态。
    }
    return saved;
  }

  public DetectionRunEntity get(String id) {
    return repository.findById(id).orElseThrow(() -> ApiException.notFound("检测运行不存在"));
  }

  public Page<DetectionRunEntity> search(int page, int size, String taskId, String imageId, String status) {
    return repository.search(text(taskId), text(imageId), text(status),
      PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt")));
  }

  public Map<String, Object> view(DetectionRunEntity run, String publicModelFileBaseUrl) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("requestId", run.getId());
    result.put("runId", run.getId());
    result.put("sourceType", run.getSourceType());
    result.put("imageId", run.getImageId());
    result.put("taskId", run.getTaskId());
    result.put("checkpointId", run.getCheckpointId());
    result.put("status", run.getStatus());
    result.put("detections", read(run.getDetectionsJson(), DETECTION_LIST, List.of()));
    boolean originalAvailable = true;
    if (StringUtils.hasText(run.getImageId())) {
      RobotInspectionImageEntity image = imageService.find(run.getImageId());
      originalAvailable = image != null && "AVAILABLE".equals(image.getStatus()) && StringUtils.hasText(image.getStorageKey());
    }
    result.put("originalAvailable", originalAvailable);
    result.put("inputImageUrl", originalAvailable ? run.getInputImageUrl() : null);
    result.put("resultImageUrl", publicImageUrl(run.getResultImageUrl(), publicModelFileBaseUrl));
    result.put("findings", read(run.getFindingsJson(), FINDING_LIST, List.of()));
    result.put("warnings", read(run.getWarningsJson(), STRING_LIST, List.of()));
    result.put("errorMessage", run.getErrorMessage());
    result.put("createdAt", run.getCreatedAt());
    result.put("startedAt", run.getStartedAt());
    result.put("completedAt", run.getCompletedAt());
    result.put("alarmCount", detectionAlarmService.countForRun(run.getId()));
    return result;
  }

  private void execute(String runId) {
    DetectionRunEntity run = repository.findById(runId).orElse(null);
    if (run == null) return;
    String startedAt = Instant.now().toString();
    run.setStartedAt(startedAt);
    run.setUpdatedAt(startedAt);
    run = repository.saveAndFlush(run);
    try {
      RobotInspectionImageEntity image = imageService.requireAvailable(run.getImageId());
      RobotInspectionImageService.DetectionContext context = imageService.detectionContext(image);
      List<Map<String, Object>> detections = read(run.getDetectionsJson(), DETECTION_LIST, List.of());
      LocateAnythingResult modelResult = gateway.detectCheckpoint(new LocateAnythingRequest(
        context.task(), context.route(), context.checkpoint(), imageService.modelImageUrl(image),
        image.getWidth(), image.getHeight(), detections));
      String advertised = firstText(modelResult.resultImageUrl(), modelResult.findings().stream()
        .map(LocateAnythingFinding::imageUrl).filter(StringUtils::hasText).findFirst().orElse(null));
      StoredResult stored = storeAnnotatedImage(runId, advertised);
      List<LocateAnythingFinding> findings = withResultImage(modelResult.findings(), stored == null ? null : stored.publicUrl());
      run.setStatus("SUCCEEDED");
      run.setFindingsJson(json(findings));
      run.setWarningsJson(json(modelResult.warnings()));
      run.setResultImageUrl(stored == null ? null : stored.publicUrl());
      run.setResultStorageKey(stored == null ? null : stored.storageKey());
      run.setErrorMessage(null);
      createAlarms(run, image, context, detections, findings);
    } catch (Exception ex) {
      run.setStatus("FAILED");
      run.setErrorMessage(safeMessage(ex));
      run.setWarningsJson(json(List.of(safeMessage(ex))));
    }
    String completedAt = Instant.now().toString();
    run.setCompletedAt(completedAt);
    run.setUpdatedAt(completedAt);
    repository.save(run);
    if ("FAILED".equals(run.getStatus())) {
      notifyFailure(run, "DETECTION_RUN_FAILED", "检测任务失败", "检测运行 " + run.getId() + " 执行失败：" + run.getErrorMessage(), "failed");
    }
  }

  private void notifyFailure(DetectionRunEntity run, String eventCode, String title, String content, String suffix) {
    if (notificationService == null) return;
    String recipient = StringUtils.hasText(run.getCreatedBy()) ? run.getCreatedBy() : "*";
    notificationService.pushEvent(recipient, "SYSTEM", eventCode, "DETECTION_RUN", run.getId(), title, content,
        "/detections/runs/" + run.getId(), "detection-run:" + run.getId() + ":" + eventCode + ":" + suffix);
  }

  private void createAlarms(
      DetectionRunEntity run,
      RobotInspectionImageEntity image,
      RobotInspectionImageService.DetectionContext context,
      List<Map<String, Object>> detections,
      List<LocateAnythingFinding> findings) {
    Map<String, Object> alarmContext = new LinkedHashMap<>();
    copy(context.task(), alarmContext, "siteId", "robotId", "taskId");
    alarmContext.put("taskId", run.getTaskId());
    alarmContext.put("routeId", image.getRouteId());
    alarmContext.put("routeName", context.route().get("name"));
    alarmContext.put("robotId", image.getRobotId());
    alarmContext.put("checkpointId", run.getCheckpointId());
    alarmContext.put("checkpointName", image.getCheckpointName());
    alarmContext.put("imageId", run.getImageId());
    try {
      detectionAlarmService.createAlarms(run.getId(), "DETECTION_RUN", alarmContext, detections, findings);
    } catch (RuntimeException ignored) {
      // 告警链路失败不改变已完成的模型检测状态。
    }
  }

  private void copy(Map<String, Object> source, Map<String, Object> target, String... keys) {
    if (source == null) return;
    for (String key : keys) if (source.get(key) != null) target.put(key, source.get(key));
  }

  private StoredResult storeAnnotatedImage(String runId, String imageUrl) {
    if (!StringUtils.hasText(imageUrl)) return null;
    URI advertised = URI.create(imageUrl);
    if (advertised.getPath() == null || !advertised.getPath().startsWith("/files/annotated/")) {
      throw new ModelServiceException("LocateAnything 标注图地址非法");
    }
    URI source = modelBaseUri.resolve(advertised.getPath());
    try {
      HttpResponse<byte[]> response = httpClient.send(HttpRequest.newBuilder(source).timeout(timeout).GET().build(),
        HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() < 200 || response.statusCode() >= 300) throw new ModelServiceException("LocateAnything 标注图下载失败");
      String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
      if (!contentType.startsWith("image/") || response.body().length > MAX_RESULT_BYTES) {
        throw new ModelServiceException("LocateAnything 标注图响应非法");
      }
      String extension = extension(advertised.getPath());
      String storageKey = "detection-runs/" + runId + "_annotated" + extension;
      Path target = ModelFileWebConfig.MODEL_FILE_ROOT.resolve(storageKey).normalize();
      if (!target.startsWith(RESULT_ROOT)) throw new ModelServiceException("LocateAnything 标注图路径非法");
      Files.createDirectories(target.getParent());
      Files.write(target, response.body());
      return new StoredResult(storageKey, "/model-files/" + storageKey);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ModelServiceException("LocateAnything 标注图下载被中断", ex);
    } catch (IOException ex) {
      throw new ModelServiceException("LocateAnything 标注图下载失败", ex);
    }
  }

  private List<LocateAnythingFinding> withResultImage(List<LocateAnythingFinding> findings, String resultImageUrl) {
    if (!StringUtils.hasText(resultImageUrl)) return findings;
    return findings.stream().map(finding -> new LocateAnythingFinding(
      finding.itemId(),
      finding.type(), finding.prompt(), finding.score(), finding.bbox(), finding.label(), resultImageUrl, finding.rawResult())).toList();
  }

  private List<Map<String, Object>> normalizeDetections(List<Map<String, Object>> raw) {
    if (raw == null) return List.of();
    return DetectionItems.enabled(raw).stream().map(item -> {
      String type = text(item.get("type"));
      String prompt = text(item.get("prompt"));
      if (type == null || prompt == null) throw ApiException.badRequest("已启用检测项必须填写类型和提示词");
      Map<String, Object> normalized = new LinkedHashMap<>(item);
      normalized.put("type", type);
      normalized.put("prompt", prompt);
      String itemId = text(item.get("itemId"));
      String name = text(item.get("name"));
      normalized.put("itemId", itemId == null ? type : itemId);
      normalized.put("name", name == null ? type : name);
      String displayLabel = text(item.get("displayLabel"));
      normalized.put("displayLabel", displayLabel == null || displayLabel.isBlank()
        ? DetectionItems.displayLabel(type)
        : displayLabel.trim());
      normalized.put("enabled", true);
      normalized.putIfAbsent("threshold", 0.75);
      DetectionRiskRules.normalize(item, normalized);
      return normalized;
    }).toList();
  }

  private String json(Object value) {
    try { return objectMapper.writeValueAsString(value); }
    catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
  }

  private <T> T read(String json, TypeReference<T> type, T fallback) {
    if (!StringUtils.hasText(json)) return fallback;
    try { return objectMapper.readValue(json, type); }
    catch (JsonProcessingException ex) { return fallback; }
  }

  private String firstText(String preferred, String fallback) { return StringUtils.hasText(preferred) ? preferred : fallback; }
  private String publicImageUrl(String imageUrl, String publicModelFileBaseUrl) {
    if (!StringUtils.hasText(imageUrl) || !imageUrl.startsWith("/model-files/")) return imageUrl;
    return (publicModelFileBaseUrl.endsWith("/") ? publicModelFileBaseUrl : publicModelFileBaseUrl + "/")
      + imageUrl.substring("/model-files/".length());
  }
  private String extension(String path) {
    String lower = path.toLowerCase(Locale.ROOT);
    if (lower.endsWith(".png")) return ".png";
    if (lower.endsWith(".webp")) return ".webp";
    if (lower.endsWith(".bmp")) return ".bmp";
    return ".jpg";
  }
  private String safeMessage(Exception ex) {
    String message = ex.getMessage();
    if (!StringUtils.hasText(message)) message = "模型检测失败";
    return message.substring(0, Math.min(1000, message.length())).replaceAll("[\r\n]+", " ");
  }
  private static String text(Object value) { return value == null || value.toString().isBlank() ? null : value.toString().trim(); }
  private record StoredResult(String storageKey, String publicUrl) {}

  @PreDestroy
  void shutdown() { executor.shutdownNow(); }
}
