package com.powerinspection.route;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.mapasset.MapAssetService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RouteRevisionService {
  private final DataStoreService dataStore;
  private final MapAssetService mapAssetService;
  private final RouteRevisionRepository repository;
  private final RouteCanonicalJsonService canonicalJson;
  private final ObjectMapper objectMapper;

  public RouteRevisionService(
      DataStoreService dataStore,
      MapAssetService mapAssetService,
      RouteRevisionRepository repository,
      RouteCanonicalJsonService canonicalJson,
      ObjectMapper objectMapper) {
    this.dataStore = dataStore;
    this.mapAssetService = mapAssetService;
    this.repository = repository;
    this.canonicalJson = canonicalJson;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Map<String, Object> create(String routeId, String createdBy) {
    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, routeId);
    Map<String, Object> source = RouteExecutorSupport.executor(route);
    if (source == null) {
      throw ApiException.badRequest("路线尚未保存执行器 JSON");
    }
    String mapId = text(route.get("mapId"));
    if (mapId == null) {
      throw ApiException.badRequest("路线未绑定地图资产");
    }
    Map<String, Object> mapAsset = mapAssetService.get(mapId);
    ObjectNode document = asObject(objectMapper.valueToTree(source), "executorJson must be an object").deepCopy();
    normalizeForPublishedRevision(document, mapAsset);

    @SuppressWarnings("unchecked")
    Map<String, Object> normalizedMap = objectMapper.convertValue(document, new TypeReference<Map<String, Object>>() {});
    RouteExecutorSupport.validate(normalizedMap);
    validatePlatformPublishability(document);

    String contentSha256 = canonicalJson.sha256(document);
    RouteRevisionEntity existing = repository.findByRouteIdAndContentSha256(routeId, contentSha256).orElse(null);
    if (existing != null) {
      return toDto(existing);
    }

    long revisionNo = repository.findTopByRouteIdOrderByRevisionNoDesc(routeId)
      .map(item -> item.getRevisionNo() + 1)
      .orElse(1L);
    String now = Instant.now().toString();
    RouteRevisionEntity entity = new RouteRevisionEntity();
    entity.setId(Ids.next("route_rev"));
    entity.setRouteId(routeId);
    entity.setRevisionNo(revisionNo);
    entity.setExecutorJson(canonicalJson.canonicalJson(document));
    entity.setContentSha256(contentSha256);
    entity.setMapAssetId(mapId);
    entity.setMapImageSha256(requiredText(mapAsset.get("pgmSha256"), "地图资产缺少 PGM SHA-256"));
    entity.setValidationReportJson(canonicalJson.canonicalJson(validationReport(document, mapAsset, now)));
    entity.setCreatedBy(createdBy);
    entity.setCreatedAt(now);
    return toDto(repository.save(entity));
  }

  public List<Map<String, Object>> list(String routeId) {
    return repository.findByRouteIdOrderByRevisionNoDesc(routeId).stream().map(this::toDto).toList();
  }

  public Map<String, Object> get(String revisionId) {
    return toDto(require(revisionId));
  }

  public RouteRevisionEntity require(String revisionId) {
    return repository.findById(revisionId).orElseThrow(() -> ApiException.notFound("路线修订不存在"));
  }

  private void normalizeForPublishedRevision(ObjectNode document, Map<String, Object> mapAsset) {
    int sourceVersion = document.path("version").asInt(-1);
    if (sourceVersion != 2 && sourceVersion != 3) {
      throw ApiException.badRequest("executorJson.version must be 2 or 3");
    }
    document.put("version", 3);
    document.put("frame_id", "map");
    document.set("map", mergeObject(document.get("map"), mapIdentity(mapAsset), "executorJson.map must be an object"));

    ObjectNode startPose = asObject(document.get("start_pose"), "executorJson.start_pose must be an object");
    ObjectNode start = asObject(startPose.get("pose"), "executorJson.start_pose.pose must be an object");
    startPose.put("frame_id", "map");
    startPose.set("location", mergeObject(startPose.get("location"), mapPose(start), "executorJson.start_pose.location must be an object"));

    JsonNode rawTargets = document.get("targets");
    if (!(rawTargets instanceof ArrayNode targets)) {
      throw ApiException.badRequest("executorJson.targets must be a list");
    }
    for (JsonNode rawTarget : targets) {
      ObjectNode target = asObject(rawTarget, "executorJson target must be an object");
      ObjectNode pose = asObject(target.get("pose"), "executorJson target.pose must be an object");
      target.set("location", mergeObject(target.get("location"), mapPose(pose), "executorJson target.location must be an object"));
    }

    JsonNode schedules = document.get("schedules");
    if (!(schedules instanceof ArrayNode) || !schedules.isEmpty()) {
      throw ApiException.badRequest("平台路线不允许配置 schedules");
    }
    if (sourceVersion == 2 && !document.has("keepout_zones")) {
      document.set("keepout_zones", objectMapper.createArrayNode());
    }
  }

  private ObjectNode mapIdentity(Map<String, Object> asset) {
    ObjectNode identity = objectMapper.createObjectNode();
    identity.put("yaml", requiredText(asset.get("yamlName"), "地图资产缺少 YAML 文件名"));
    identity.put("image", requiredText(asset.get("pgmName"), "地图资产缺少 PGM 文件名"));
    identity.put("resolution", number(asset.get("resolution"), "地图资产 resolution 无效"));
    ArrayNode origin = identity.putArray("origin");
    Object rawOrigin = asset.get("origin");
    if (!(rawOrigin instanceof List<?> values) || values.size() != 3) {
      throw ApiException.badRequest("地图资产 origin 无效");
    }
    for (Object value : values) origin.add(number(value, "地图资产 origin 无效"));
    identity.put("width", positiveInt(asset.get("width"), "地图资产 width 无效"));
    identity.put("height", positiveInt(asset.get("height"), "地图资产 height 无效"));
    identity.put("image_sha256", requiredText(asset.get("pgmSha256"), "地图资产缺少 PGM SHA-256"));
    return identity;
  }

  private ObjectNode mapPose(ObjectNode pose) {
    ObjectNode location = objectMapper.createObjectNode();
    location.put("type", "map_pose");
    location.put("frame_id", "map");
    location.set("x", pose.get("x"));
    location.set("y", pose.get("y"));
    location.set("yaw", pose.get("yaw"));
    return location;
  }

  private ObjectNode mergeObject(JsonNode raw, ObjectNode generated, String message) {
    ObjectNode merged = raw == null || raw.isNull() ? objectMapper.createObjectNode() : asObject(raw, message);
    generated.fields().forEachRemaining(field -> merged.set(field.getKey(), field.getValue()));
    return merged;
  }

  private void validatePlatformPublishability(ObjectNode document) {
    JsonNode rawRoutes = document.get("routes");
    if (!(rawRoutes instanceof ArrayNode routes) || routes.size() != 1) {
      throw ApiException.badRequest("平台发布仅支持一条路线");
    }
    String activeRouteId = document.path("active_route_id").asText();
    String routeId = routes.get(0).path("id").asText();
    if (activeRouteId.isBlank() || !activeRouteId.equals(routeId)) {
      throw ApiException.badRequest("平台发布要求 active_route_id 指向唯一路线");
    }
  }

  private ObjectNode validationReport(ObjectNode document, Map<String, Object> mapAsset, String now) {
    ObjectNode report = objectMapper.createObjectNode();
    report.put("valid", true);
    report.put("validatedAt", now);
    report.put("routeVersion", document.path("version").asInt());
    report.put("mapAssetId", requiredText(mapAsset.get("id"), "地图资产缺少 id"));
    report.put("mapImageSha256", requiredText(mapAsset.get("pgmSha256"), "地图资产缺少 PGM SHA-256"));
    report.set("issues", objectMapper.createArrayNode());
    return report;
  }

  private Map<String, Object> toDto(RouteRevisionEntity entity) {
    try {
      Map<String, Object> dto = new LinkedHashMap<>();
      dto.put("id", entity.getId());
      dto.put("routeId", entity.getRouteId());
      dto.put("revisionNo", entity.getRevisionNo());
      dto.put("contentSha256", entity.getContentSha256());
      dto.put("mapAssetId", entity.getMapAssetId());
      dto.put("mapImageSha256", entity.getMapImageSha256());
      dto.put("executorJson", objectMapper.readValue(entity.getExecutorJson(), new TypeReference<Map<String, Object>>() {}));
      dto.put("validationReport", objectMapper.readValue(entity.getValidationReportJson(), new TypeReference<Map<String, Object>>() {}));
      dto.put("createdBy", entity.getCreatedBy());
      dto.put("createdAt", entity.getCreatedAt());
      return dto;
    } catch (Exception ex) {
      throw new IllegalStateException("路线修订数据损坏", ex);
    }
  }

  private ObjectNode asObject(JsonNode node, String message) {
    if (node instanceof ObjectNode object) return object;
    throw ApiException.badRequest(message);
  }

  private String requiredText(Object value, String message) {
    String text = text(value);
    if (text == null) throw ApiException.badRequest(message);
    return text;
  }

  private String text(Object value) {
    if (value == null || value.toString().isBlank() || "null".equals(value.toString())) return null;
    return value.toString();
  }

  private double number(Object value, String message) {
    try {
      double result = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
      if (!Double.isFinite(result)) throw new NumberFormatException();
      return result;
    } catch (NumberFormatException ex) {
      throw ApiException.badRequest(message);
    }
  }

  private int positiveInt(Object value, String message) {
    double number = number(value, message);
    if (number <= 0 || number != Math.rint(number) || number > Integer.MAX_VALUE) {
      throw ApiException.badRequest(message);
    }
    return (int) number;
  }
}
