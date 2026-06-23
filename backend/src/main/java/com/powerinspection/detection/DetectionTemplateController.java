package com.powerinspection.detection;

import com.powerinspection.business.CrudSupport;
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
@RequestMapping("/api/v1/detection-templates")
public class DetectionTemplateController extends CrudSupport {
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public DetectionTemplateController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser) {
    super(dataStore);
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> templates() {
    return ApiResponse.ok(list(DataCategory.DETECTION_TEMPLATE));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> template(@PathVariable String id) {
    return ApiResponse.ok(dataStore.get(DataCategory.DETECTION_TEMPLATE, id));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> addTemplate(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    return ApiResponse.ok(create(DataCategory.DETECTION_TEMPLATE, "tpl", body));
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> updateTemplate(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    return ApiResponse.ok(update(DataCategory.DETECTION_TEMPLATE, id, body));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> removeTemplate(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    delete(DataCategory.DETECTION_TEMPLATE, id);
    return ApiResponse.ok();
  }
}
