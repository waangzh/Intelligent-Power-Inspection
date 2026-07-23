package com.powerinspection.route;

import com.powerinspection.business.CrudSupport;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.Ids;
import com.powerinspection.common.ListQuery;
import com.powerinspection.common.PageResult;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.mapasset.MapAssetService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.task.TaskExecutionRepository;
import com.powerinspection.task.TaskExecutionStatus;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController extends CrudSupport {
  private final PermissionService permissionService;
  private final CurrentUser currentUser;
  private final MapAssetService mapAssetService;
  private final RouteRevisionService routeRevisionService;
  private final RouteRevisionRepository routeRevisionRepository;
  private final TaskExecutionRepository taskExecutionRepository;

  public RouteController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser,
      MapAssetService mapAssetService, RouteRevisionService routeRevisionService,
      RouteRevisionRepository routeRevisionRepository, TaskExecutionRepository taskExecutionRepository) {
    super(dataStore);
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.mapAssetService = mapAssetService;
    this.routeRevisionService = routeRevisionService;
    this.routeRevisionRepository = routeRevisionRepository;
    this.taskExecutionRepository = taskExecutionRepository;
  }

  @GetMapping
  public ApiResponse<PageResult<Map<String, Object>>> routes(ListQuery query) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    Map<String, String> filters = query.filters("siteId", "status");
    if (query.getStatus() == null || query.getStatus().isBlank()) filters.put("status", "ACTIVE");
    PageResult<Map<String, Object>> result = dataStore.page(
      DataCategory.ROUTE, query.getPage(), query.getSize(), query.getSort(), query.getDirection(),
      query.getUpdatedAfter(), query.getQ(), filters);
    return ApiResponse.ok(new PageResult<>(
      result.items().stream().map(RouteExecutorSupport::attachRosAlias).toList(),
      result.total(), result.page(), result.size(), result.hasMore(), result.nextCursor()
    ));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> route(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(RouteExecutorSupport.attachRosAlias(dataStore.get(DataCategory.ROUTE, id)));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> createRoute(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    String siteId = String.valueOf(body.get("siteId"));
    ensureSiteExists(siteId);
    validateMapAsset(body.get("mapId"), siteId);
    body.putIfAbsent("id", Ids.next("route"));
    body.putIfAbsent("path", List.of());
    body.putIfAbsent("checkpoints", List.of());
    body.putIfAbsent("mapMode", "2d");
    body.put("status", "ACTIVE");
    RouteExecutorSupport.normalizeRoute(body);
    return ApiResponse.ok(RouteExecutorSupport.attachRosAlias(create(DataCategory.ROUTE, "route", body)));
  }

  @GetMapping("/{id}/checkpoints")
  public ApiResponse<List<Map<String, Object>>> routeCheckpoints(@PathVariable String id) {
    return ApiResponse.ok(RouteExecutorSupport.compatibleCheckpoints(dataStore.get(DataCategory.ROUTE, id)));
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> updateRoute(@PathVariable String id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(updateRoutePayload(id, body));
  }

  @PutMapping("/{id}")
  public ApiResponse<Map<String, Object>> replaceRoute(@PathVariable String id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(updateRoutePayload(id, body));
  }

  private Map<String, Object> updateRoutePayload(String id, Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    ensureNoActiveTaskForRoute(id);
    Map<String, Object> current = dataStore.get(DataCategory.ROUTE, id);
    ensureRouteEditable(current);
    body.remove("status");
    String siteId = body.containsKey("siteId") ? String.valueOf(body.get("siteId")) : String.valueOf(current.get("siteId"));
    ensureSiteExists(siteId);
    Object mapId = body.containsKey("mapId") ? body.get("mapId") : current.get("mapId");
    validateMapAsset(mapId, siteId);
    String previousMapId = text(current.get("mapId"));
    body.put("id", id);
    RouteExecutorSupport.normalizeRoute(body);
    current.putAll(body);
    Map<String, Object> updated = RouteExecutorSupport.attachRosAlias(dataStore.upsert(DataCategory.ROUTE, current));
    String updatedMapId = text(updated.get("mapId"));
    if (previousMapId != null && !previousMapId.equals(updatedMapId)) {
      mapAssetService.deleteIfUnreferenced(previousMapId);
    }
    return updated;
  }

  @DeleteMapping("/{id}")
  @Transactional
  public ApiResponse<RouteDeletionResult> deleteRoute(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    ensureNoActiveTaskForRoute(id);
    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, id);
    String mapId = text(route.get("mapId"));
    List<Map<String, Object>> tasks = dataStore.list(DataCategory.TASK).stream()
      .filter(task -> id.equals(text(task.get("routeId"))))
      .toList();
    List<RouteRevisionEntity> revisions = routeRevisionRepository.findByRouteIdOrderByRevisionNoDesc(id);
    if (!tasks.isEmpty() || !revisions.isEmpty()) {
      route.put("status", "ARCHIVED");
      dataStore.upsert(DataCategory.ROUTE, route);
      return ApiResponse.ok(new RouteDeletionResult(id, true));
    }
    String draftMapId = routeRevisionService.deleteDraft(id);
    delete(DataCategory.ROUTE, id);
    mapAssetService.deleteIfUnreferenced(mapId);
    if (draftMapId != null && !draftMapId.equals(mapId)) mapAssetService.deleteIfUnreferenced(draftMapId);
    return ApiResponse.ok(new RouteDeletionResult(id, false));
  }

  @PostMapping("/{id}/checkpoints")
  public ApiResponse<Map<String, Object>> addCheckpoint(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    ensureNoActiveTaskForRoute(id);
    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, id);
    ensureRouteEditable(route);
    List<Map<String, Object>> checkpoints = checkpoints(route);
    body.putIfAbsent("id", Ids.next("cp"));
    body.put("routeId", id);
    body.put("seq", checkpoints.size() + 1);
    checkpoints.add(body);
    route.put("checkpoints", checkpoints);
    dataStore.upsert(DataCategory.ROUTE, route);
    return ApiResponse.ok(body);
  }

  @PatchMapping("/{routeId}/checkpoints/{checkpointId}")
  public ApiResponse<Map<String, Object>> updateCheckpoint(@PathVariable String routeId, @PathVariable String checkpointId, @RequestBody Map<String, Object> patch) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    ensureNoActiveTaskForRoute(routeId);
    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, routeId);
    ensureRouteEditable(route);
    List<Map<String, Object>> checkpoints = checkpoints(route);
    Map<String, Object> checkpoint = checkpoints.stream()
      .filter(item -> checkpointId.equals(String.valueOf(item.get("id"))))
      .findFirst()
      .orElseThrow(() -> ApiException.notFound("检查点不存在"));
    checkpoint.putAll(patch);
    checkpoint.put("id", checkpointId);
    route.put("checkpoints", checkpoints);
    dataStore.upsert(DataCategory.ROUTE, route);
    return ApiResponse.ok(checkpoint);
  }

  @DeleteMapping("/{routeId}/checkpoints/{checkpointId}")
  public ApiResponse<Void> deleteCheckpoint(@PathVariable String routeId, @PathVariable String checkpointId) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    ensureNoActiveTaskForRoute(routeId);
    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, routeId);
    ensureRouteEditable(route);
    List<Map<String, Object>> checkpoints = checkpoints(route).stream()
      .filter(item -> !checkpointId.equals(String.valueOf(item.get("id"))))
      .sorted(Comparator.comparing(item -> Integer.parseInt(String.valueOf(item.getOrDefault("seq", "0")))))
      .toList();
    for (int i = 0; i < checkpoints.size(); i++) {
      checkpoints.get(i).put("seq", i + 1);
    }
    route.put("checkpoints", checkpoints);
    route.put("updatedAt", Instant.now().toString());
    dataStore.upsert(DataCategory.ROUTE, route);
    return ApiResponse.ok();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> checkpoints(Map<String, Object> route) {
    Object raw = route.get("checkpoints");
    if (raw instanceof List<?> list) {
      return new ArrayList<>((List<Map<String, Object>>) list);
    }
    return new ArrayList<>();
  }

  private void ensureSiteExists(String siteId) {
    if (siteId == null || siteId.isBlank() || "null".equals(siteId)) {
      throw ApiException.badRequest("请选择站点");
    }
    if (dataStore.find(DataCategory.SITE, siteId) == null) {
      throw ApiException.badRequest("站点不存在");
    }
  }

  private void validateMapAsset(Object rawMapId, String siteId) {
    String mapId = text(rawMapId);
    if (mapId != null) mapAssetService.ensureAvailableForSite(mapId, siteId);
  }

  private String text(Object value) {
    if (value == null || value.toString().isBlank() || "null".equals(value.toString())) return null;
    return value.toString();
  }

  private void ensureNoActiveTaskForRoute(String routeId) {
    boolean active = dataStore.list(DataCategory.TASK).stream()
      .anyMatch(task -> routeId.equals(text(task.get("routeId"))) && isActiveStatus(text(task.get("status"))));
    if (active) throw ApiException.conflict("路线正在执行，不能修改或归档");

    Set<String> revisionIds = new HashSet<>(routeRevisionRepository.findByRouteIdOrderByRevisionNoDesc(routeId).stream()
      .map(RouteRevisionEntity::getId).toList());
    if (!revisionIds.isEmpty() && taskExecutionRepository.findByRouteRevisionIdIn(revisionIds).stream()
        .anyMatch(execution -> TaskExecutionStatus.ACTIVE.contains(execution.getStatus()))) {
      throw ApiException.conflict("路线正在执行或等待机器人确认，不能修改或归档");
    }
  }

  private boolean isActiveStatus(String status) {
    return TaskExecutionStatus.ACTIVE.contains(status) || "DISPATCHED".equals(status);
  }

  private void ensureRouteEditable(Map<String, Object> route) {
    if ("ARCHIVED".equals(text(route.get("status")))) {
      throw ApiException.conflict("路线已归档，不能继续编辑");
    }
  }
}
