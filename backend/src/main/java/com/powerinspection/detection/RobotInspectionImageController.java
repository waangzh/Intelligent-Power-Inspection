package com.powerinspection.detection;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.PageResult;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/robot-inspection-images")
public class RobotInspectionImageController {
  private final RobotInspectionImageService service;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public RobotInspectionImageController(RobotInspectionImageService service, PermissionService permissionService, CurrentUser currentUser) {
    this.service = service;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<Map<String, Object>>> importImage(
      @RequestPart("image") MultipartFile image,
      @RequestParam String robotId,
      @RequestParam String taskId,
      @RequestParam String checkpointId,
      @RequestParam(required = false) String capturedAt,
      HttpServletRequest request) throws IOException {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    RobotInspectionImageEntity saved = service.importForAdministrator(
      taskId, robotId, checkpointId, capturedAt, currentUser.get().getId(), image);
    return ResponseEntity.created(java.net.URI.create("/api/v1/robot-inspection-images/" + saved.getId()))
      .body(ApiResponse.ok(service.view(saved, publicBase(request))));
  }

  @GetMapping
  public ApiResponse<PageResult<Map<String, Object>>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String taskId,
      @RequestParam(required = false) String checkpointId,
      @RequestParam(required = false) String robotId,
      @RequestParam(required = false) String capturedFrom,
      @RequestParam(required = false) String capturedTo,
      HttpServletRequest request) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    Page<RobotInspectionImageEntity> result = service.search(page, size, taskId, checkpointId, robotId, capturedFrom, capturedTo);
    var items = result.getContent().stream().map(item -> service.view(item, publicBase(request))).toList();
    return ApiResponse.ok(new PageResult<>(items, result.getTotalElements(), result.getNumber(), result.getSize(), result.hasNext(), null));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable String id, HttpServletRequest request) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    return ApiResponse.ok(service.view(service.get(id), publicBase(request)));
  }

  private String publicBase(HttpServletRequest request) {
    return ServletUriComponentsBuilder.fromRequestUri(request)
      .replacePath("/model-files/").replaceQuery(null).build().toUriString();
  }
}
