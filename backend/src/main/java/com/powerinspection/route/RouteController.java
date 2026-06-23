package com.powerinspection.route;

import com.powerinspection.business.CrudSupport;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController extends CrudSupport {
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public RouteController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser) {
    super(dataStore);
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> routes(@RequestParam(required = false) String siteId) {
    List<Map<String, Object>> routes = list(DataCategory.ROUTE);
    if (siteId != null && !siteId.isBlank()) {
      routes = routes.stream().filter(route -> siteId.equals(String.valueOf(route.get("siteId")))).toList();
    }
    return ApiResponse.ok(routes);
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> route(@PathVariable String id) {
    return ApiResponse.ok(dataStore.get(DataCategory.ROUTE, id));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> createRoute(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    ensureSiteExists(String.valueOf(body.get("siteId")));
    body.putIfAbsent("path", List.of());
    body.putIfAbsent("checkpoints", List.of());
    body.putIfAbsent("mapMode", "2d");
    return ApiResponse.ok(create(DataCategory.ROUTE, "route", body));
  }

  @GetMapping("/{id}/checkpoints")
  public ApiResponse<List<Map<String, Object>>> routeCheckpoints(@PathVariable String id) {
    return ApiResponse.ok(checkpoints(dataStore.get(DataCategory.ROUTE, id)));
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> updateRoute(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    ensureNoActiveTaskForRoute(id);
    if (body.containsKey("siteId")) {
      ensureSiteExists(String.valueOf(body.get("siteId")));
    }
    return ApiResponse.ok(update(DataCategory.ROUTE, id, body));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> deleteRoute(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    ensureNoActiveTaskForRoute(id);
    delete(DataCategory.ROUTE, id);
    return ApiResponse.ok();
  }

  @PostMapping("/{id}/checkpoints")
  public ApiResponse<Map<String, Object>> addCheckpoint(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    ensureNoActiveTaskForRoute(id);
    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, id);
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

  private void ensureNoActiveTaskForRoute(String routeId) {
    boolean active = dataStore.list(DataCategory.TASK).stream()
      .anyMatch(task -> routeId.equals(String.valueOf(task.get("routeId"))) && isActiveStatus(String.valueOf(task.get("status"))));
    if (active) {
      throw ApiException.badRequest("路线正在被任务使用，不能修改");
    }
  }

  private boolean isActiveStatus(String status) {
    return List.of("DISPATCHED", "RUNNING", "PAUSED", "MANUAL_TAKEOVER").contains(status);
  }
}
