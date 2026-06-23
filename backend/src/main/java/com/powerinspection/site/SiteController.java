package com.powerinspection.site;

import com.powerinspection.business.CrudSupport;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
  public ApiResponse<List<Map<String, Object>>> sites() {
    return ApiResponse.ok(list(DataCategory.SITE));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> site(@PathVariable String id) {
    return ApiResponse.ok(dataStore.get(DataCategory.SITE, id));
  }

  @GetMapping("/areas")
  public ApiResponse<List<Map<String, Object>>> areas() {
    return ApiResponse.ok(list(DataCategory.AREA));
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
    return ApiResponse.ok();
  }

  @GetMapping("/{id}/areas")
  public ApiResponse<List<Map<String, Object>>> areasBySite(@PathVariable String id) {
    return ApiResponse.ok(list(DataCategory.AREA).stream().filter(area -> id.equals(String.valueOf(area.get("siteId")))).toList());
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
