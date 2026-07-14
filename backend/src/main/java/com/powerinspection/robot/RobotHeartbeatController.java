package com.powerinspection.robot;

import com.powerinspection.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/robots")
public class RobotHeartbeatController {
  private final RobotHeartbeatService heartbeatService;
  public RobotHeartbeatController(RobotHeartbeatService heartbeatService) { this.heartbeatService = heartbeatService; }

  @GetMapping("/status")
  public ApiResponse<RobotHeartbeatStatusPage> list(
    @RequestParam(required = false) Boolean online,
    @RequestParam(required = false) String connectionStatus,
    @RequestParam(defaultValue = "lastHeartbeatAt") String sort,
    @RequestParam(defaultValue = "desc") String direction,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
  ) {
    return ApiResponse.ok(heartbeatService.list(online, connectionStatus, sort, direction, page, size));
  }

  @GetMapping("/{robotId}/status")
  public ApiResponse<RobotHeartbeatStatusView> detail(@PathVariable String robotId) {
    return ApiResponse.ok(heartbeatService.detail(robotId));
  }
}
