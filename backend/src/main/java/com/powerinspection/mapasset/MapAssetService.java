package com.powerinspection.mapasset;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.route.RouteRevisionRepository;
import com.powerinspection.route.RouteDraftRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

@Service
public class MapAssetService {
  private static final Logger log = LoggerFactory.getLogger(MapAssetService.class);
  private static final long MAX_YAML_BYTES = 1024L * 1024L;
  private static final long MAX_PGM_BYTES = 100L * 1024L * 1024L;
  private static final int MAX_PGM_HEADER_BYTES = 8192;
  private static final Path ROOT = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("map-assets").normalize();
  private static final String YAML_FILE = "map.yaml";
  private static final String PGM_FILE = "map.pgm";

  private final DataStoreService dataStore;
  private final RouteRevisionRepository routeRevisionRepository;
  private final RouteDraftRepository routeDraftRepository;
  private final RobotMapUploadRepository robotMapUploadRepository;
  private final ConcurrentMap<String, Object> uploadLocks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Object> reviewLocks = new ConcurrentHashMap<>();

  public MapAssetService(DataStoreService dataStore, RouteRevisionRepository routeRevisionRepository,
      RouteDraftRepository routeDraftRepository, RobotMapUploadRepository robotMapUploadRepository) {
    this.dataStore = dataStore;
    this.routeRevisionRepository = routeRevisionRepository;
    this.routeDraftRepository = routeDraftRepository;
    this.robotMapUploadRepository = robotMapUploadRepository;
  }

  public Map<String, Object> create(String siteId, MultipartFile yaml, MultipartFile pgm) throws IOException {
    ensureSiteExists(siteId);
    PreparedUpload prepared = prepare(yaml, pgm);
    return createAsset(siteId, yaml, pgm, prepared, "AVAILABLE", "USER", null, null, null, null);
  }

  public RobotMapUploadResult createForRobot(String siteId, String robotId, String bridgeRobotId,
      String idempotencyKey, Instant capturedAt, MultipartFile yaml, MultipartFile pgm) throws IOException {
    ensureSiteExists(siteId);
    String normalizedKey = requiredText(idempotencyKey, "缺少 Idempotency-Key");
    if (normalizedKey.length() > 160) throw ApiException.badRequest("Idempotency-Key 长度不能超过 160");
    if (robotId == null || robotId.isBlank() || robotId.length() > 100) throw ApiException.badRequest("机器人 ID 无效");
    PreparedUpload prepared = prepare(yaml, pgm);
    String lockKey = robotId + '\n' + normalizedKey;
    Object lock = uploadLocks.computeIfAbsent(lockKey, ignored -> new Object());
    try {
      synchronized (lock) {
        RobotMapUploadEntity upload = robotMapUploadRepository.findByRobotIdAndIdempotencyKey(robotId, normalizedKey).orElse(null);
        if (upload != null) {
          verifyHashes(upload, prepared);
          if ("SUCCEEDED".equals(upload.getStatus()) && upload.getMapAssetId() != null) {
            return new RobotMapUploadResult(getForManagement(upload.getMapAssetId()), false);
          }
          if ("PROCESSING".equals(upload.getStatus())) {
            throw ApiException.conflict("相同 Idempotency-Key 的上传正在处理");
          }
          if (!"FAILED".equals(upload.getStatus())
              || robotMapUploadRepository.claimFailed(upload.getId(), Instant.now().toString()) != 1) {
            throw ApiException.conflict("相同 Idempotency-Key 的上传状态已变化，请稍后重试");
          }
          upload = robotMapUploadRepository.findById(upload.getId())
            .orElseThrow(() -> ApiException.conflict("幂等上传记录不存在"));
        } else {
          ClaimResult claim = claimRobotUpload(robotId, normalizedKey, prepared);
          upload = claim.upload();
          if (!claim.claimed()) {
            verifyHashes(upload, prepared);
            if ("SUCCEEDED".equals(upload.getStatus()) && upload.getMapAssetId() != null) {
              return new RobotMapUploadResult(getForManagement(upload.getMapAssetId()), false);
            }
            throw ApiException.conflict("相同 Idempotency-Key 的上传正在处理");
          }
        }

        Map<String, Object> asset = null;
        try {
          asset = createAsset(siteId, yaml, pgm, prepared, "PENDING_REVIEW", "ROBOT",
            robotId, bridgeRobotId, normalizedKey, capturedAt);
          upload.setMapAssetId(String.valueOf(asset.get("id")));
          upload.setStatus("SUCCEEDED");
          upload.setUpdatedAt(Instant.now().toString());
          robotMapUploadRepository.saveAndFlush(upload);
          return new RobotMapUploadResult(asset, true);
        } catch (IOException | RuntimeException ex) {
          if (asset != null) deleteIfUnreferenced(String.valueOf(asset.get("id")));
          upload.setStatus("FAILED");
          upload.setUpdatedAt(Instant.now().toString());
          try {
            robotMapUploadRepository.saveAndFlush(upload);
          } catch (RuntimeException updateFailure) {
            ex.addSuppressed(updateFailure);
          }
          throw ex;
        }
      }
    } finally {
      uploadLocks.remove(lockKey, lock);
    }
  }

  private PreparedUpload prepare(MultipartFile yaml, MultipartFile pgm) throws IOException {
    validateFile(yaml, MAX_YAML_BYTES, List.of(".yaml", ".yml"), "YAML");
    validateFile(pgm, MAX_PGM_BYTES, List.of(".pgm"), "PGM");

    String yamlName = safeOriginalName(yaml, "map.yaml");
    String pgmName = safeOriginalName(pgm, "map.pgm");
    byte[] yamlBytes = yaml.getBytes();
    Map<String, Object> yamlConfig = parseYaml(yamlBytes);
    String image = requiredText(yamlConfig.get("image"), "YAML 缺少有效的 image 字段");
    if (!basename(image).equalsIgnoreCase(pgmName)) {
      throw ApiException.badRequest("YAML 的 image 与上传的 PGM 文件名不匹配");
    }
    double resolution = positiveNumber(yamlConfig.get("resolution"), "YAML 的 resolution 必须大于 0");
    List<Double> origin = origin(yamlConfig.get("origin"));
    int negate = negate(yamlConfig.getOrDefault("negate", 0));
    validateThresholds(yamlConfig);
    PgmHeader pgmHeader = parsePgmHeader(pgm);
    String pgmSha256;
    try (InputStream input = pgm.getInputStream()) {
      pgmSha256 = digest(input);
    }
    return new PreparedUpload(yamlBytes, yamlName, pgmName, image, resolution, origin, negate,
      pgmHeader.width(), pgmHeader.height(), sha256(yamlBytes), pgmSha256);
  }

  private Map<String, Object> createAsset(String siteId, MultipartFile yaml, MultipartFile pgm,
      PreparedUpload prepared, String status, String source, String sourceRobotId, String sourceBridgeRobotId,
      String idempotencyKey, Instant capturedAt) throws IOException {
    String id = Ids.next("map");
    Path staging = resolveAssetDirectory("." + id + ".staging");
    Path target = resolveAssetDirectory(id);
    Files.createDirectories(ROOT);
    deleteDirectoryQuietly(staging);
    Files.createDirectory(staging);
    boolean published = false;
    try {
      Files.write(staging.resolve(YAML_FILE), prepared.yamlBytes());
      String pgmSha256;
      try (InputStream input = pgm.getInputStream()) {
        MessageDigest digest = sha256Digest();
        try (var digestInput = new java.security.DigestInputStream(input, digest)) {
          Files.copy(digestInput, staging.resolve(PGM_FILE));
        }
        pgmSha256 = HexFormat.of().formatHex(digest.digest());
      }
      if (!prepared.pgmSha256().equals(pgmSha256)) {
        throw ApiException.conflict("PGM 文件内容在上传处理期间发生变化");
      }
      publish(staging, target);
      published = true;

      String now = Instant.now().toString();
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("id", id);
      metadata.put("siteId", siteId);
      metadata.put("status", status);
      metadata.put("source", source);
      metadata.put("sourceRobotId", sourceRobotId);
      metadata.put("sourceBridgeRobotId", sourceBridgeRobotId);
      metadata.put("uploadIdempotencyKey", idempotencyKey);
      metadata.put("capturedAt", capturedAt == null ? null : capturedAt.toString());
      metadata.put("reviewedBy", null);
      metadata.put("reviewedAt", null);
      metadata.put("reviewComment", null);
      metadata.put("yamlName", prepared.yamlName());
      metadata.put("pgmName", prepared.pgmName());
      metadata.put("image", prepared.image());
      metadata.put("resolution", prepared.resolution());
      metadata.put("origin", prepared.origin());
      metadata.put("negate", prepared.negate());
      metadata.put("width", prepared.width());
      metadata.put("height", prepared.height());
      metadata.put("yamlSize", yaml.getSize());
      metadata.put("pgmSize", pgm.getSize());
      metadata.put("yamlSha256", prepared.yamlSha256());
      metadata.put("pgmSha256", pgmSha256);
      metadata.put("createdAt", now);
      metadata.put("updatedAt", now);
      try {
        return dataStore.upsert(DataCategory.MAP_ASSET, metadata);
      } catch (RuntimeException ex) {
        deleteDirectoryQuietly(target);
        throw ex;
      }
    } finally {
      if (!published) deleteDirectoryQuietly(staging);
    }
  }

  public Map<String, Object> get(String id) {
    Map<String, Object> asset = dataStore.get(DataCategory.MAP_ASSET, id);
    if (!"AVAILABLE".equals(String.valueOf(asset.get("status")))) {
      throw ApiException.notFound("地图资产不可用");
    }
    return asset;
  }

  public Map<String, Object> getForManagement(String id) {
    return managementView(dataStore.get(DataCategory.MAP_ASSET, id));
  }

  public List<Map<String, Object>> listForManagement(String source, String status, String siteId) {
    String effectiveStatus = status == null || status.isBlank() ? "AVAILABLE" : status.trim();
    return dataStore.list(DataCategory.MAP_ASSET).stream()
      .filter(asset -> source == null || source.isBlank() || source.equals(String.valueOf(asset.get("source"))))
      .filter(asset -> effectiveStatus.equals(String.valueOf(asset.get("status"))))
      .filter(asset -> siteId == null || siteId.isBlank() || siteId.equals(String.valueOf(asset.get("siteId"))))
      .map(this::managementView)
      .toList();
  }

  public Map<String, Object> review(String id, String action, String comment, String reviewerId) {
    String normalizedAction = requiredText(action, "审核动作不能为空").toUpperCase(Locale.ROOT);
    if (!List.of("APPROVE", "REJECT").contains(normalizedAction)) {
      throw ApiException.badRequest("审核动作只能是 APPROVE 或 REJECT");
    }
    String normalizedComment = comment == null ? null : comment.trim();
    if ("REJECT".equals(normalizedAction) && (normalizedComment == null || normalizedComment.isBlank())) {
      throw ApiException.badRequest("驳回时必须填写审核意见");
    }
    Object lock = reviewLocks.computeIfAbsent(id, ignored -> new Object());
    try {
      synchronized (lock) {
        Map<String, Object> asset = getForManagement(id);
        if (!"ROBOT".equals(String.valueOf(asset.get("source")))) {
          throw ApiException.badRequest("仅机器人建图资产需要审核");
        }
        if (!"PENDING_REVIEW".equals(String.valueOf(asset.get("status")))) {
          throw ApiException.conflict("地图资产已经完成审核，不能重复审核");
        }
        if ("APPROVE".equals(normalizedAction)) verifyStoredFiles(id, asset);
        String now = Instant.now().toString();
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("status", "APPROVE".equals(normalizedAction) ? "AVAILABLE" : "REJECTED");
        patch.put("reviewedBy", reviewerId);
        patch.put("reviewedAt", now);
        patch.put("reviewComment", normalizedComment);
        patch.put("updatedAt", now);
        return dataStore.patch(DataCategory.MAP_ASSET, id, patch);
      }
    } finally {
      reviewLocks.remove(id, lock);
    }
  }

  public Path yamlPath(String id) {
    get(id);
    return regularAssetFile(id, YAML_FILE);
  }

  public Path pgmPath(String id) {
    get(id);
    return regularAssetFile(id, PGM_FILE);
  }

  public Path yamlPathForManagement(String id) {
    getForManagement(id);
    return regularAssetFile(id, YAML_FILE);
  }

  public Path pgmPathForManagement(String id) {
    getForManagement(id);
    return regularAssetFile(id, PGM_FILE);
  }

  public void ensureAvailableForSite(String id, String siteId) {
    Map<String, Object> asset = get(id);
    if (!siteId.equals(String.valueOf(asset.get("siteId")))) {
      throw ApiException.badRequest("地图资产不属于当前站点");
    }
  }

  public void delete(String id) throws IOException {
    get(id);
    if (isReferenced(id)) {
      throw ApiException.conflict("地图资产正在被巡检路线使用");
    }
    deleteAssetFiles(id);
    dataStore.delete(DataCategory.MAP_ASSET, id);
  }

  public void deleteIfUnreferenced(String id) {
    if (id == null || id.isBlank() || dataStore.find(DataCategory.MAP_ASSET, id) == null || isReferenced(id)) return;
    try {
      deleteAssetFiles(id);
      dataStore.delete(DataCategory.MAP_ASSET, id);
    } catch (IOException ex) {
      log.error("Failed to clean unreferenced map asset {}", id, ex);
    }
  }

  private void validateFile(MultipartFile file, long maxBytes, List<String> extensions, String label) {
    if (file == null || file.isEmpty()) throw ApiException.badRequest("请上传 " + label + " 文件");
    if (file.getSize() > maxBytes) throw ApiException.badRequest(label + " 文件过大");
    String name = safeOriginalName(file, "").toLowerCase(Locale.ROOT);
    if (extensions.stream().noneMatch(name::endsWith)) {
      throw ApiException.badRequest(label + " 文件扩展名不正确");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseYaml(byte[] bytes) {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(0);
    options.setCodePointLimit((int) MAX_YAML_BYTES);
    try {
      Object loaded = new Yaml(new SafeConstructor(options)).load(new String(bytes, StandardCharsets.UTF_8));
      if (!(loaded instanceof Map<?, ?> map)) throw ApiException.badRequest("YAML 根节点必须是地图配置对象");
      return new LinkedHashMap<>((Map<String, Object>) map);
    } catch (YAMLException ex) {
      throw ApiException.badRequest("YAML 解析失败");
    }
  }

  private void validateThresholds(Map<String, Object> config) {
    Double free = optionalUnitNumber(config.get("free_thresh"), "free_thresh");
    Double occupied = optionalUnitNumber(config.get("occupied_thresh"), "occupied_thresh");
    if (free != null && occupied != null && free >= occupied) {
      throw ApiException.badRequest("YAML 的 free_thresh 必须小于 occupied_thresh");
    }
    Object mode = config.get("mode");
    if (mode != null && !List.of("trinary", "scale", "raw").contains(mode.toString())) {
      throw ApiException.badRequest("YAML 的 mode 不受支持");
    }
  }

  private PgmHeader parsePgmHeader(MultipartFile pgm) throws IOException {
    byte[] headerBytes;
    try (InputStream input = pgm.getInputStream()) {
      headerBytes = input.readNBytes(MAX_PGM_HEADER_BYTES);
    }
    int[] index = {0};
    String magic = readAsciiToken(headerBytes, index);
    int width = positiveInt(readAsciiToken(headerBytes, index), "PGM 宽度无效");
    int height = positiveInt(readAsciiToken(headerBytes, index), "PGM 高度无效");
    int maxValue = positiveInt(readAsciiToken(headerBytes, index), "PGM 灰度上限无效");
    if (!"P5".equals(magic) || maxValue > 255) throw ApiException.badRequest("仅支持 8-bit P5 PGM 地图");
    if (index[0] >= headerBytes.length || (headerBytes[index[0]] & 0xff) > 32) {
      throw ApiException.badRequest("PGM 文件头不完整");
    }
    if ((headerBytes[index[0]] & 0xff) == 13 && index[0] + 1 < headerBytes.length && (headerBytes[index[0] + 1] & 0xff) == 10) {
      index[0] += 2;
    } else {
      index[0] += 1;
    }
    long pixels;
    try {
      pixels = Math.multiplyExact((long) width, (long) height);
    } catch (ArithmeticException ex) {
      throw ApiException.badRequest("PGM 尺寸过大");
    }
    if (pixels > MAX_PGM_BYTES || pgm.getSize() < index[0] + pixels) {
      throw ApiException.badRequest("PGM 像素数据不完整或尺寸过大");
    }
    return new PgmHeader(width, height);
  }

  private String readAsciiToken(byte[] bytes, int[] index) {
    while (index[0] < bytes.length) {
      int value = bytes[index[0]] & 0xff;
      if (value == '#') {
        while (index[0] < bytes.length && bytes[index[0]] != '\n') index[0]++;
      } else if (value <= 32) {
        index[0]++;
      } else {
        break;
      }
    }
    int start = index[0];
    while (index[0] < bytes.length && (bytes[index[0]] & 0xff) > 32) index[0]++;
    if (start == index[0]) throw ApiException.badRequest("PGM 文件头不完整");
    return new String(bytes, start, index[0] - start, StandardCharsets.US_ASCII);
  }

  private Path regularAssetFile(String id, String filename) {
    Path file = resolveAssetDirectory(id).resolve(filename).normalize();
    if (!file.startsWith(ROOT) || !Files.isRegularFile(file)) {
      throw ApiException.notFound("地图资产文件不存在，请重新上传 YAML/PGM");
    }
    return file;
  }

  private Map<String, Object> managementView(Map<String, Object> asset) {
    Map<String, Object> view = new LinkedHashMap<>(asset);
    view.put("filesReady", storedFilesReady(String.valueOf(asset.get("id"))));
    return view;
  }

  private boolean storedFilesReady(String id) {
    Path directory = resolveAssetDirectory(id);
    return Files.isRegularFile(directory.resolve(YAML_FILE)) && Files.isRegularFile(directory.resolve(PGM_FILE));
  }

  private void verifyStoredFiles(String id, Map<String, Object> asset) {
    if (!storedFilesReady(id)) {
      throw ApiException.conflict("地图资产文件不完整，无法通过审核，请让机器人使用新的 Idempotency-Key 重新上传");
    }
    try (InputStream yamlInput = Files.newInputStream(regularAssetFile(id, YAML_FILE));
         InputStream pgmInput = Files.newInputStream(regularAssetFile(id, PGM_FILE))) {
      String yamlSha256 = digest(yamlInput);
      String pgmSha256 = digest(pgmInput);
      if (!yamlSha256.equals(String.valueOf(asset.get("yamlSha256")))
          || !pgmSha256.equals(String.valueOf(asset.get("pgmSha256")))) {
        throw ApiException.conflict("地图资产文件校验失败，无法通过审核，请重新上传");
      }
    } catch (IOException ex) {
      throw ApiException.conflict("地图资产文件读取失败，无法通过审核，请重新上传");
    }
  }

  private Path resolveAssetDirectory(String name) {
    Path path = ROOT.resolve(name).normalize();
    if (!path.startsWith(ROOT)) throw ApiException.badRequest("地图资产路径无效");
    return path;
  }

  private void publish(Path staging, Path target) throws IOException {
    try {
      Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ex) {
      Files.move(staging, target);
    }
  }

  private void deleteAssetFiles(String id) throws IOException {
    Path directory = resolveAssetDirectory(id);
    Files.deleteIfExists(directory.resolve(YAML_FILE));
    Files.deleteIfExists(directory.resolve(PGM_FILE));
    Files.deleteIfExists(directory);
  }

  private void deleteDirectoryQuietly(Path directory) {
    try {
      Files.deleteIfExists(directory.resolve(YAML_FILE));
      Files.deleteIfExists(directory.resolve(PGM_FILE));
      Files.deleteIfExists(directory);
    } catch (IOException ex) {
      log.warn("Failed to clean map asset directory {}", directory, ex);
    }
  }

  private boolean isReferenced(String id) {
    return routeRevisionRepository.existsByMapAssetId(id)
      || routeDraftRepository.existsByMapAssetId(id)
      || dataStore.list(DataCategory.ROUTE).stream()
        .anyMatch(route -> id.equals(String.valueOf(route.get("mapId"))));
  }

  private void ensureSiteExists(String siteId) {
    if (siteId == null || siteId.isBlank() || "null".equals(siteId) || dataStore.find(DataCategory.SITE, siteId) == null) {
      throw ApiException.badRequest("站点不存在");
    }
  }

  private String safeOriginalName(MultipartFile file, String fallback) {
    String raw = file == null ? null : file.getOriginalFilename();
    if (raw == null || raw.isBlank()) return fallback;
    String name = basename(raw).replace("\r", "").replace("\n", "");
    if (name.isBlank() || name.length() > 180 || name.equals(".") || name.equals("..")) {
      throw ApiException.badRequest("上传文件名无效");
    }
    return name;
  }

  private String basename(String value) {
    String normalized = value.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    return slash >= 0 ? normalized.substring(slash + 1) : normalized;
  }

  private String requiredText(Object value, String message) {
    if (value == null || value.toString().isBlank()) throw ApiException.badRequest(message);
    return value.toString().trim();
  }

  private double positiveNumber(Object value, String message) {
    double number = finiteNumber(value, message);
    if (number <= 0) throw ApiException.badRequest(message);
    return number;
  }

  private Double optionalUnitNumber(Object value, String field) {
    if (value == null) return null;
    double number = finiteNumber(value, "YAML 的 " + field + " 必须是数字");
    if (number < 0 || number > 1) throw ApiException.badRequest("YAML 的 " + field + " 必须在 0 到 1 之间");
    return number;
  }

  private double finiteNumber(Object value, String message) {
    try {
      double number = value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
      if (!Double.isFinite(number)) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException ex) {
      throw ApiException.badRequest(message);
    }
  }

  private List<Double> origin(Object value) {
    if (!(value instanceof List<?> values) || values.size() != 3) {
      throw ApiException.badRequest("YAML 的 origin 必须包含 3 个数字");
    }
    return values.stream().map(item -> finiteNumber(item, "YAML 的 origin 必须包含 3 个数字")).toList();
  }

  private int negate(Object value) {
    if (value instanceof Boolean flag) return flag ? 1 : 0;
    int number = positiveOrZeroInt(String.valueOf(value), "YAML 的 negate 只能是 0、1、false 或 true");
    if (number > 1) throw ApiException.badRequest("YAML 的 negate 只能是 0、1、false 或 true");
    return number;
  }

  private int positiveInt(String value, String message) {
    int number = positiveOrZeroInt(value, message);
    if (number <= 0) throw ApiException.badRequest(message);
    return number;
  }

  private int positiveOrZeroInt(String value, String message) {
    try {
      int number = Integer.parseInt(value);
      if (number < 0) throw new NumberFormatException();
      return number;
    } catch (NumberFormatException ex) {
      throw ApiException.badRequest(message);
    }
  }

  private String sha256(byte[] bytes) {
    return HexFormat.of().formatHex(sha256Digest().digest(bytes));
  }

  private String digest(InputStream input) throws IOException {
    MessageDigest digest = sha256Digest();
    try (var digestInput = new java.security.DigestInputStream(input, digest)) {
      digestInput.transferTo(java.io.OutputStream.nullOutputStream());
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private ClaimResult claimRobotUpload(String robotId, String idempotencyKey, PreparedUpload prepared) {
    String now = Instant.now().toString();
    RobotMapUploadEntity upload = new RobotMapUploadEntity();
    upload.setRobotId(robotId);
    upload.setIdempotencyKey(idempotencyKey);
    upload.setYamlSha256(prepared.yamlSha256());
    upload.setPgmSha256(prepared.pgmSha256());
    upload.setStatus("PROCESSING");
    upload.setCreatedAt(now);
    upload.setUpdatedAt(now);
    try {
      return new ClaimResult(robotMapUploadRepository.saveAndFlush(upload), true);
    } catch (DataIntegrityViolationException ex) {
      RobotMapUploadEntity existing = robotMapUploadRepository.findByRobotIdAndIdempotencyKey(robotId, idempotencyKey)
        .orElseThrow(() -> ApiException.conflict("幂等上传记录发生并发冲突，请稍后重试"));
      return new ClaimResult(existing, false);
    }
  }

  private void verifyHashes(RobotMapUploadEntity upload, PreparedUpload prepared) {
    if (!Objects.equals(upload.getYamlSha256(), prepared.yamlSha256())
        || !Objects.equals(upload.getPgmSha256(), prepared.pgmSha256())) {
      throw ApiException.conflict("相同 Idempotency-Key 已用于不同的地图内容");
    }
  }

  private record PreparedUpload(byte[] yamlBytes, String yamlName, String pgmName, String image,
      double resolution, List<Double> origin, int negate, int width, int height,
      String yamlSha256, String pgmSha256) {
  }

  private record ClaimResult(RobotMapUploadEntity upload, boolean claimed) {
  }

  private MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private record PgmHeader(int width, int height) {
  }
}
