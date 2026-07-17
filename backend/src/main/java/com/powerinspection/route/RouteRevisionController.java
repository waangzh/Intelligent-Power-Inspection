package com.powerinspection.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.robot.RobotBridgeIdMapper;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RouteRevisionController {
  private final RouteRevisionService routeRevisionService;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;
  private final RobotBridgeIdMapper robotBridgeIdMapper;

  public RouteRevisionController(RouteRevisionService routeRevisionService, PermissionService permissionService, CurrentUser currentUser,
      RobotBridgeIdMapper robotBridgeIdMapper) {
    this.routeRevisionService = routeRevisionService;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.robotBridgeIdMapper = robotBridgeIdMapper;
  }

  @GetMapping("/routes/{routeId}/revisions")
  public ApiResponse<List<Map<String, Object>>> list(@PathVariable String routeId) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    return ApiResponse.ok(routeRevisionService.list(routeId));
  }

  @PostMapping("/routes/{routeId}/revisions")
  public ApiResponse<Map<String, Object>> create(@PathVariable String routeId) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    return ApiResponse.ok(routeRevisionService.create(routeId, currentUser.get().getId()));
  }

  @PostMapping("/routes/{routeId}/draft:validate")
  public ApiResponse<Map<String, Object>> validateDraft(@PathVariable String routeId, @RequestBody JsonNode body) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    JsonNode executorJson = body == null ? null : body.get("executorJson");
    if (executorJson == null) {
      throw com.powerinspection.common.ApiException.badRequest("executorJson is required");
    }
    String mapAssetId = body.path("mapAssetId").isTextual() ? body.path("mapAssetId").asText() : null;
    return ApiResponse.ok(routeRevisionService.validateDraft(routeId, executorJson, mapAssetId));
  }

  @GetMapping("/routes/{routeId}/draft")
  public ApiResponse<Map<String, Object>> getDraft(@PathVariable String routeId) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    return ApiResponse.ok(routeRevisionService.getDraft(routeId));
  }

  @GetMapping("/routes/{routeId}/draft:check")
  public ApiResponse<Map<String, Object>> getDraftCheck(@PathVariable String routeId) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    return ApiResponse.ok(routeRevisionService.getDraftCheck(routeId));
  }

  @PutMapping("/routes/{routeId}/draft")
  public ApiResponse<Map<String, Object>> saveDraft(@PathVariable String routeId, @RequestBody JsonNode body) {
    permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    JsonNode executorJson = body == null ? null : body.get("executorJson");
    if (executorJson == null) throw com.powerinspection.common.ApiException.badRequest("executorJson is required");
    String mapAssetId = body.path("mapAssetId").isTextual() ? body.path("mapAssetId").asText() : null;
    JsonNode expectedVersionNode = body.path("expectedVersion");
    if (!expectedVersionNode.isMissingNode() && !expectedVersionNode.isNull()
        && (!expectedVersionNode.isIntegralNumber() || expectedVersionNode.asLong() < 0)) {
      throw com.powerinspection.common.ApiException.badRequest("expectedVersion must be a non-negative integer");
    }
    Long expectedVersion = expectedVersionNode.isIntegralNumber() ? expectedVersionNode.asLong() : null;
    return ApiResponse.ok(routeRevisionService.saveDraft(routeId, executorJson, mapAssetId, expectedVersion, currentUser.get().getId()));
  }

  @GetMapping("/route-revisions/{revisionId}")
  public ApiResponse<Map<String, Object>> get(@PathVariable String revisionId,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    if (!robotBridgeIdMapper.isBridgePlatformRequest(authorization)) {
      permissionService.require(currentUser.get(), Permission.ROUTE_EDIT);
    }
    return ApiResponse.ok(routeRevisionService.get(revisionId));
  }
}
