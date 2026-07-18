package com.powerinspection.detection;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.PageResult;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/detections")
public class DetectionRunController {
  private final DetectionRunService service;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public DetectionRunController(DetectionRunService service, PermissionService permissionService, CurrentUser currentUser) {
    this.service = service;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @PostMapping("/robot-image")
  public ApiResponse<Map<String, Object>> detectRobotImage(@RequestBody RobotImageDetectionRequest request, HttpServletRequest servletRequest) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    DetectionRunEntity run = service.submitRobotImage(request.imageId(), request.detections(), currentUser.get().getId(), publicBase(servletRequest));
    return ApiResponse.ok(service.view(run, publicBase(servletRequest)));
  }

  @GetMapping("/runs/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable String id, HttpServletRequest request) {
    permissionService.requireAny(currentUser.get(), Permission.DETECTION_MANAGE, Permission.TASK_VIEW);
    return ApiResponse.ok(service.view(service.get(id), publicBase(request)));
  }

  @GetMapping("/runs")
  public ApiResponse<PageResult<Map<String, Object>>> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String taskId, @RequestParam(required = false) String imageId,
      @RequestParam(required = false) String status, HttpServletRequest request) {
    permissionService.requireAny(currentUser.get(), Permission.DETECTION_MANAGE, Permission.TASK_VIEW);
    Page<DetectionRunEntity> result = service.search(page, size, taskId, imageId, status);
    String publicBase = publicBase(request);
    return ApiResponse.ok(new PageResult<>(result.getContent().stream().map(run -> service.view(run, publicBase)).toList(),
      result.getTotalElements(), result.getNumber(), result.getSize(), result.hasNext(), null));
  }

  private String publicBase(HttpServletRequest request) {
    return ServletUriComponentsBuilder.fromRequestUri(request)
      .replacePath("/model-files/").replaceQuery(null).build().toUriString();
  }

  public record RobotImageDetectionRequest(String imageId, List<Map<String, Object>> detections) {}
}
