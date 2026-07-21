package com.powerinspection.robot;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/robots")
public class RobotLocationController {
  private final RobotLocationService locationService;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public RobotLocationController(
      RobotLocationService locationService,
      PermissionService permissionService,
      CurrentUser currentUser) {
    this.locationService = locationService;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping("/locations")
  public ApiResponse<List<RobotLocationView>> listLocations(
      @RequestParam(required = false) String siteId,
      @RequestParam(required = false) Boolean online) {
    permissionService.require(currentUser.get(), Permission.ROBOT_LOCATION_VIEW);
    return ApiResponse.ok(locationService.listLocations(siteId, online));
  }

  @GetMapping("/{robotId}/location")
  public ApiResponse<RobotLocationView> location(@PathVariable String robotId) {
    permissionService.require(currentUser.get(), Permission.ROBOT_LOCATION_VIEW);
    return ApiResponse.ok(locationService.getLocation(robotId));
  }

  @GetMapping("/{robotId}/track")
  public ApiResponse<RobotTrackView> track(
      @PathVariable String robotId,
      @RequestParam(required = false) Instant start,
      @RequestParam(required = false) Instant end,
      @RequestParam(required = false) String executionId,
      @RequestParam(required = false) Integer limit) {
    permissionService.require(currentUser.get(), Permission.ROBOT_TRACK_VIEW);
    return ApiResponse.ok(locationService.getTrack(robotId, start, end, executionId, limit));
  }
}
