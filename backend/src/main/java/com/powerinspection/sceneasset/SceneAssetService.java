package com.powerinspection.sceneasset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.common.ResourceChangeEvent;
import com.powerinspection.config.ModelFileWebConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SceneAssetService {
  private static final Logger log = LoggerFactory.getLogger(SceneAssetService.class);
  private static final Path ROOT = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("scene-assets").normalize();
  private static final String MODEL_FILE = "original.ply";
  private static final String METADATA_FILE = "metadata.json";
  private static final String PREVIEW_FILE = "preview.ply";
  private static final int MAX_PLY_HEADER_BYTES = 1024 * 1024;

  private final SceneAssetRepository assetRepository;
  private final RobotSceneUploadRepository uploadRepository;
  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;
  private final long maxModelBytes;
  private final long maxMetadataBytes;
  private final Duration processingTimeout;
  private final Duration stagingTimeout;
  private final ConcurrentMap<String, Object> uploadLocks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Object> reviewLocks = new ConcurrentHashMap<>();

  public SceneAssetService(SceneAssetRepository assetRepository, RobotSceneUploadRepository uploadRepository,
      ObjectMapper objectMapper, SimpMessagingTemplate messagingTemplate,
      @Value("${app.scene-assets.max-model-bytes:1073741824}") long maxModelBytes,
      @Value("${app.scene-assets.max-metadata-bytes:1048576}") long maxMetadataBytes,
      @Value("${app.scene-assets.processing-timeout-seconds:1800}") long processingTimeoutSeconds,
      @Value("${app.scene-assets.staging-timeout-seconds:7200}") long stagingTimeoutSeconds) {
    this.assetRepository = assetRepository;
    this.uploadRepository = uploadRepository;
    this.objectMapper = objectMapper;
    this.messagingTemplate = messagingTemplate;
    this.maxModelBytes = Math.max(1, maxModelBytes);
    this.maxMetadataBytes = Math.max(1, maxMetadataBytes);
    this.processingTimeout = Duration.ofSeconds(Math.max(1, processingTimeoutSeconds));
    this.stagingTimeout = Duration.ofSeconds(Math.max(60, stagingTimeoutSeconds));
  }

  public RobotSceneUploadResult createForRobot(String siteId, String robotId, String bridgeRobotId,
      String idempotencyKey, MultipartFile model, MultipartFile metadata, String expectedModelSha256,
      String assetKind, String format, String sourceSessionId, String capturedAt,
      String reconstructedAt, String coordinateSystem, String unit, Long reportedPointCount) throws IOException {
    String key = required(idempotencyKey, "缺少 Idempotency-Key", 160);
    String normalizedRobotId = required(robotId, "机器人 ID 无效", 100);
    PreparedScene prepared = prepare(model, metadata, expectedModelSha256, assetKind, format,
      sourceSessionId, capturedAt, reconstructedAt, coordinateSystem, unit, reportedPointCount);
    String lockKey = normalizedRobotId + '\n' + key;
    Object lock = uploadLocks.computeIfAbsent(lockKey, ignored -> new Object());
    try {
      synchronized (lock) {
        RobotSceneUploadEntity upload = uploadRepository.findByRobotIdAndIdempotencyKey(normalizedRobotId, key)
          .orElse(null);
        if (upload != null) {
          RobotSceneUploadResult replay = claimExisting(upload, prepared.modelSha256());
          if (replay != null) return replay;
          upload = uploadRepository.findById(upload.id).orElseThrow();
        } else {
          upload = new RobotSceneUploadEntity();
          String now = Instant.now().toString();
          upload.robotId = normalizedRobotId;
          upload.idempotencyKey = key;
          upload.modelSha256 = prepared.modelSha256();
          upload.status = "PROCESSING";
          upload.createdAt = now;
          upload.updatedAt = now;
          try {
            upload = uploadRepository.saveAndFlush(upload);
          } catch (DataIntegrityViolationException ex) {
            upload = uploadRepository.findByRobotIdAndIdempotencyKey(normalizedRobotId, key).orElseThrow(() -> ex);
            RobotSceneUploadResult replay = claimExisting(upload, prepared.modelSha256());
            if (replay != null) return replay;
            upload = uploadRepository.findById(upload.id).orElseThrow();
          }
        }

        SceneAssetEntity asset = null;
        try {
          asset = storeAsset(siteId, normalizedRobotId, bridgeRobotId, model, prepared);
          upload.sceneAssetId = asset.id;
          upload.status = "SUCCEEDED";
          upload.updatedAt = Instant.now().toString();
          uploadRepository.saveAndFlush(upload);
          publish(ResourceChangeEvent.created("sceneAsset", asset.id));
          log.info("Robot scene upload succeeded robotId={} key={} assetId={} bytes={} points={} sha={}",
            normalizedRobotId, fingerprint(key), asset.id, asset.fileSize, asset.pointCount, shortHash(asset.modelSha256));
          return new RobotSceneUploadResult(toView(asset), true);
        } catch (IOException | RuntimeException ex) {
          if (asset != null) {
            assetRepository.deleteById(asset.id);
            deleteDirectoryQuietly(assetDirectory(asset.id));
          }
          upload.sceneAssetId = null;
          upload.status = "FAILED";
          upload.updatedAt = Instant.now().toString();
          try { uploadRepository.saveAndFlush(upload); } catch (RuntimeException failure) { ex.addSuppressed(failure); }
          throw ex;
        }
      }
    } finally {
      uploadLocks.remove(lockKey, lock);
    }
  }

  public List<Map<String, Object>> list(String source, String status, String siteId, String robotId,
      String assetKind) {
    return assetRepository.search(normalize(source), normalize(status), normalize(siteId),
      normalize(robotId), normalize(assetKind)).stream().map(this::toView).toList();
  }

  public Map<String, Object> get(String id) {
    return toView(requireAsset(id));
  }

  public Map<String, Object> review(String id, SceneAssetReviewRequest request, String reviewerId) {
    String action = required(request == null ? null : request.action(), "审核动作不能为空", 16)
      .toUpperCase(Locale.ROOT);
    if (!List.of("APPROVE", "REJECT").contains(action)) {
      throw ApiException.badRequest("审核动作只能是 APPROVE 或 REJECT");
    }
    String comment = normalize(request == null ? null : request.comment());
    if ("REJECT".equals(action) && comment == null) throw ApiException.badRequest("驳回时必须填写审核意见");
    if (comment != null && comment.length() > 1000) throw ApiException.badRequest("审核意见不能超过 1000 个字符");
    Object lock = reviewLocks.computeIfAbsent(id, ignored -> new Object());
    try {
      synchronized (lock) {
        SceneAssetEntity asset = requireAsset(id);
        if (!"PENDING_REVIEW".equals(asset.status)) throw ApiException.conflict("三维资产已经完成审核，不能重复审核");
        if ("APPROVE".equals(action)) verifyStoredFiles(asset);
        String now = Instant.now().toString();
        asset.status = "APPROVE".equals(action) ? "AVAILABLE" : "REJECTED";
        asset.reviewedBy = reviewerId;
        asset.reviewedAt = now;
        asset.reviewComment = comment;
        asset.updatedAt = now;
        if ("REJECT".equals(action)) {
          deleteDirectoryQuietly(assetDirectory(id));
          asset.filesReady = false;
          asset.storageKey = null;
          asset.previewStorageKey = null;
        }
        asset = assetRepository.saveAndFlush(asset);
        publish(ResourceChangeEvent.updated("sceneAsset", id));
        return toView(asset);
      }
    } finally {
      reviewLocks.remove(id, lock);
    }
  }

  public Path modelPath(String id) {
    SceneAssetEntity asset = requireReadableAsset(id);
    return regularFile(assetDirectory(id).resolve(MODEL_FILE));
  }

  public Path previewPath(String id) {
    SceneAssetEntity asset = requireReadableAsset(id);
    Path preview = assetDirectory(id).resolve(PREVIEW_FILE).normalize();
    return Files.isRegularFile(preview) ? regularFile(preview) : modelPath(asset.id);
  }

  public Path metadataPath(String id) {
    requireReadableAsset(id);
    return regularFile(assetDirectory(id).resolve(METADATA_FILE));
  }

  public void delete(String id) {
    SceneAssetEntity asset = requireAsset(id);
    deleteDirectoryQuietly(assetDirectory(asset.id));
    uploadRepository.deleteBySceneAssetId(asset.id);
    assetRepository.delete(asset);
    publish(ResourceChangeEvent.deleted("sceneAsset", id));
  }

  @Scheduled(cron = "${app.scene-assets.staging-cleanup-cron:0 45 3 * * *}")
  public void cleanupStagingDirectories() {
    if (!Files.isDirectory(ROOT)) return;
    Instant cutoff = Instant.now().minus(stagingTimeout);
    try (var paths = Files.list(ROOT)) {
      paths.filter(Files::isDirectory)
        .filter(path -> path.getFileName().toString().startsWith("."))
        .filter(path -> lastModified(path).isBefore(cutoff))
        .forEach(this::deleteDirectoryQuietly);
    } catch (IOException ex) {
      log.warn("Scene staging cleanup failed", ex);
    }
  }

  private PreparedScene prepare(MultipartFile model, MultipartFile metadata, String expectedSha,
      String assetKind, String format, String sourceSessionId, String capturedAt,
      String reconstructedAt, String coordinateSystem, String unit, Long reportedPointCount) throws IOException {
    validateFile(model, maxModelBytes, ".ply", "PLY");
    validateFile(metadata, maxMetadataBytes, ".json", "metadata.json");
    if (!"POINT_CLOUD".equals(required(assetKind, "assetKind 不能为空", 32).toUpperCase(Locale.ROOT))) {
      throw ApiException.badRequest("首期仅支持 POINT_CLOUD");
    }
    if (!"PLY".equals(required(format, "format 不能为空", 16).toUpperCase(Locale.ROOT))) {
      throw ApiException.badRequest("首期仅支持 PLY");
    }
    String normalizedCoordinate = required(coordinateSystem, "coordinateSystem 不能为空", 64).toUpperCase(Locale.ROOT);
    if (!"RIGHT_HANDED_Z_UP".equals(normalizedCoordinate)) throw ApiException.badRequest("不支持的坐标系");
    String normalizedUnit = required(unit, "unit 不能为空", 32).toUpperCase(Locale.ROOT);
    if (!"METER".equals(normalizedUnit)) throw ApiException.badRequest("不支持的长度单位");
    String sessionId = required(sourceSessionId, "sourceSessionId 不能为空", 160);
    String normalizedReconstructedAt = requiredInstant(reconstructedAt, "reconstructedAt");
    String normalizedCapturedAt = optionalInstant(capturedAt, "capturedAt");
    String expected = required(expectedSha, "modelSha256 不能为空", 64).toLowerCase(Locale.ROOT);
    if (!expected.matches("[0-9a-f]{64}")) throw ApiException.badRequest("modelSha256 必须是 SHA-256");
    if (reportedPointCount != null && reportedPointCount < 0) throw ApiException.badRequest("pointCount 不能为负数");

    byte[] rawMetadata = metadata.getBytes();
    JsonNode metadataNode;
    try {
      metadataNode = objectMapper.readTree(rawMetadata);
    } catch (RuntimeException ex) {
      throw ApiException.badRequest("metadata 必须是合法 JSON 对象");
    }
    if (metadataNode == null || !metadataNode.isObject()) throw ApiException.badRequest("metadata 必须是 JSON 对象");
    PlyHeader header = parsePlyHeader(model);
    String actualSha;
    try (InputStream input = model.getInputStream()) { actualSha = digest(input); }
    if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actualSha.getBytes(StandardCharsets.US_ASCII))) {
      throw ApiException.conflict("modelSha256 与平台计算结果不一致");
    }
    byte[] normalizedMetadata = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(metadataNode);
    String transform = validateTransform(metadataNode.get("sceneToReferenceTransform"));
    return new PreparedScene(safeName(model.getOriginalFilename(), ".ply"), model.getContentType(), model.getSize(),
      actualSha, sha256(normalizedMetadata), normalizedMetadata, sessionId,
      text(metadataNode, "sourceCaptureSessionId", 160), text(metadataNode, "reconstructProfile", 160),
      normalizedCoordinate, normalizedUnit, header.vertexCount(), reportedPointCount,
      normalizedCapturedAt, normalizedReconstructedAt, text(metadataNode, "sceneFrame", 100),
      text(metadataNode, "referenceFrame", 100), transform);
  }

  private SceneAssetEntity storeAsset(String siteId, String robotId, String bridgeRobotId,
      MultipartFile model, PreparedScene prepared) throws IOException {
    String id = Ids.next("scene");
    Path staging = assetDirectory("." + id + ".staging");
    Path target = assetDirectory(id);
    Files.createDirectories(ROOT);
    deleteDirectoryQuietly(staging);
    Files.createDirectory(staging);
    boolean published = false;
    try {
      String copiedSha;
      try (InputStream input = model.getInputStream(); var output = Files.newOutputStream(staging.resolve(MODEL_FILE))) {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[1024 * 1024];
        int read;
        while ((read = input.read(buffer)) >= 0) {
          if (read == 0) continue;
          output.write(buffer, 0, read);
          digest.update(buffer, 0, read);
        }
        copiedSha = HexFormat.of().formatHex(digest.digest());
      }
      if (!copiedSha.equals(prepared.modelSha256())) throw ApiException.conflict("上传文件在处理过程中发生变化");
      Files.write(staging.resolve(METADATA_FILE), prepared.metadataBytes());
      publishDirectory(staging, target);
      published = true;

      String now = Instant.now().toString();
      SceneAssetEntity asset = new SceneAssetEntity();
      asset.id = id;
      asset.siteId = siteId;
      asset.status = "PENDING_REVIEW";
      asset.source = "ROBOT";
      asset.sourceRobotId = robotId;
      asset.sourceBridgeRobotId = bridgeRobotId;
      asset.assetKind = "POINT_CLOUD";
      asset.format = "PLY";
      asset.originalName = prepared.originalName();
      asset.contentType = prepared.contentType();
      asset.fileSize = prepared.fileSize();
      asset.modelSha256 = prepared.modelSha256();
      asset.metadataSha256 = prepared.metadataSha256();
      asset.sourceCaptureSessionId = prepared.sourceCaptureSessionId();
      asset.sourceReconstructSessionId = prepared.sourceReconstructSessionId();
      asset.reconstructProfile = prepared.reconstructProfile();
      asset.coordinateSystem = prepared.coordinateSystem();
      asset.unit = prepared.unit();
      asset.pointCount = prepared.pointCount();
      asset.reportedPointCount = prepared.reportedPointCount();
      asset.capturedAt = prepared.capturedAt();
      asset.reconstructedAt = prepared.reconstructedAt();
      asset.createdAt = now;
      asset.updatedAt = now;
      asset.storageKey = "scene-assets/" + id + "/" + MODEL_FILE;
      asset.filesReady = true;
      asset.sceneFrame = prepared.sceneFrame();
      asset.referenceFrame = prepared.referenceFrame();
      asset.sceneToReferenceTransform = prepared.sceneToReferenceTransform();
      try {
        return assetRepository.saveAndFlush(asset);
      } catch (RuntimeException ex) {
        deleteDirectoryQuietly(target);
        throw ex;
      }
    } finally {
      if (!published) deleteDirectoryQuietly(staging);
    }
  }

  private RobotSceneUploadResult claimExisting(RobotSceneUploadEntity upload, String modelSha) {
    if (!MessageDigest.isEqual(upload.modelSha256.getBytes(StandardCharsets.US_ASCII),
        modelSha.getBytes(StandardCharsets.US_ASCII))) {
      throw ApiException.conflict("相同 Idempotency-Key 已用于不同的点云内容");
    }
    if ("SUCCEEDED".equals(upload.status) && upload.sceneAssetId != null) {
      SceneAssetEntity asset = assetRepository.findById(upload.sceneAssetId)
        .orElseThrow(() -> ApiException.conflict("幂等记录关联的三维资产不存在"));
      return new RobotSceneUploadResult(toView(asset), false);
    }
    String now = Instant.now().toString();
    String cutoff = Instant.now().minus(processingTimeout).toString();
    if (uploadRepository.claimRetryable(upload.id, cutoff, now) != 1) {
      throw ApiException.serviceUnavailable("相同 Idempotency-Key 的上传正在处理，请稍后使用原 key 重试");
    }
    return null;
  }

  private Map<String, Object> toView(SceneAssetEntity asset) {
    boolean filesReady = asset.filesReady && Files.isRegularFile(assetDirectory(asset.id).resolve(MODEL_FILE))
      && Files.isRegularFile(assetDirectory(asset.id).resolve(METADATA_FILE));
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("id", asset.id);
    view.put("siteId", asset.siteId);
    view.put("status", asset.status);
    view.put("source", asset.source);
    view.put("sourceRobotId", asset.sourceRobotId);
    view.put("sourceBridgeRobotId", asset.sourceBridgeRobotId);
    view.put("assetKind", asset.assetKind);
    view.put("format", asset.format);
    view.put("originalName", asset.originalName);
    view.put("contentType", asset.contentType);
    view.put("fileSize", asset.fileSize);
    view.put("modelSha256", asset.modelSha256);
    view.put("metadataSha256", asset.metadataSha256);
    view.put("sourceCaptureSessionId", asset.sourceCaptureSessionId);
    view.put("sourceReconstructSessionId", asset.sourceReconstructSessionId);
    view.put("reconstructProfile", asset.reconstructProfile);
    view.put("coordinateSystem", asset.coordinateSystem);
    view.put("unit", asset.unit);
    view.put("pointCount", asset.pointCount);
    view.put("reportedPointCount", asset.reportedPointCount);
    view.put("pointCountMismatch", asset.reportedPointCount != null && asset.reportedPointCount != asset.pointCount);
    view.put("capturedAt", asset.capturedAt);
    view.put("reconstructedAt", asset.reconstructedAt);
    view.put("createdAt", asset.createdAt);
    view.put("updatedAt", asset.updatedAt);
    view.put("reviewedBy", asset.reviewedBy);
    view.put("reviewedAt", asset.reviewedAt);
    view.put("reviewComment", asset.reviewComment);
    view.put("filesReady", filesReady);
    view.put("previewReady", filesReady && Files.isRegularFile(assetDirectory(asset.id).resolve(PREVIEW_FILE)));
    view.put("sceneFrame", asset.sceneFrame);
    view.put("referenceFrame", asset.referenceFrame);
    view.put("sceneToReferenceTransform", parseJson(asset.sceneToReferenceTransform));
    return view;
  }

  private SceneAssetEntity requireAsset(String id) {
    return assetRepository.findById(id).orElseThrow(() -> ApiException.notFound("三维场景资产不存在"));
  }

  private SceneAssetEntity requireReadableAsset(String id) {
    SceneAssetEntity asset = requireAsset(id);
    if (!asset.filesReady || "REJECTED".equals(asset.status) || "DELETED".equals(asset.status)) {
      throw ApiException.notFound("三维场景文件不可用");
    }
    return asset;
  }

  private void verifyStoredFiles(SceneAssetEntity asset) {
    Path model = regularFile(assetDirectory(asset.id).resolve(MODEL_FILE));
    Path metadata = regularFile(assetDirectory(asset.id).resolve(METADATA_FILE));
    try (InputStream modelInput = Files.newInputStream(model); InputStream metadataInput = Files.newInputStream(metadata)) {
      if (!asset.modelSha256.equals(digest(modelInput)) || !asset.metadataSha256.equals(digest(metadataInput))) {
        throw ApiException.conflict("三维场景文件校验失败，不能通过审核");
      }
    } catch (IOException ex) {
      throw ApiException.conflict("三维场景文件缺失，不能通过审核");
    }
  }

  private PlyHeader parsePlyHeader(MultipartFile model) throws IOException {
    try (InputStream input = model.getInputStream()) {
      ByteArrayOutputStream line = new ByteArrayOutputStream();
      String format = null;
      Long vertexCount = null;
      int total = 0;
      int lineNumber = 0;
      int value;
      while ((value = input.read()) >= 0) {
        if (++total > MAX_PLY_HEADER_BYTES) throw ApiException.badRequest("PLY header 超过安全限制");
        if (value != '\n') {
          if (value != '\r') line.write(value);
          continue;
        }
        String text = line.toString(StandardCharsets.US_ASCII).trim();
        line.reset();
        lineNumber++;
        if (lineNumber == 1 && !"ply".equals(text)) throw ApiException.badRequest("文件不是有效的 PLY");
        if (text.startsWith("format ")) format = text;
        if (text.startsWith("element vertex ")) {
          try { vertexCount = Long.parseLong(text.substring("element vertex ".length()).trim()); }
          catch (NumberFormatException ex) { throw ApiException.badRequest("PLY vertex 数量无效"); }
        }
        if ("end_header".equals(text)) {
          if (!("format ascii 1.0".equals(format) || "format binary_little_endian 1.0".equals(format))) {
            throw ApiException.badRequest("PLY 仅支持 ASCII 或 binary little-endian 1.0");
          }
          if (vertexCount == null || vertexCount < 0) throw ApiException.badRequest("PLY 缺少有效的 element vertex");
          return new PlyHeader(vertexCount);
        }
      }
      throw ApiException.badRequest("PLY 缺少完整 header");
    }
  }

  private void validateFile(MultipartFile file, long limit, String extension, String label) {
    if (file == null || file.isEmpty()) throw ApiException.badRequest(label + " 文件不能为空");
    if (file.getSize() > limit) throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, 413, label + " 文件超过大小限制");
    safeName(file.getOriginalFilename(), extension);
  }

  private String safeName(String value, String extension) {
    String name = required(value, "文件名不能为空", 255);
    if (name.contains("/") || name.contains("\\") || name.contains("\0") || !name.toLowerCase(Locale.ROOT).endsWith(extension)) {
      throw ApiException.badRequest("文件名或扩展名无效");
    }
    return name;
  }

  private String validateTransform(JsonNode transform) throws IOException {
    if (transform == null || transform.isNull()) return null;
    if (!transform.isArray() || transform.size() != 16) throw ApiException.badRequest("sceneToReferenceTransform 必须包含 16 个数字");
    for (JsonNode value : transform) if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
      throw ApiException.badRequest("sceneToReferenceTransform 只能包含有限数字");
    }
    return objectMapper.writeValueAsString(transform);
  }

  private String text(JsonNode node, String field, int maxLength) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || !value.isTextual()) return null;
    String text = normalize(value.asText());
    if (text != null && text.length() > maxLength) throw ApiException.badRequest(field + " 长度超过限制");
    return text;
  }

  private Object parseJson(String value) {
    if (value == null) return null;
    try { return objectMapper.readValue(value, List.class); } catch (IOException ex) { return null; }
  }

  private Path assetDirectory(String id) {
    Path path = ROOT.resolve(id).normalize();
    if (!path.getParent().equals(ROOT)) throw ApiException.badRequest("资产 ID 无效");
    return path;
  }

  private Path regularFile(Path path) {
    Path normalized = path.normalize();
    if (!normalized.startsWith(ROOT) || !Files.isRegularFile(normalized)) throw ApiException.notFound("三维场景文件不存在");
    return normalized;
  }

  private void publishDirectory(Path staging, Path target) throws IOException {
    try {
      Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException | AccessDeniedException ex) {
      log.warn("Atomic scene directory publish unavailable, falling back to regular move assetId={} reason={}",
        target.getFileName(), ex.getClass().getSimpleName());
      Files.move(staging, target);
    }
  }

  private void deleteDirectoryQuietly(Path root) {
    if (root == null || !root.normalize().startsWith(ROOT) || !Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      paths.sorted((left, right) -> right.getNameCount() - left.getNameCount()).forEach(path -> {
        try { Files.deleteIfExists(path); } catch (IOException ex) { log.warn("Scene file cleanup failed path={}", path, ex); }
      });
    } catch (IOException ex) {
      log.warn("Scene directory cleanup failed path={}", root, ex);
    }
  }

  private Instant lastModified(Path path) {
    try { return Files.getLastModifiedTime(path).toInstant(); } catch (IOException ex) { return Instant.MAX; }
  }

  private String requiredInstant(String value, String field) {
    String normalized = required(value, field + " 不能为空", 40);
    try { return Instant.parse(normalized).toString(); }
    catch (DateTimeParseException ex) { throw ApiException.badRequest(field + " 必须是带时区的 ISO-8601 时间"); }
  }

  private String optionalInstant(String value, String field) {
    String normalized = normalize(value);
    return normalized == null ? null : requiredInstant(normalized, field);
  }

  private String required(String value, String message, int maxLength) {
    String normalized = normalize(value);
    if (normalized == null) throw ApiException.badRequest(message);
    if (normalized.length() > maxLength) throw ApiException.badRequest(message + "（长度超过限制）");
    return normalized;
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String digest(InputStream input) throws IOException {
    MessageDigest digest = sha256Digest();
    byte[] buffer = new byte[1024 * 1024];
    int read;
    while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
    return HexFormat.of().formatHex(digest.digest());
  }

  private String sha256(byte[] value) {
    return HexFormat.of().formatHex(sha256Digest().digest(value));
  }

  private MessageDigest sha256Digest() {
    try { return MessageDigest.getInstance("SHA-256"); }
    catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
  }

  private String fingerprint(String value) { return shortHash(sha256(value.getBytes(StandardCharsets.UTF_8))); }
  private String shortHash(String value) { return value.substring(0, Math.min(12, value.length())); }

  private void publish(ResourceChangeEvent event) {
    try { messagingTemplate.convertAndSend("/topic/scene-assets", event); }
    catch (RuntimeException ex) { log.warn("Scene realtime notification failed assetId={}", event.resourceId(), ex); }
  }

  private record PlyHeader(long vertexCount) { }
  private record PreparedScene(String originalName, String contentType, long fileSize, String modelSha256,
      String metadataSha256, byte[] metadataBytes, String sourceReconstructSessionId,
      String sourceCaptureSessionId, String reconstructProfile, String coordinateSystem, String unit,
      long pointCount, Long reportedPointCount, String capturedAt, String reconstructedAt,
      String sceneFrame, String referenceFrame, String sceneToReferenceTransform) { }
}
