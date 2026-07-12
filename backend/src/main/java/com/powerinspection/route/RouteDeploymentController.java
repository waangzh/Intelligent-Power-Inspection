package com.powerinspection.route;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RouteDeploymentController {
  private final RouteDeploymentService routeDeploymentService;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public RouteDeploymentController(RouteDeploymentService routeDeploymentService, PermissionService permissionService, CurrentUser currentUser) {
    this.routeDeploymentService = routeDeploymentService;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @PostMapping("/route-revisions/{revisionId}/deployments")
  public ApiResponse<Map<String, Object>> create(
      @PathVariable String revisionId,
      @RequestHeader(value = "Idempotency-Key", required = false) String requestId,
      @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    String robotId = text(body.get("robotId"));
    if (robotId == null) throw ApiException.badRequest("请选择机器人");
    return ApiResponse.ok(routeDeploymentService.request(revisionId, robotId, requestId));
  }

  @GetMapping("/route-deployments/{deploymentId}")
  public ApiResponse<Map<String, Object>> get(@PathVariable String deploymentId) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(routeDeploymentService.get(deploymentId));
  }

  private String text(Object value) {
    if (value == null || value.toString().isBlank() || "null".equals(value.toString())) return null;
    return value.toString();
  }
}
