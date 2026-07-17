package com.powerinspection.route;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.robot.RobotBridgeIdMapper;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.util.Map;
import java.util.Set;
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
  private final RobotBridgeIdMapper robotBridgeIdMapper;

  public RouteDeploymentController(RouteDeploymentService routeDeploymentService, PermissionService permissionService, CurrentUser currentUser,
      RobotBridgeIdMapper robotBridgeIdMapper) {
    this.routeDeploymentService = routeDeploymentService;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.robotBridgeIdMapper = robotBridgeIdMapper;
  }

  @PostMapping("/route-revisions/{revisionId}/deployments")
  public ApiResponse<Map<String, Object>> create(
      @PathVariable String revisionId,
      @RequestHeader(value = "Idempotency-Key", required = false) String requestId,
      @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    if (!body.keySet().equals(Set.of("robotId"))) {
      throw ApiException.badRequest("部署请求只允许包含 robotId；路线、地图哈希和 Bridge 凭据由服务端生成");
    }
    String robotId = text(body.get("robotId"));
    if (robotId == null) throw ApiException.badRequest("请选择机器人");
    return ApiResponse.ok(routeDeploymentService.request(revisionId, robotId, requestId));
  }

  @GetMapping("/route-revisions/{revisionId}/deployments")
  public ApiResponse<java.util.List<Map<String, Object>>> list(@PathVariable String revisionId) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(routeDeploymentService.listByRevision(revisionId));
  }

  @PostMapping("/route-deployments/{deploymentId}/reconcile")
  public ApiResponse<Map<String, Object>> reconcile(@PathVariable String deploymentId) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    return ApiResponse.ok(routeDeploymentService.reconcile(deploymentId));
  }

  @GetMapping("/route-deployments/{deploymentId}")
  public ApiResponse<Map<String, Object>> get(@PathVariable String deploymentId,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    boolean bridgeRequest = robotBridgeIdMapper.isBridgePlatformRequest(authorization);
    if (!bridgeRequest) permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    Map<String, Object> deployment = routeDeploymentService.get(deploymentId);
    return ApiResponse.ok(bridgeRequest
      ? robotBridgeIdMapper.toBridgeDeploymentView(deployment) : deployment);
  }

  private String text(Object value) {
    if (value == null || value.toString().isBlank() || "null".equals(value.toString())) return null;
    return value.toString();
  }
}
