package com.powerinspection.detection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.model.ModelProperties;
import com.powerinspection.route.RouteExecutorSupport;
import com.powerinspection.route.RouteRevisionEntity;
import com.powerinspection.route.RouteRevisionRepository;
import com.powerinspection.task.TaskExecutionEntity;
import com.powerinspection.task.TaskExecutionRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RobotInspectionImageService {
  static final Path ROOT = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("robot-inspection").normalize();

  private final RobotInspectionImageRepository repository;
  private final DataStoreService dataStore;
  private final TaskExecutionRepository executionRepository;
  private final RouteRevisionRepository revisionRepository;
  private final ObjectMapper objectMapper;
  private final ModelProperties modelProperties;
  private final long maxBytes;
  private final long maxPixels;

  public RobotInspectionImageService(
      RobotInspectionImageRepository repository,
      DataStoreService dataStore,
      TaskExecutionRepository executionRepository,
      RouteRevisionRepository revisionRepository,
      ObjectMapper objectMapper,
      ModelProperties modelProperties,
      @Value("${app.robot-inspection.max-image-bytes:20971520}") long maxBytes,
      @Value("${app.robot-inspection.max-image-pixels:40000000}") long maxPixels) {
    this.repository = repository;
    this.dataStore = dataStore;
    this.executionRepository = executionRepository;
    this.revisionRepository = revisionRepository;
    this.objectMapper = objectMapper;
    this.modelProperties = modelProperties;
    this.maxBytes = maxBytes;
    this.maxPixels = maxPixels;
  }

  @Transactional
  public RobotInspectionImageEntity importForAdministrator(
      String taskId, String robotId, String checkpointId, String capturedAt, String createdBy, MultipartFile image) throws IOException {
    ImageContext context = context(taskId, robotId, checkpointId, null, false);
    PreparedImage prepared = prepare(image, null);
    return store("ADMIN_IMPORT", null, context, capturedAt, createdBy, prepared);
  }

  @Transactional
  public UploadResult importFromBridge(String robotId, String executionId, String taskId, String checkpointId,
      String capturedAt, String expectedSha256, String idempotencyKey, MultipartFile image) throws IOException {
    String normalizedKey = required(idempotencyKey, "缺少 Idempotency-Key");
    if (normalizedKey.length() > 160) throw ApiException.badRequest("Idempotency-Key 长度超过限制");
    PreparedImage prepared = prepare(image, expectedSha256);
    RobotInspectionImageEntity existing = repository.findByRobotIdAndIdempotencyKey(robotId, normalizedKey).orElse(null);
    if (existing != null) {
      if (!existing.getSha256().equals(prepared.sha256())) {
        throw ApiException.conflict("相同 Idempotency-Key 已用于不同图片");
      }
      return new UploadResult(existing, false);
    }
    ImageContext context = context(taskId, robotId, checkpointId, executionId, true);
    return new UploadResult(store("ROBOT_BRIDGE", normalizedKey, context, capturedAt, robotId, prepared), true);
  }

  public Page<RobotInspectionImageEntity> search(int page, int size, String taskId, String checkpointId,
      String robotId, String capturedFrom, String capturedTo) {
    return repository.search(text(taskId), text(checkpointId), text(robotId), text(capturedFrom), text(capturedTo),
      PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "capturedAt")));
  }

  public RobotInspectionImageEntity get(String id) {
    return repository.findById(id).orElseThrow(() -> ApiException.notFound("机器人巡检图片不存在"));
  }

  public RobotInspectionImageEntity find(String id) {
    return repository.findById(id).orElse(null);
  }

  public RobotInspectionImageEntity requireAvailable(String id) {
    RobotInspectionImageEntity image = get(id);
    if (!"AVAILABLE".equals(image.getStatus()) || !StringUtils.hasText(image.getStorageKey())) {
      throw ApiException.conflict("机器人原始图片已按保留策略清理");
    }
    Path file = resolve(image.getStorageKey());
    if (!Files.isRegularFile(file)) throw ApiException.conflict("机器人原始图片文件不存在");
    return image;
  }

  public Map<String, Object> view(RobotInspectionImageEntity image, String publicModelFileBaseUrl) {
    Map<String, Object> task = dataStore.find(DataCategory.TASK, image.getTaskId());
    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, image.getRobotId());
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", image.getId());
    result.put("source", image.getSource());
    result.put("robotId", image.getRobotId());
    result.put("robotName", robot == null ? image.getRobotId() : robot.get("name"));
    result.put("taskId", image.getTaskId());
    result.put("taskName", task == null ? image.getTaskId() : task.get("name"));
    result.put("executionId", image.getExecutionId());
    result.put("routeId", image.getRouteId());
    result.put("routeRevisionId", image.getRouteRevisionId());
    result.put("checkpointId", image.getCheckpointId());
    result.put("checkpointName", image.getCheckpointName());
    result.put("capturedAt", image.getCapturedAt());
    result.put("contentType", image.getContentType());
    result.put("width", image.getWidth());
    result.put("height", image.getHeight());
    result.put("sizeBytes", image.getSizeBytes());
    result.put("sha256", image.getSha256());
    result.put("status", image.getStatus());
    result.put("originalAvailable", "AVAILABLE".equals(image.getStatus()) && image.getStorageKey() != null);
    result.put("imageUrl", image.getStorageKey() == null ? null : append(publicModelFileBaseUrl, image.getStorageKey()));
    result.put("createdAt", image.getCreatedAt());
    result.put("originalPurgedAt", image.getOriginalPurgedAt());
    return result;
  }

  public String publicImageUrl(RobotInspectionImageEntity image, String publicModelFileBaseUrl) {
    return append(publicModelFileBaseUrl, image.getStorageKey());
  }

  public String modelImageUrl(RobotInspectionImageEntity image) {
    String configured = modelProperties.getLocateAnything().getInputFileBaseUrl();
    if (!StringUtils.hasText(configured)) return "/model-files/" + image.getStorageKey();
    return append(configured, image.getStorageKey());
  }

  public DetectionContext detectionContext(RobotInspectionImageEntity image) {
    Map<String, Object> task = dataStore.find(DataCategory.TASK, image.getTaskId());
    Map<String, Object> route = dataStore.find(DataCategory.ROUTE, image.getRouteId());
    if (task == null || route == null) throw ApiException.conflict("机器人图片关联的任务或路线不存在");
    Map<String, Object> checkpoint = new LinkedHashMap<>();
    checkpoint.put("id", image.getCheckpointId());
    checkpoint.put("name", image.getCheckpointName());
    checkpoint.put("routeId", image.getRouteId());
    return new DetectionContext(task, route, checkpoint);
  }

  Path resolve(String storageKey) {
    if (!StringUtils.hasText(storageKey)) throw ApiException.conflict("图片文件不存在");
    Path path = ModelFileWebConfig.MODEL_FILE_ROOT.resolve(storageKey).normalize();
    if (!path.startsWith(ROOT)) throw ApiException.badRequest("图片存储路径非法");
    return path;
  }

  private RobotInspectionImageEntity store(String source, String idempotencyKey, ImageContext context,
      String capturedAt, String createdBy, PreparedImage prepared) throws IOException {
    String id = Ids.next("rimg");
    Instant captured = parseCapturedAt(capturedAt);
    LocalDate day = captured.atZone(ZoneOffset.UTC).toLocalDate();
    String storageKey = "robot-inspection/" + day.toString().replace("-", "") + "/" + id + prepared.extension();
    Path target = resolve(storageKey);
    Files.createDirectories(target.getParent());
    Path staging = target.resolveSibling("." + target.getFileName() + ".staging");
    Files.write(staging, prepared.bytes());
    try {
      try {
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(staging, target);
      }
      String now = Instant.now().toString();
      RobotInspectionImageEntity entity = new RobotInspectionImageEntity();
      entity.setId(id);
      entity.setSource(source);
      entity.setRobotId(context.robotId());
      entity.setTaskId(context.taskId());
      entity.setExecutionId(context.executionId());
      entity.setRouteId(context.routeId());
      entity.setRouteRevisionId(context.routeRevisionId());
      entity.setCheckpointId(context.checkpointId());
      entity.setCheckpointName(context.checkpointName());
      entity.setCapturedAt(captured.toString());
      entity.setContentType(prepared.contentType());
      entity.setExtension(prepared.extension());
      entity.setWidth(prepared.width());
      entity.setHeight(prepared.height());
      entity.setSizeBytes(prepared.bytes().length);
      entity.setSha256(prepared.sha256());
      entity.setStorageKey(storageKey);
      entity.setStatus("AVAILABLE");
      entity.setIdempotencyKey(idempotencyKey);
      entity.setCreatedBy(createdBy);
      entity.setCreatedAt(now);
      entity.setUpdatedAt(now);
      return repository.saveAndFlush(entity);
    } catch (IOException | RuntimeException ex) {
      Files.deleteIfExists(staging);
      Files.deleteIfExists(target);
      throw ex;
    }
  }

  private PreparedImage prepare(MultipartFile image, String expectedSha256) throws IOException {
    if (image == null || image.isEmpty()) throw ApiException.badRequest("请选择机器人巡检图片");
    if (image.getSize() > maxBytes) throw ApiException.badRequest("机器人巡检图片超过 20MB 限制");
    byte[] bytes = image.getBytes();
    ImageFormat format = ImageFormat.detect(bytes);
    if (format == null) throw ApiException.badRequest("机器人巡检图片格式无效，仅支持 JPG、PNG、WEBP、BMP");
    String sha256 = sha256(bytes);
    if (StringUtils.hasText(expectedSha256) && !sha256.equalsIgnoreCase(expectedSha256.trim())) {
      throw ApiException.badRequest("机器人巡检图片 SHA-256 与平台计算结果不一致");
    }
    Integer width = null;
    Integer height = null;
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
    if (decoded != null) {
      width = decoded.getWidth();
      height = decoded.getHeight();
      if ((long) width * height > maxPixels) throw ApiException.badRequest("机器人巡检图片像素数量超过限制");
    } else if (format != ImageFormat.WEBP) {
      throw ApiException.badRequest("机器人巡检图片内容无法解码");
    }
    return new PreparedImage(bytes, format.contentType, format.extension, width, height, sha256);
  }

  private ImageContext context(String taskId, String robotId, String checkpointId, String executionId, boolean requireExecution) {
    String normalizedTaskId = required(taskId, "请选择关联任务");
    String normalizedRobotId = required(robotId, "请选择机器人");
    String normalizedCheckpointId = required(checkpointId, "请选择检查点");
    Map<String, Object> task = dataStore.find(DataCategory.TASK, normalizedTaskId);
    if (task == null) throw ApiException.notFound("关联任务不存在");
    if (!Objects.equals(normalizedRobotId, text(task.get("robotId")))) throw ApiException.badRequest("任务与机器人不匹配");
    if (dataStore.find(DataCategory.ROBOT, normalizedRobotId) == null) throw ApiException.notFound("机器人不存在");
    String routeId = required(text(task.get("routeId")), "任务未关联路线");
    Map<String, Object> route = dataStore.find(DataCategory.ROUTE, routeId);
    if (route == null) throw ApiException.notFound("任务关联路线不存在");

    TaskExecutionEntity execution = executionRepository.findById(normalizedTaskId).orElse(null);
    if (requireExecution && execution == null) throw ApiException.badRequest("正式机器人图片必须关联执行实例");
    if (execution != null) {
      if (StringUtils.hasText(executionId) && !Objects.equals(executionId, execution.getExecutionId())) {
        throw ApiException.badRequest("executionId 与任务执行实例不匹配");
      }
      if (!Objects.equals(normalizedRobotId, execution.getRobotId())) {
        throw ApiException.badRequest("任务执行实例与机器人不匹配");
      }
      RouteRevisionEntity revision = revisionRepository.findById(execution.getRouteRevisionId())
        .orElseThrow(() -> ApiException.notFound("路线修订不存在"));
      Map<String, Object> executor;
      try {
        executor = objectMapper.readValue(revision.getExecutorJson(), new TypeReference<>() {});
      } catch (IOException ex) {
        throw ApiException.conflict("路线修订内容无法读取");
      }
      Map<String, Object> target = RouteExecutorSupport.orderedTargets(executor).stream()
        .filter(item -> normalizedCheckpointId.equals(text(item.get("id"))))
        .findFirst().orElseThrow(() -> ApiException.badRequest("检查点不属于任务路线修订"));
      return new ImageContext(normalizedRobotId, normalizedTaskId, execution.getExecutionId(), revision.getRouteId(),
        revision.getId(), normalizedCheckpointId, required(text(target.get("name")), "检查点名称缺失"));
    }

    Map<String, Object> checkpoint = RouteExecutorSupport.compatibleCheckpoints(route).stream()
      .filter(item -> normalizedCheckpointId.equals(text(item.get("id"))))
      .findFirst().orElseThrow(() -> ApiException.badRequest("检查点不属于任务路线"));
    return new ImageContext(normalizedRobotId, normalizedTaskId, null, routeId, null,
      normalizedCheckpointId, required(text(checkpoint.get("name")), "检查点名称缺失"));
  }

  private Instant parseCapturedAt(String value) {
    if (!StringUtils.hasText(value)) return Instant.now();
    try {
      return Instant.parse(value.trim());
    } catch (DateTimeParseException ex) {
      throw ApiException.badRequest("capturedAt 必须是带时区的 ISO-8601 时间");
    }
  }

  private static String append(String base, String path) {
    if (!StringUtils.hasText(base)) return "/model-files/" + path;
    return (base.endsWith("/") ? base : base + "/") + path.replace('\\', '/');
  }

  private static String required(String value, String message) {
    String normalized = text(value);
    if (normalized == null) throw ApiException.badRequest(message);
    return normalized;
  }

  private static String text(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString().trim();
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private enum ImageFormat {
    JPEG("image/jpeg", ".jpg"), PNG("image/png", ".png"), WEBP("image/webp", ".webp"), BMP("image/bmp", ".bmp");
    private final String contentType;
    private final String extension;
    ImageFormat(String contentType, String extension) { this.contentType = contentType; this.extension = extension; }
    static ImageFormat detect(byte[] bytes) {
      if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return JPEG;
      if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return PNG;
      if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
          && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return WEBP;
      if (bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M') return BMP;
      return null;
    }
  }

  private record PreparedImage(byte[] bytes, String contentType, String extension, Integer width, Integer height, String sha256) {}
  private record ImageContext(String robotId, String taskId, String executionId, String routeId, String routeRevisionId,
                              String checkpointId, String checkpointName) {}
  public record UploadResult(RobotInspectionImageEntity image, boolean created) {}
  public record DetectionContext(Map<String, Object> task, Map<String, Object> route, Map<String, Object> checkpoint) {}
}
