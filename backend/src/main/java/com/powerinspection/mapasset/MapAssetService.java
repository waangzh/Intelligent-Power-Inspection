package com.powerinspection.mapasset;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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

  public MapAssetService(DataStoreService dataStore) {
    this.dataStore = dataStore;
  }

  public Map<String, Object> create(String siteId, MultipartFile yaml, MultipartFile pgm) throws IOException {
    ensureSiteExists(siteId);
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

    String id = Ids.next("map");
    Path staging = resolveAssetDirectory("." + id + ".staging");
    Path target = resolveAssetDirectory(id);
    Files.createDirectories(ROOT);
    deleteDirectoryQuietly(staging);
    Files.createDirectory(staging);
    boolean published = false;
    try {
      Files.write(staging.resolve(YAML_FILE), yamlBytes);
      String pgmSha256;
      try (InputStream input = pgm.getInputStream()) {
        MessageDigest digest = sha256Digest();
        try (var digestInput = new java.security.DigestInputStream(input, digest)) {
          Files.copy(digestInput, staging.resolve(PGM_FILE));
        }
        pgmSha256 = HexFormat.of().formatHex(digest.digest());
      }
      publish(staging, target);
      published = true;

      String now = Instant.now().toString();
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("id", id);
      metadata.put("siteId", siteId);
      metadata.put("status", "AVAILABLE");
      metadata.put("yamlName", yamlName);
      metadata.put("pgmName", pgmName);
      metadata.put("image", image);
      metadata.put("resolution", resolution);
      metadata.put("origin", origin);
      metadata.put("negate", negate);
      metadata.put("width", pgmHeader.width());
      metadata.put("height", pgmHeader.height());
      metadata.put("yamlSize", yaml.getSize());
      metadata.put("pgmSize", pgm.getSize());
      metadata.put("yamlSha256", sha256(yamlBytes));
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

  public Path yamlPath(String id) {
    get(id);
    return regularAssetFile(id, YAML_FILE);
  }

  public Path pgmPath(String id) {
    get(id);
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
    if (!file.startsWith(ROOT) || !Files.isRegularFile(file)) throw ApiException.notFound("地图资产文件不存在");
    return file;
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
    return dataStore.list(DataCategory.ROUTE).stream()
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
