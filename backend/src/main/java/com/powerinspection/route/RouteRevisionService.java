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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class RouteRevisionService {
  private final DataStoreService dataStore;
  private final MapAssetService mapAssetService;
  private final RouteRevisionRepository repository;
  private final RouteDraftRepository draftRepository;
  private final RouteCanonicalJsonService canonicalJson;
  private final ObjectMapper objectMapper;
  private final RouteDocumentValidator documentValidator;

  public RouteRevisionService(
      DataStoreService dataStore,
      MapAssetService mapAssetService,
      RouteRevisionRepository repository,
      RouteDraftRepository draftRepository,
      RouteCanonicalJsonService canonicalJson,
      ObjectMapper objectMapper,
      RouteDocumentValidator documentValidator) {
    this.dataStore = dataStore;
    this.mapAssetService = mapAssetService;
    this.repository = repository;
    this.draftRepository = draftRepository;
    this.canonicalJson = canonicalJson;
    this.objectMapper = objectMapper;
    this.documentValidator = documentValidator;
  }

  @Transactional
  public Map<String, Object> create(String routeId, String createdBy) {
    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, routeId);
    ensureRouteEditable(route);
    RouteDraftEntity draft = draftRepository.findByRouteId(routeId).orElse(null);
    if (draft != null && !isDraftPublishable(draft)) {
      throw ApiException.conflict("草稿当前不可发布，请处理校验问题或地图身份不一致的问题");
    }
    Map<String, Object> source = draft == null ? RouteExecutorSupport.executor(route) : document(draft.getExecutorJson());
    if (source == null) {
      throw ApiException.badRequest("路线尚未保存执行器 JSON");
    }
    String mapId = draft == null ? text(route.get("mapId")) : draft.getMapAssetId();
    if (mapId == null) {
      throw ApiException.badRequest("路线未绑定地图资产");
    }
    Map<String, Object> mapAsset = mapAssetService.get(mapId);
    ObjectNode document = asObject(objectMapper.valueToTree(source), "executorJson must be an object").deepCopy();
    normalizeForPublishedRevision(document, mapAsset);

    @SuppressWarnings("unchecked")
    Map<String, Object> normalizedMap = objectMapper.convertValue(document, new TypeReference<Map<String, Object>>() {});
    RouteExecutorSupport.validate(normalizedMap);
    List<RouteDocumentValidator.ValidationIssue> issues = documentValidator.validate(document);
    if (!issues.isEmpty()) {
      throw ApiException.badRequest("路线参数校验失败: " + issues.get(0).jsonPointer() + " " + issues.get(0).message());
    }

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
    entity.setValidationReportJson(canonicalJson.canonicalJson(validationReport(document, mapAsset, now, issues)));
    entity.setCreatedBy(createdBy);
    entity.setCreatedAt(now);
    return toDto(repository.save(entity));
  }

  /** 复用草稿保存的规范化与校验逻辑；该接口不持久化草稿。 */
  public Map<String, Object> validateDraft(String routeId, JsonNode executorJson, String requestedMapAssetId) {
    DraftCheck check = inspectDraft(routeId, executorJson, requestedMapAssetId);
    return draftResponse(check, check.valid());
  }

  @Transactional
  public Map<String, Object> saveDraft(String routeId, JsonNode executorJson, String requestedMapAssetId, Long expectedVersion, String updatedBy) {
    DraftCheck check = inspectDraft(routeId, executorJson, requestedMapAssetId);
    RouteDraftEntity entity = draftRepository.findByRouteId(routeId).orElse(null);
    if (entity != null && (expectedVersion == null || entity.getVersion() != expectedVersion)) {
      throw ApiException.conflict("草稿已被其他用户更新，请刷新后重试");
    }
    if (entity == null) {
      if (expectedVersion != null) throw ApiException.conflict("草稿版本已变化，请刷新后重试");
      entity = new RouteDraftEntity();
      entity.setRouteId(routeId);
    }
    entity.setExecutorJson(canonicalJson.canonicalJson(check.normalizedExecutorJson()));
    entity.setValidationReportJson(canonicalJson.canonicalJson(draftValidationReport(check)));
    entity.setMapAssetId(check.mapAssetId());
    entity.setMapImageSha256(check.mapImageSha256());
    entity.setPublishable(check.valid());
    entity.setCheckedAt(check.checkedAt());
    entity.setUpdatedAt(check.checkedAt());
    entity.setUpdatedBy(updatedBy);
    if (check.valid()) {
      entity.setPublishableExecutorJson(entity.getExecutorJson());
      entity.setPublishableValidationReportJson(entity.getValidationReportJson());
      entity.setPublishableMapAssetId(entity.getMapAssetId());
      entity.setPublishableMapImageSha256(entity.getMapImageSha256());
      entity.setPublishableCheckedAt(entity.getCheckedAt());
    }
    try {
      return storedDraftResponse(draftRepository.saveAndFlush(entity));
    } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
      throw ApiException.conflict("草稿已被其他用户更新，请刷新后重试");
    }
  }

  public Map<String, Object> getDraft(String routeId) {
    RouteDraftEntity draft = draftRepository.findByRouteId(routeId).orElse(null);
    if (draft != null) return storedDraftResponse(draft);

    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, routeId);
    Map<String, Object> executor = RouteExecutorSupport.executor(route);
    String mapAssetId = text(route.get("mapId"));
    if (executor != null && mapAssetId != null) {
      DraftCheck check = inspectDraft(routeId, objectMapper.valueToTree(executor), mapAssetId);
      Map<String, Object> response = draftResponse(check, false);
      response.put("draft", null);
      response.put("fallback", "ROUTE_CONFIGURATION");
      return response;
    }
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("draft", null);
    response.put("normalizedExecutorJson", null);
    response.put("valid", false);
    response.put("issues", List.of(new RouteDocumentValidator.ValidationIssue("DRAFT_NOT_INITIALIZED", "", "请先导入地图并保存路线草稿", "ERROR")));
    response.put("checkedAt", null);
    response.put("publishable", false);
    response.put("mapAssetId", mapAssetId);
    response.put("mapImageSha256", null);
    response.put("mapIdentity", null);
    response.put("fallback", "EMPTY");
    return response;
  }

  public Map<String, Object> getDraftCheck(String routeId) {
    return getDraft(routeId);
  }

  @Transactional
  public String deleteDraft(String routeId) {
    RouteDraftEntity draft = draftRepository.findByRouteId(routeId).orElse(null);
    if (draft == null) return null;
    draftRepository.delete(draft);
    return draft.getMapAssetId();
  }

  private DraftCheck inspectDraft(String routeId, JsonNode executorJson, String requestedMapAssetId) {
    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, routeId);
    ensureRouteEditable(route);
    String mapId = requestedMapAssetId == null ? text(route.get("mapId")) : requestedMapAssetId;
    if (mapId == null) throw ApiException.badRequest("路线尚未绑定地图资产");
    mapAssetService.ensureAvailableForSite(mapId, requiredText(route.get("siteId"), "路线缺少站点"));
    Map<String, Object> mapAsset = mapAssetService.get(mapId);
    JsonNode normalized = executorJson == null ? objectMapper.nullNode() : executorJson.deepCopy();
    if (normalized instanceof ObjectNode document) document.set("map", mergeMapIdentity(document.get("map"), mapIdentity(mapAsset)));
    List<RouteDocumentValidator.ValidationIssue> issues = new ArrayList<>(documentValidator.validate(normalized));
    if (normalized.path("targets").isArray() && normalized.path("targets").isEmpty()) {
      issues.add(new RouteDocumentValidator.ValidationIssue("EMPTY_TARGETS", "/targets", "路线未包含巡检点，发布后不会执行巡检", "WARNING"));
    }
    boolean valid = issues.stream().noneMatch(issue -> "ERROR".equals(issue.severity()));
    return new DraftCheck(normalized, issues, valid, requiredText(mapAsset.get("id"), "地图资产缺少 id"),
      requiredText(mapAsset.get("pgmSha256"), "地图资产缺少 PGM SHA-256"), Instant.now().toString());
  }

  private Map<String, Object> storedDraftResponse(RouteDraftEntity draft) {
    JsonNode document = documentNode(draft.getExecutorJson());
    Map<String, Object> report = document(draft.getValidationReportJson());
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("normalizedExecutorJson", document);
    response.put("valid", Boolean.TRUE.equals(report.get("valid")));
    response.put("issues", report.getOrDefault("issues", List.of()));
    response.put("checkedAt", draft.getCheckedAt());
    response.put("publishable", isDraftPublishable(draft));
    response.put("mapAssetId", draft.getMapAssetId());
    response.put("mapImageSha256", draft.getMapImageSha256());
    response.put("mapIdentity", document.path("map"));
    response.put("draft", draftMetadata(draft));
    return response;
  }

  private Map<String, Object> draftResponse(DraftCheck check, boolean publishable) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("normalizedExecutorJson", check.normalizedExecutorJson());
    response.put("valid", check.valid());
    response.put("issues", check.issues());
    response.put("checkedAt", check.checkedAt());
    response.put("publishable", publishable);
    response.put("mapAssetId", check.mapAssetId());
    response.put("mapImageSha256", check.mapImageSha256());
    response.put("mapIdentity", check.normalizedExecutorJson().path("map"));
    return response;
  }

  private ObjectNode draftValidationReport(DraftCheck check) {
    ObjectNode report = objectMapper.createObjectNode();
    report.put("valid", check.valid());
    report.put("checkedAt", check.checkedAt());
    report.put("mapAssetId", check.mapAssetId());
    report.put("mapImageSha256", check.mapImageSha256());
    report.set("issues", objectMapper.valueToTree(check.issues()));
    return report;
  }

  private Map<String, Object> draftMetadata(RouteDraftEntity draft) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("version", draft.getVersion());
    metadata.put("updatedAt", draft.getUpdatedAt());
    metadata.put("updatedBy", draft.getUpdatedBy());
    metadata.put("publishable", isDraftPublishable(draft));
    if (draft.getPublishableCheckedAt() != null) {
      metadata.put("lastPublishable", Map.of(
        "checkedAt", draft.getPublishableCheckedAt(),
        "mapAssetId", draft.getPublishableMapAssetId(),
        "mapImageSha256", draft.getPublishableMapImageSha256()
      ));
    }
    return metadata;
  }

  private boolean isDraftPublishable(RouteDraftEntity draft) {
    if (!draft.isPublishable()) return false;
    try {
      Map<String, Object> mapAsset = mapAssetService.get(draft.getMapAssetId());
      JsonNode map = documentNode(draft.getExecutorJson()).path("map");
      String currentSha = requiredText(mapAsset.get("pgmSha256"), "地图资产缺少 PGM SHA-256");
      return currentSha.equals(draft.getMapImageSha256()) && currentSha.equals(map.path("image_sha256").asText());
    } catch (ApiException ex) {
      return false;
    }
  }

  private JsonNode documentNode(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception ex) {
      throw new IllegalStateException("路线草稿数据损坏", ex);
    }
  }

  private Map<String, Object> document(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      throw new IllegalStateException("路线草稿数据损坏", ex);
    }
  }

  private record DraftCheck(JsonNode normalizedExecutorJson, List<RouteDocumentValidator.ValidationIssue> issues,
      boolean valid, String mapAssetId, String mapImageSha256, String checkedAt) { }

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
    JsonNode rawRoutes = document.get("routes");
    if (!(rawRoutes instanceof ArrayNode routes) || routes.size() != 1) {
      throw ApiException.badRequest("平台发布仅支持一条路线");
    }
    ObjectNode route = asObject(routes.get(0), "executorJson.routes[0] must be an object");
    String routeId = route.path("id").asText();
    if (routeId.isBlank()) throw ApiException.badRequest("executorJson.routes[0].id is required");
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
    ArrayNode targetIds = route.putArray("target_ids");
    for (JsonNode rawTarget : targets) {
      ObjectNode target = asObject(rawTarget, "executorJson target must be an object");
      targetIds.add(target.path("id").asText());
      ObjectNode pose = asObject(target.get("pose"), "executorJson target.pose must be an object");
      target.set("location", mergeObject(target.get("location"), mapPose(pose), "executorJson target.location must be an object"));
      target.remove("safety");
    }

    JsonNode schedules = document.get("schedules");
    if (!(schedules instanceof ArrayNode) || !schedules.isEmpty()) {
      throw ApiException.badRequest("平台路线不允许配置 schedules");
    }
    document.put("active_route_id", routeId);
    document.remove("safety");
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

  private ObjectNode mergeMapIdentity(JsonNode raw, ObjectNode identity) {
    ObjectNode merged = raw instanceof ObjectNode object ? object.deepCopy() : objectMapper.createObjectNode();
    identity.fields().forEachRemaining(field -> merged.set(field.getKey(), field.getValue()));
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

  private ObjectNode validationReport(ObjectNode document, Map<String, Object> mapAsset, String now, List<RouteDocumentValidator.ValidationIssue> issues) {
    ObjectNode report = objectMapper.createObjectNode();
    report.put("valid", true);
    report.put("validatedAt", now);
    report.put("routeVersion", document.path("version").asInt());
    report.put("mapAssetId", requiredText(mapAsset.get("id"), "地图资产缺少 id"));
    report.put("mapImageSha256", requiredText(mapAsset.get("pgmSha256"), "地图资产缺少 PGM SHA-256"));
    report.set("issues", objectMapper.valueToTree(issues));
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

  private void ensureRouteEditable(Map<String, Object> route) {
    if ("ARCHIVED".equals(text(route.get("status")))) {
      throw ApiException.conflict("路线已归档，不能继续编辑");
    }
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
