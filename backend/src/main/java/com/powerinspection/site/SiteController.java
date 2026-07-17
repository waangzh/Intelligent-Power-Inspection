package com.powerinspection.site;

import com.powerinspection.business.CrudSupport;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.ListQuery;
import com.powerinspection.common.PageResult;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sites")
public class SiteController extends CrudSupport {
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public SiteController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser) {
    super(dataStore);
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ApiResponse<PageResult<Map<String, Object>>> sites(ListQuery query) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(page(DataCategory.SITE, query, "status"));
  }

  @GetMapping("/slam-maps")
  public ApiResponse<List<Map<String, Object>>> slamMaps() {
    List<Map<String, Object>> maps = dataStore.list(DataCategory.SLAM_MAP).stream()
      .map(this::slamMapSummary)
      .toList();
    return ApiResponse.ok(maps);
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> site(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(dataStore.get(DataCategory.SITE, id));
  }

  @GetMapping("/{id}/slam-map")
  public ApiResponse<Map<String, Object>> slamMap(@PathVariable String id) {
    ensureSiteExists(id);
    Map<String, Object> item = dataStore.find(DataCategory.SLAM_MAP, id);
    if (item == null) {
      throw ApiException.notFound("SLAM map does not exist");
    }
    return ApiResponse.ok(item);
  }

  @GetMapping("/areas")
  public ApiResponse<PageResult<Map<String, Object>>> areas(ListQuery query) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(page(DataCategory.AREA, query, "siteId"));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> createSite(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.SITE_EDIT);
    return ApiResponse.ok(create(DataCategory.SITE, "site", body));
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> updateSite(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.SITE_EDIT);
    return ApiResponse.ok(update(DataCategory.SITE, id, body));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> deleteSite(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.SITE_EDIT);
    ensureSiteNotReferenced(id);
    delete(DataCategory.SITE, id);
    dataStore.deleteWhere(DataCategory.AREA, "siteId", id);
    dataStore.delete(DataCategory.SLAM_MAP, id);
    return ApiResponse.ok();
  }

  @PutMapping("/{id}/slam-map")
  public ApiResponse<Map<String, Object>> saveSlamMap(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.SITE_EDIT);
    ensureSiteExists(id);
    String yamlText = text(body.get("yamlText"));
    String pngBase64 = text(body.get("pngBase64"));
    if (yamlText == null || yamlText.isBlank() || pngBase64 == null || pngBase64.isBlank()) {
      throw ApiException.badRequest("yamlText and pngBase64 are required");
    }
    String now = Instant.now().toString();
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("id", id);
    item.put("siteId", id);
    item.put("yamlText", yamlText);
    item.put("pngBase64", pngBase64);
    item.put("source", text(body.getOrDefault("source", "cloud")));
    item.put("updatedAt", now);
    Map<String, Object> existing = dataStore.find(DataCategory.SLAM_MAP, id);
    if (existing != null && existing.get("createdAt") != null) {
      item.put("createdAt", existing.get("createdAt"));
    } else {
      item.put("createdAt", now);
    }
    return ApiResponse.ok(dataStore.upsert(DataCategory.SLAM_MAP, item));
  }

  @DeleteMapping("/{id}/slam-map")
  public ApiResponse<Void> deleteSlamMap(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.SITE_EDIT);
    ensureSiteExists(id);
    dataStore.delete(DataCategory.SLAM_MAP, id);
    return ApiResponse.ok();
  }
  @GetMapping("/{id}/areas")
  public ApiResponse<PageResult<Map<String, Object>>> areasBySite(@PathVariable String id, ListQuery query) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    ensureSiteExists(id);
    query.setSiteId(id);
    return ApiResponse.ok(page(DataCategory.AREA, query, "siteId"));
  }

  @PostMapping("/{id}/areas")
  public ApiResponse<Map<String, Object>> createArea(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.SITE_EDIT);
    ensureSiteExists(id);
    body.put("siteId", id);
    return ApiResponse.ok(create(DataCategory.AREA, "area", body));
  }

  @PatchMapping("/{siteId}/areas/{areaId}")
  public ApiResponse<Map<String, Object>> updateArea(@PathVariable String siteId, @PathVariable String areaId, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.SITE_EDIT);
    ensureSiteExists(siteId);
    body.put("siteId", siteId);
    return ApiResponse.ok(update(DataCategory.AREA, areaId, body));
  }

  @DeleteMapping("/{siteId}/areas/{areaId}")
  public ApiResponse<Void> deleteAreaInSite(@PathVariable String siteId, @PathVariable String areaId) {
    permissionService.require(currentUser.get(), Permission.SITE_EDIT);
    delete(DataCategory.AREA, areaId);
    return ApiResponse.ok();
  }

  @DeleteMapping("/areas/{areaId}")
  public ApiResponse<Void> deleteArea(@PathVariable String areaId) {
    permissionService.require(currentUser.get(), Permission.SITE_EDIT);
    delete(DataCategory.AREA, areaId);
    return ApiResponse.ok();
  }

  private Map<String, Object> slamMapSummary(Map<String, Object> item) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("siteId", item.get("siteId"));
    summary.put("source", item.getOrDefault("source", "cloud"));
    summary.put("updatedAt", item.get("updatedAt"));
    return summary;
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }
  private void ensureSiteExists(String siteId) {
    if (dataStore.find(DataCategory.SITE, siteId) == null) {
      throw ApiException.badRequest("站点不存在");
    }
  }

  private void ensureSiteNotReferenced(String siteId) {
    boolean hasRoutes = dataStore.list(DataCategory.ROUTE).stream()
      .anyMatch(route -> siteId.equals(String.valueOf(route.get("siteId"))));
    if (hasRoutes) {
      throw ApiException.badRequest("站点下存在巡检路线，不能删除");
    }
    boolean hasRobots = dataStore.list(DataCategory.ROBOT).stream()
      .anyMatch(robot -> siteId.equals(String.valueOf(robot.get("siteId"))));
    if (hasRobots) {
      throw ApiException.badRequest("站点下存在机器人，不能删除");
    }
  }
}
